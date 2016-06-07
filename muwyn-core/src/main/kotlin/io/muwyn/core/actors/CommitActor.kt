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
import io.muwyn.core.data.increment
import io.muwyn.core.storage.CommitsRepository
import io.muwyn.core.storage.Snapshot
import io.muwyn.core.storage.SnapshotMgr
import org.apache.commons.collections4.queue.CircularFifoQueue


internal class CommitActor(val resourceId: String, val commitsRepository: CommitsRepository,
                           val snapshotMgr: SnapshotMgr) :
        UntypedActor() {

    interface Factory {
        fun create(context: ActorContext?, resourceId: String): ActorRef
    }

    companion object {

        private val NUM_MSGS_TO_IGNORE_CACHE = 100

        fun props(resourceId: String, commitsRepository: CommitsRepository, snapshotMgr: SnapshotMgr)
                = Props.create(CommitActor::class.java, resourceId, commitsRepository, snapshotMgr)
    }

    private val msgsToIgnore = CircularFifoQueue<String>(NUM_MSGS_TO_IGNORE_CACHE)
    private lateinit var snapshot: Snapshot

    override fun preStart() {
        snapshot = snapshotMgr.getLatestSnapshot(resourceId, commitsRepository)
        assert(snapshot.getVersion() == commitsRepository.getLatestVersion(resourceId),
                { "Snapshot should always be the latest version." })
    }

    override fun onReceive(msg: Any?) {
        when (msg) {
            is ActorMsgs.PostCommit -> handlePostCommit(msg)
            is ActorMsgs.GetCommits -> handleGetCommits(msg)
            is ActorMsgs.GetLatestVersion -> handleGetLatestVersion(msg)
            else -> unhandled(msg)
        }
    }

    private fun handlePostCommit(msg: ActorMsgs.PostCommit) {
        if (msgsToIgnore.contains(msg.msgId)) {
            return
        }
        // Verify sync sequence
        val latestCommitVersion = commitsRepository.getLatestVersion(resourceId)
        if (commitsRepository.existsId(resourceId, msg.commit.id)) {
            msgsToIgnore.add(msg.msgId)
            sender.tell(ActorMsgs.InvalidCommitId(msg.msgId, msg.commit.id, latestCommitVersion), self)
            return
        }
        if (!verifySequenceBroken(msg.previousCommitId)) {
            msgsToIgnore.add(msg.msgId)
            val response = ActorMsgs.SequenceBroken(msg.msgId, latestCommitVersion)
            sender.tell(response, self)
            return
        }
        if (latestCommitVersion.id != msg.previousCommitId) {
            msgsToIgnore.add(msg.msgId)
            val response = ActorMsgs.OutOfSyncCommit(msg.msgId, msg.commit.id, latestCommitVersion)
            sender.tell(response, self)
            return
        }

        // Verify if it violates the state (don't trust the client!)
        val validation = snapshot.isCommitValid(msg.commit)
        if (validation.isValid) {
            // Apply the changes
            commitsRepository.add(resourceId, msg.commit)
            snapshot.applyCommit(msg.commit)
            val newVersion = latestCommitVersion.increment(msg.commit.id)
            sender.tell(ActorMsgs.AckCommit(msg.msgId, newVersion), self)
        } else {
            msgsToIgnore.add(msg.msgId)
            val response = ActorMsgs.InvalidStateCommit(msg.msgId, msg.commit.id, validation.msg!!, latestCommitVersion)
            sender.tell(response, self)
        }

    }

    private fun handleGetCommits(msg: ActorMsgs.GetCommits) {
        if (!verifySequenceBroken(msg.fromCommitId)) {
            val latestCommitVersion = commitsRepository.getLatestVersion(resourceId)
            val resMsg = ActorMsgs.SequenceBroken(msg.msgId, latestCommitVersion)
            sender.tell(resMsg, self)
            return
        }

        val allCommits = commitsRepository.getAllCommitsFromId(resourceId, msg.fromCommitId)

        if (msg.fromCommitId == Version.INITIAL_ID) {
            sender.tell(ActorMsgs.Commits(msg.msgId, Version(), allCommits), self)
        } else {
            val fromCommitSeq = commitsRepository.getSequenceById(resourceId, msg.fromCommitId)
            val fromCommitVersion = Version(msg.fromCommitId, fromCommitSeq)
            sender.tell(ActorMsgs.Commits(msg.msgId, fromCommitVersion, allCommits), self)
        }
    }

    private fun handleGetLatestVersion(msg: ActorMsgs.GetLatestVersion) {
        val latestCommitVersion = commitsRepository.getLatestVersion(resourceId)
        sender.tell(ActorMsgs.LatestVersion(msg.msgId, latestCommitVersion), self)
    }

    private fun verifySequenceBroken(previousCommitId: String): Boolean {
        if (previousCommitId != Version.INITIAL_ID) {
            return commitsRepository.existsId(resourceId, previousCommitId)
        }
        return true
    }
}
