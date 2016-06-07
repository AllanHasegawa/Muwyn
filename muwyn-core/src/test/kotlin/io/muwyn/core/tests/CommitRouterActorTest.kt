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
import io.muwyn.core.Commits
import io.muwyn.core.CommitsAcks
import io.muwyn.core.GetCommitsResponse
import io.muwyn.core.InvalidCommitId
import io.muwyn.core.InvalidState
import io.muwyn.core.OutOfSync
import io.muwyn.core.PostCommitsResponse
import io.muwyn.core.SequenceBroken
import io.muwyn.core.actors.ActorMsgs
import io.muwyn.core.actors.CommitQueueActor
import io.muwyn.core.actors.CommitRouterActor
import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.After
import org.junit.Assert.assertThat
import org.junit.Test
import scala.concurrent.duration.FiniteDuration
import scala.runtime.AbstractPartialFunction
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

class CommitRouterActorTest {

    class QueueFactory : CommitQueueActor.Factory {
        var actorToReturn: ActorRef? = null
        override fun create(context: ActorContext?, resourceId: String): ActorRef {
            return actorToReturn!!
        }
    }

    class MsgIdApf(var captureMsgId: (Any) -> Boolean) : AbstractPartialFunction<Any, Any>() {

        override fun apply(x: Any?): Any? {
            return x
        }

        override fun isDefinedAt(p0: Any?): Boolean {
            return captureMsgId(p0!!)
        }
    }

    private data class Actors(val queueProbe: TestProbe,
                              val routerActor: ActorRef,
                              val clientProbe: TestProbe)

    val system = ActorSystem.create()

    val resourceId = "Resource1"

    val queueFactory = QueueFactory()

    val shortDuration = FiniteDuration.create(100, TimeUnit.MILLISECONDS)


    @After
    fun cleanUp() {
        queueFactory.actorToReturn = null
    }

    private fun <T> subAndWait(barrier: CyclicBarrier, f: (Any) -> Unit): CompletableFuture<T> {
        val fut = CompletableFuture<T>()
        fut.whenComplete { t, throwable -> f(t!!); barrier.await() }
        return fut
    }

    private fun setupActors(): Actors {
        val queueProbe = TestProbe(system)
        queueFactory.actorToReturn = queueProbe.ref()
        val routerActor = system.actorOf(CommitRouterActor.props(queueFactory))
        val clientProbe = TestProbe(system)
        return Actors(queueProbe, routerActor, clientProbe)
    }

    @Test
    fun testGetCommitsOkay() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<GetCommitsResponse>(barrier, { msg = it })

        clientProbe.send(routerActor, ActorMsgs.GetCommitsRouter(resourceId, Version.INITIAL_ID, sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.GetCommits) {
                msgId = it.msgId
                it.fromCommitId == Version.INITIAL_ID
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "barr"))
        queueProbe.send(routerActor, ActorMsgs.Commits(msgId!!, Version(), commits))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is Commits, `is`(true))
        assertThat((msg as Commits).commits, `is`(commits))
        assertThat((msg as Commits).previousVersion, `is`(Version()))
    }


    @Test
    fun testGetCommitsSequenceBroken() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<GetCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.GetCommitsRouter(resourceId, commits[1].id, sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.GetCommits) {
                msgId = it.msgId
                it.fromCommitId == commits[1].id
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        queueProbe.send(routerActor, ActorMsgs.SequenceBroken(msgId!!, Version(commits[0].id, 0)))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is SequenceBroken, `is`(true))
        assertThat((msg as SequenceBroken).latestVersion, `is`(Version(commits[0].id, 0)))
    }

    @Test
    fun testPostCommitsOkay() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<PostCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.PostCommitsRouter(resourceId, Version.INITIAL_ID, commits, sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.PostCommits) {
                msgId = it.msgId
                it.commits == commits
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        queueProbe.send(routerActor, ActorMsgs.AckPostCommits(msgId!!, Version(commits[1].id, 1), commits.map { it.id }))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is CommitsAcks, `is`(true))
        assertThat((msg as CommitsAcks).commitsAcks, `is`(commits.map { it.id }))
        assertThat((msg as CommitsAcks).latestVersion, `is`(Version(commits[1].id, 1)))
    }

    @Test
    fun testPostCommitsSequenceBroken() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<PostCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.PostCommitsRouter(resourceId, commits[0].id,
                commits.subList(1, 2), sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.PostCommits) {
                msgId = it.msgId
                it.commits == commits.subList(1, 2)
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        queueProbe.send(routerActor, ActorMsgs.SequenceBroken(msgId!!, Version()))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is SequenceBroken, `is`(true))
        assertThat((msg as SequenceBroken).latestVersion, `is`(Version()))
    }

    @Test
    fun testPostCommitsOutOfSync() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<PostCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.PostCommitsRouter(resourceId, commits[0].id,
                commits.subList(1, 2), sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.PostCommits) {
                msgId = it.msgId
                it.commits == commits.subList(1, 2)
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        queueProbe.send(routerActor, ActorMsgs.OutOfSync(msgId!!, Version()))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is OutOfSync, `is`(true))
        assertThat((msg as OutOfSync).latestVersion, `is`(Version()))
    }

    @Test
    fun testPostCommitsInvalidState() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<PostCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.PostCommitsRouter(resourceId, Version.INITIAL_ID, commits, sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.PostCommits) {
                msgId = it.msgId
                it.commits == commits
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        val invalidStateMsg = "invalid state msg"
        queueProbe.send(routerActor, ActorMsgs.InvalidState(msgId!!, invalidStateMsg, commits[1].id,
                Version(commits[0].id, 0), listOf(commits[0].id)))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is InvalidState, `is`(true))
        assertThat((msg as InvalidState).latestVersion, `is`(Version(commits[0].id, 0)))
        assertThat((msg as InvalidState).commitsAcks, `is`(listOf(commits[0].id)))
        assertThat((msg as InvalidState).message, `is`(invalidStateMsg))
    }

    @Test
    fun testPostCommitsInvalidCommitId() {
        val (queueProbe, routerActor, clientProbe) = setupActors()

        val barrier = CyclicBarrier(2)
        var msg: Any? = null
        val sub = subAndWait<PostCommitsResponse>(barrier, { msg = it })

        val commits = listOf(Commit("c0", "foo"), Commit("c1", "bar"))
        clientProbe.send(routerActor, ActorMsgs.PostCommitsRouter(resourceId, Version.INITIAL_ID, commits, sub))

        var msgId: String? = null
        val pf = MsgIdApf {
            if (it is ActorMsgs.PostCommits) {
                msgId = it.msgId
                it.commits == commits
            } else {
                false
            }
        }
        queueProbe.expectMsgPF(FiniteDuration.create(1, TimeUnit.SECONDS), "", pf)
        queueProbe.expectNoMsg(shortDuration)

        queueProbe.send(routerActor, ActorMsgs.InvalidCommitIdAcks(msgId!!, commits[1].id,
                Version(commits[0].id, 0), listOf(commits[0].id)))

        barrier.await(1, TimeUnit.SECONDS)
        assertThat(msg, notNullValue())
        assertThat(msg is InvalidCommitId, `is`(true))
        assertThat((msg as InvalidCommitId).latestVersion, `is`(Version(commits[0].id, 0)))
        assertThat((msg as InvalidCommitId).commitsAcks, `is`(listOf(commits[0].id)))
    }
}
