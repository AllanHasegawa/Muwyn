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
import akka.testkit.TestActorRef
import akka.testkit.TestProbe
import io.muwyn.core.actors.ActorMsgs
import io.muwyn.core.actors.CommitActor
import io.muwyn.core.actors.CommitQueueActor
import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import org.hamcrest.Matchers.`is`
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Test
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit

class CommitQueueActorTest {
    class Factory : CommitActor.Factory {
        var actorToReturn: ActorRef? = null
        override fun create(context: ActorContext?, resourceId: String): ActorRef {
            return actorToReturn!!
        }
    }

    private data class Actors(val commitProbe: TestProbe, val queueActor: TestActorRef<CommitQueueActor>,
                              val routerProbe: TestProbe)

    val system = ActorSystem.create()

    val resourceId = "Resource1"

    val factory = Factory()

    val shortDuration = FiniteDuration.create(100, TimeUnit.MILLISECONDS)


    @After
    fun cleanUp() {
        factory.actorToReturn = null
    }


    private fun setupActors(): Actors {
        val commitActor = TestProbe(system)
        factory.actorToReturn = commitActor.ref()
        val queueActor = TestActorRef.create<CommitQueueActor>(system, CommitQueueActor.props(resourceId, factory))
        val routerActor = TestProbe(system)
        return Actors(commitActor, queueActor, routerActor)
    }


    @Test
    fun testGetCommits() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        routerProbe.send(queueActor, ActorMsgs.GetCommits("msg1", "cid0"))

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))

        commitProbe.expectMsg(ActorMsgs.GetCommits("msg1", "cid0"))
    }

    @Test
    fun testGetLatestVersion() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        routerProbe.send(queueActor, ActorMsgs.GetLatestVersion("msg1"))

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))

        commitProbe.expectMsg(ActorMsgs.GetLatestVersion("msg1"))
    }

    @Test
    fun testPostCommitsFromStart() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", Version.INITIAL_ID, commits))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", Version.INITIAL_ID, commits[0]))
        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.expectNoMsg(shortDuration)

        val ref = queueActor.underlyingActor()
        val msgIds = ref.msgIdToSender.keys
        assertThat(msgIds.size, `is`(1))

        val msgId = msgIds.first()
        assertThat(msgId, `is`("msg1"))

    }

    @Test
    fun testPostCommitsPartial() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", commits[0].id, commits.subList(1, 2)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(1))

        val msgId = msgIds.first()
        assertThat(msgId, `is`("msg1"))
    }


    @Test
    fun testAckCommit() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", commits[0].id, commits.subList(1, 2)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))

        commitProbe.send(queueActor, ActorMsgs.AckCommit("msg1", Version(commits[1].id, 1L)))

        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.AckPostCommits("msg1", Version(commits[1].id, 1L), listOf(commits[1].id)))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }

    @Test
    fun testAckCommits() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", Version.INITIAL_ID, commits))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", Version.INITIAL_ID, commits[0]))
        commitProbe.send(queueActor, ActorMsgs.AckCommit("msg1", Version(commits[0].id, 0L)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.send(queueActor, ActorMsgs.AckCommit("msg1", Version(commits[1].id, 1L)))

        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.AckPostCommits("msg1", Version(commits[1].id, 1L), commits.map { it.id }))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }

    @Test
    fun testOutOfSyncCommit() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", Version.INITIAL_ID, commits))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", Version.INITIAL_ID, commits[0]))
        commitProbe.send(queueActor, ActorMsgs.OutOfSyncCommit("msg1", commits[0].id, Version(commits[1].id, 1L)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.OutOfSync("msg1", Version(commits[1].id, 1L)))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }

    @Test
    fun testInvalidStateCommit() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", Version.INITIAL_ID, commits))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", Version.INITIAL_ID, commits[0]))
        commitProbe.send(queueActor, ActorMsgs.AckCommit("msg1", Version(commits[0].id, 0L)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        val invalidStateMsg = "invalid state"
        commitProbe.send(queueActor, ActorMsgs.InvalidStateCommit("msg1", commits[1].id,
                invalidStateMsg, Version(commits[1].id, 1L)))

        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.InvalidState("msg1", invalidStateMsg, commits[1].id,
                Version(commits[1].id, 1L), listOf(commits[0].id)))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }

    @Test
    fun testInvalidCommitIdFromChild() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", Version.INITIAL_ID, commits))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", Version.INITIAL_ID, commits[0]))
        commitProbe.send(queueActor, ActorMsgs.AckCommit("msg1", Version(commits[0].id, 0L)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.send(queueActor, ActorMsgs.InvalidCommitId("msg1", commits[1].id, Version(commits[0].id, 0L)))

        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.InvalidCommitIdAcks("msg1", commits[1].id, Version(commits[0].id, 0L),
                listOf(commits[0].id)))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }


    @Test
    fun testSequenceBroken() {
        val (commitProbe, queueActor, routerProbe) = setupActors()

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        routerProbe.send(queueActor, ActorMsgs.PostCommits("msg1", commits[0].id, commits.subList(1, 2)))

        commitProbe.expectMsg(ActorMsgs.PostCommit("msg1", commits[0].id, commits[1]))
        commitProbe.send(queueActor, ActorMsgs.SequenceBroken("msg1", Version()))

        commitProbe.expectNoMsg(shortDuration)

        routerProbe.expectMsg(ActorMsgs.SequenceBroken("msg1", Version()))
        routerProbe.expectNoMsg(shortDuration)

        val msgIds = queueActor.underlyingActor().msgIdToSender.keys
        assertThat(msgIds.size, `is`(0))
    }
}

