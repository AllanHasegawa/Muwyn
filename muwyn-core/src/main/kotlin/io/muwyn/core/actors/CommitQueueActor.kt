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

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.UntypedActor
import io.muwyn.core.data.Version
import java.util.HashMap

internal class CommitQueueActor(val resourceId: String,
                                val commitActorFactory: CommitActor.Factory) : UntypedActor() {

    interface Factory {
        fun create(context: ActorContext?, resourceId: String): ActorRef
    }

    companion object {
        fun props(resourceId: String, factory: CommitActor.Factory) =
                Props.create(CommitQueueActor::class.java, resourceId, factory)
    }

    lateinit var roomActorRef: ActorRef
    // <MsgId, CommitsIds>
    val awaitingCommits = HashMap<String, List<String>>()
    // <MsgId, CommitsIds>
    val ackCommits = HashMap<String, List<Version>>()
    // <MsgId, Sender>
    val msgIdToSender = HashMap<String, ActorRef>()
    // <MsgId, Sender>
    val emptyPostsSenders = HashMap<String, ActorRef>()

    override fun preStart() {
        super.preStart()
        roomActorRef = commitActorFactory.create(context, resourceId)
    }

    override fun onReceive(msg: Any?) {
        when (msg) {
            is ActorMsgs.GetCommits -> roomActorRef.forward(msg, context)
            is ActorMsgs.GetLatestVersion -> roomActorRef.forward(msg, context)
            is ActorMsgs.PostCommits -> handlePostCommits(msg)
            is ActorMsgs.AckCommit -> handleAckCommit(msg)
            is ActorMsgs.OutOfSyncCommit -> handleOutOfSyncCommit(msg)
            is ActorMsgs.SequenceBroken -> handleSequenceBroken(msg)
            is ActorMsgs.InvalidStateCommit -> handleInvalidStateCommit(msg)
            is ActorMsgs.InvalidCommitId -> handleInvalidCommitId(msg)
            is ActorMsgs.LatestVersion -> handleLatestVersion(msg)
            else -> unhandled(msg)
        }
    }

    private fun handlePostCommits(msg: ActorMsgs.PostCommits) {
        if (msg.commits.isEmpty()) {
            emptyPostsSenders.put(msg.msgId, sender)
            roomActorRef.tell(ActorMsgs.GetLatestVersion(msg.msgId), self)
            return
        }

        val commits = msg.commits.mapIndexed { i, commit ->
            val previousId = if (i == 0) {
                msg.previousCommitId
            } else {
                msg.commits[i - 1].id
            }
            ActorMsgs.PostCommit(msg.msgId, previousId, commit)
        }

        msgIdToSender.put(msg.msgId, sender)
        awaitingCommits.put(msg.msgId, msg.commits.map { it.id })

        commits.forEach { roomActorRef.tell(it, self) }
    }

    private fun handleLatestVersion(msg: ActorMsgs.LatestVersion) {
        val oldSender = emptyPostsSenders[msg.msgId]
        if (oldSender != null) {
            oldSender.tell(ActorMsgs.AckPostCommits(msg.msgId, msg.latestVersion, emptyList()), self)
            emptyPostsSenders.remove(msg.msgId)
        }
    }

    private fun handleAckCommit(msg: ActorMsgs.AckCommit) {
        val commitId = msg.version.id
        val commitsRemaining = awaitingCommits[msg.msgId]?.filter { it != commitId } ?: return
        awaitingCommits.put(msg.msgId, commitsRemaining)

        val newAckList = if (ackCommits[msg.msgId] == null) {
            listOf(msg.version)
        } else {
            val m = ackCommits[msg.msgId]!!.toMutableList()
            m.add(msg.version)
            m
        }
        ackCommits.put(msg.msgId, newAckList)

        if (commitsRemaining.isEmpty()) {
            val sender = msgIdToSender[msg.msgId] ?: return
            val ackMsg = ActorMsgs.AckPostCommits(msg.msgId, msg.version,
                    ackCommits[msg.msgId]?.map { it.id } ?: emptyList())
            sender.tell(ackMsg, self)

            cleanMsgId(msg.msgId)
        }
    }

    private fun handleOutOfSyncCommit(msg: ActorMsgs.OutOfSyncCommit) {
        val response = ActorMsgs.OutOfSync(msg.msgId, msg.latestVersion)
        msgIdToSender[msg.msgId]?.tell(response, self)

        cleanMsgId(msg.msgId)
    }

    private fun handleSequenceBroken(msg: ActorMsgs.SequenceBroken) {
        val response = ActorMsgs.SequenceBroken(msg.msgId, msg.latestVersion)
        msgIdToSender[msg.msgId]?.tell(response, self)

        cleanMsgId(msg.msgId)
    }

    private fun handleInvalidStateCommit(msg: ActorMsgs.InvalidStateCommit) {
        val response = ActorMsgs.InvalidState(msg.msgId, msg.msg,
                msg.commitId, msg.latestVersion, ackCommits[msg.msgId]?.map { it.id } ?: emptyList())
        msgIdToSender[msg.msgId]?.tell(response, self)

        cleanMsgId(msg.msgId)
    }

    private fun handleInvalidCommitId(msg: ActorMsgs.InvalidCommitId) {
        val response = ActorMsgs.InvalidCommitIdAcks(msg.msgId, msg.invalidCommitId, msg.latestVersion,
                ackCommits[msg.msgId]?.map { it.id } ?: emptyList())
        msgIdToSender[msg.msgId]?.tell(response, self)

        cleanMsgId(msg.msgId)
    }

    private fun cleanMsgId(msgId: String) {
        emptyPostsSenders.remove(msgId)
        msgIdToSender.remove(msgId)
        awaitingCommits.remove(msgId)
        ackCommits.remove(msgId)
    }
}
