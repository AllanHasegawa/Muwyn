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

package io.muwyn.core.actors

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Terminated
import akka.actor.UntypedActor
import io.muwyn.core.Commits
import io.muwyn.core.CommitsAcks
import io.muwyn.core.GetCommitsResponse
import io.muwyn.core.InvalidCommitId
import io.muwyn.core.InvalidState
import io.muwyn.core.OutOfSync
import io.muwyn.core.PostCommitsResponse
import io.muwyn.core.SequenceBroken
import java.util.HashMap
import java.util.UUID
import java.util.concurrent.CompletableFuture

internal class CommitRouterActor(val commitQueueActorFactory: CommitQueueActor.Factory) : UntypedActor() {
    companion object {
        fun props(commitQueueActorFactory: CommitQueueActor.Factory): Props {
            return Props.create(CommitRouterActor::class.java, commitQueueActorFactory)
        }
    }

    // <resourceId, ActorRef>
    private val commitQueueRefs = HashMap<String, ActorRef>()
    // <MsgId, Subscription>
    private val getCommitsSubs = HashMap<String, CompletableFuture<GetCommitsResponse>>()
    private val postCommitsSubs = HashMap<String, CompletableFuture<PostCommitsResponse>>()

    override fun onReceive(msg: Any?) {
        when (msg) {
            is ActorMsgs.GetCommitsRouter -> handleGetCommitsRouter(msg)
            is ActorMsgs.PostCommitsRouter -> handlePostCommitsRouter(msg)
            is ActorMsgs.SequenceBroken -> handleSequenceBroken(msg)
            is ActorMsgs.AckPostCommits -> handleAckPostCommits(msg)
            is ActorMsgs.OutOfSync -> handleOutOfSync(msg)
            is ActorMsgs.InvalidState -> handleInvalidState(msg)
            is ActorMsgs.Commits -> handleCommits(msg)
            is ActorMsgs.InvalidCommitIdAcks -> handleInvalidCommitIdAcks(msg)
            is Terminated -> handleCommitQueueTerminated(msg)
            else -> unhandled(msg)
        }
    }

    private fun handleGetCommitsRouter(msg: ActorMsgs.GetCommitsRouter) {
        val commitQueueRef = getCommitQueueRef(msg.resourceId)

        val msgId = genMsgId()
        getCommitsSubs.put(msgId, msg.getCommitsSub)

        commitQueueRef.tell(ActorMsgs.GetCommits(msgId, msg.fromCommitId), self)
    }

    private fun handlePostCommitsRouter(msg: ActorMsgs.PostCommitsRouter) {
        val commitQueueRef = getCommitQueueRef(msg.resourceId)

        val msgId = genMsgId()
        postCommitsSubs.put(msgId, msg.postCommitsSub)

        val msgToSend = ActorMsgs.PostCommits(msgId, msg.previousCommitId, msg.commits)
        commitQueueRef.tell(msgToSend, self)
    }

    private fun handleSequenceBroken(msg: ActorMsgs.SequenceBroken) {
        val postCommitsSub = postCommitsSubs[msg.msgId]
        if (postCommitsSub != null) {
            postCommitsSub.complete(SequenceBroken(msg.latestVersion))
        } else {
            val getCommitsSub = getCommitsSubs[msg.msgId]
            getCommitsSub?.complete(SequenceBroken(msg.latestVersion))
        }
        clearMsgId(msg.msgId)
    }

    private fun handleAckPostCommits(msg: ActorMsgs.AckPostCommits) {
        postCommitsSubs[msg.msgId]?.complete(CommitsAcks(msg.commitIdsAcks, msg.latestVersion))
        clearMsgId(msg.msgId)
    }

    private fun handleOutOfSync(msg: ActorMsgs.OutOfSync) {
        postCommitsSubs[msg.msgId]?.complete(OutOfSync(msg.latestVersion))
        clearMsgId(msg.msgId)
    }

    private fun handleInvalidState(msg: ActorMsgs.InvalidState) {
        postCommitsSubs[msg.msgId]?.complete(InvalidState(msg.invalidCommitId, msg.commitIdsAcks, msg.lastVersion, msg.msg))
        clearMsgId(msg.msgId)
    }

    private fun handleCommits(msg: ActorMsgs.Commits) {
        getCommitsSubs[msg.msgId]?.complete(Commits(msg.commits, msg.previousVersion))
        clearMsgId(msg.msgId)
    }

    private fun handleInvalidCommitIdAcks(msg: ActorMsgs.InvalidCommitIdAcks) {
        postCommitsSubs[msg.msgId]?.complete(InvalidCommitId(msg.invalidCommitId, msg.commitIdsAcks, msg.latestVersion))
        clearMsgId(msg.msgId)
    }

    private fun clearMsgId(msgId: String) {
        getCommitsSubs.remove(msgId)
        postCommitsSubs.remove(msgId)
    }

    private fun handleCommitQueueTerminated(msg: Terminated) {
        val keyMaybe = commitQueueRefs.entries.find { it.value == msg.actor }?.key
        commitQueueRefs.remove(keyMaybe)
    }

    private fun getCommitQueueRef(resourceId: String): ActorRef {
        var commitQueueRef = commitQueueRefs[resourceId]
        if (commitQueueRef == null) {
            commitQueueRef = commitQueueActorFactory.create(context, resourceId)
            commitQueueRefs.put(resourceId, commitQueueRef)
            context.watch(commitQueueRef)
        }
        return commitQueueRef
    }

    private fun genMsgId() = UUID.randomUUID().toString()
}
