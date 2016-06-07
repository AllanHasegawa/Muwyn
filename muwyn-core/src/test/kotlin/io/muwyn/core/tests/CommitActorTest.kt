/*
 * Copyright 2016 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.muwyn.core.tests

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.TestProbe
import io.muwyn.core.actors.ActorMsgs
import io.muwyn.core.actors.CommitActor
import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import io.muwyn.core.data.increment
import io.muwyn.core.storage.CommitsRepository
import io.muwyn.core.storage.Snapshot
import io.muwyn.core.storage.Snapshot.ValidationResult
import io.muwyn.core.storage.SnapshotMgr
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.MockitoAnnotations

class CommitActorTest {
    val system = ActorSystem.create()

    val resourceId = "Resource1"

    @Mock var commitsRepository: CommitsRepository? = null
    @Mock var snapshotMgr: SnapshotMgr? = null
    @Mock var snapshot: Snapshot? = null

    private val factory = object : CommitActor.Factory {
        override fun create(context: ActorContext?, resourceId: String): ActorRef {
            return system.actorOf(CommitActor.props(resourceId, commitsRepository!!, snapshotMgr!!))
        }
    }

    init {
        MockitoAnnotations.initMocks(this)
    }

    @Before
    fun beforeTests() {
        `when`(snapshotMgr?.getLatestSnapshot(resourceId, commitsRepository!!)).thenReturn(snapshot!!)
        `when`(snapshot?.getVersion()).thenReturn(Version())
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version())
    }

    @After
    fun afterTests() {
        verify(snapshotMgr)!!.getLatestSnapshot(resourceId, commitsRepository!!)
        verify(snapshot)!!.getVersion()
        verify(commitsRepository, atLeastOnce())!!.getLatestVersion(resourceId)
        verifyNoMoreInteractions(snapshot)
        verifyNoMoreInteractions(snapshotMgr)
        verifyNoMoreInteractions(commitsRepository)
    }

    @Test
    fun testGetCommitsFromStart() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getAllCommitsFromId(resourceId, Version.INITIAL_ID)).thenReturn(commits)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.GetCommits("m1", Version.INITIAL_ID))

        val expectedMsg = ActorMsgs.Commits("m1", Version(), commits)

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.getAllCommitsFromId(resourceId, Version.INITIAL_ID)
    }

    @Test
    fun testGetCommitsPartial() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getAllCommitsFromId(resourceId, commits[0].id)).thenReturn(commits.drop(1))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(true)
        `when`(commitsRepository?.getSequenceById(resourceId, commits[0].id)).thenReturn(0L)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.GetCommits("m1", commits[0].id))

        val expectedMsg = ActorMsgs.Commits("m1", Version(commits[0].id, 0L), commits.drop(1))

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
        verify(commitsRepository)!!.getAllCommitsFromId(resourceId, commits[0].id)
        verify(commitsRepository)!!.getSequenceById(resourceId, commits[0].id)
    }

    @Test
    fun testGetCommitsSequenceBroken() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(false)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.GetCommits("m1", commits[0].id))

        val expectedMsg = ActorMsgs.SequenceBroken("m1", Version())

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
    }

    @Test
    fun testPostCommitOnEmptyRepo() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version())
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(false)
        `when`(snapshot?.isCommitValid(commits[0])).thenReturn(ValidationResult(true))

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", Version().id, commits[0]))

        val expectedMsg = ActorMsgs.AckCommit("m1", Version(commits[0].id, 0L))

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
        verify(commitsRepository)!!.add(resourceId, commits[0])
        verify(snapshot)!!.isCommitValid(commits[0])
        verify(snapshot)!!.applyCommit(commits[0])
    }

    @Test
    fun testPostCommitOnNonEmptyRepo() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version(commits[0].id, 0L))
        `when`(snapshot?.getVersion()).thenReturn(Version(commits[0].id, 0L))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(true)
        `when`(commitsRepository?.existsId(resourceId, commits[1].id)).thenReturn(false)
        `when`(snapshot?.isCommitValid(commits[1])).thenReturn(ValidationResult(true))

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", commits[0].id, commits[1]))

        val expectedMsg = ActorMsgs.AckCommit("m1", Version(commits[1].id, 1L))

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
        verify(commitsRepository)!!.existsId(resourceId, commits[1].id)
        verify(commitsRepository)!!.add(resourceId, commits[1])
        verify(snapshot)!!.isCommitValid(commits[1])
        verify(snapshot)!!.applyCommit(commits[1])
    }

    @Test
    fun testPostCommitInvalidState() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version(commits[0].id, 0L))
        `when`(snapshot?.getVersion()).thenReturn(Version(commits[0].id, 0L))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(true)
        `when`(commitsRepository?.existsId(resourceId, commits[1].id)).thenReturn(false)
        val invalidStateMsg = "msg invalid state"
        `when`(snapshot?.isCommitValid(commits[1])).thenReturn(ValidationResult(false, invalidStateMsg))

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", commits[0].id, commits[1]))

        val expectedMsg = ActorMsgs.InvalidStateCommit("m1", commits[1].id, invalidStateMsg,
                Version().increment(commits[0].id))

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
        verify(commitsRepository)!!.existsId(resourceId, commits[1].id)
        verify(snapshot)!!.isCommitValid(commits[1])
    }

    @Test
    fun testPostCommitOutOfSync() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version(commits[1].id, 1L))
        `when`(snapshot?.getVersion()).thenReturn(Version(commits[1].id, 1L))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(false)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", Version.INITIAL_ID, commits[0]))

        val expectedMsg = ActorMsgs.OutOfSyncCommit("m1", commits[0].id, Version(commits[1].id, 1L))

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
    }

    @Test
    fun testPostCommitSequenceBroken() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version())
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(false)
        `when`(commitsRepository?.existsId(resourceId, commits[1].id)).thenReturn(false)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", commits[0].id, commits[1]))

        val expectedMsg = ActorMsgs.SequenceBroken("m1", Version())

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
        verify(commitsRepository)!!.existsId(resourceId, commits[1].id)
    }

    @Test
    fun testPostCommitInvalidId() {
        val commits = listOf(Commit("c1", "hey"), Commit("c2", "hey"))
        `when`(commitsRepository?.existsId(resourceId, commits[0].id)).thenReturn(true)
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(Version())

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.PostCommit("m1", commits[0].id, commits[0]))

        val expectedMsg = ActorMsgs.InvalidCommitId("m1", commits[0].id, Version())

        tp.expectMsg(expectedMsg)

        verify(commitsRepository)!!.existsId(resourceId, commits[0].id)
    }

    @Test
    fun testGetLatestVersion() {
        val latestVersion = Version("Hey", 42)
        `when`(commitsRepository?.getLatestVersion(resourceId)).thenReturn(latestVersion)
        `when`(snapshot?.getVersion()).thenReturn(latestVersion)

        val tp = TestProbe(system)
        val actorRef = factory.create(null, resourceId)

        tp.send(actorRef, ActorMsgs.GetLatestVersion("m1"))

        val expectedMsg = ActorMsgs.LatestVersion("m1", latestVersion)

        tp.expectMsg(expectedMsg)
    }
}

