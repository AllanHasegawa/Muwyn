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

package io.muwyn.chatroom

import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import io.muwyn.core.data.increment
import io.muwyn.core.storage.CommitsRepository
import io.muwyn.core.storage.InvalidState
import io.muwyn.core.storage.Snapshot
import io.muwyn.core.storage.Snapshot.ValidationResult
import io.muwyn.core.storage.SnapshotMgr

class ChatSnapshotMgr : SnapshotMgr {
    override fun getLatestSnapshot(resourceId: String,
                                   commitsRepository: CommitsRepository): Snapshot {
        // I'm rebuilding the snapshot every time
        // It could be persisted
        val snapshot = ChatSnapshot(resourceId)
        commitsRepository.getAllCommitsFromId(resourceId, Version.INITIAL_ID).forEach {
            snapshot.applyCommit(it)
        }
        return snapshot
    }
}

class ChatSnapshot(private val chatRoomId: String) : Snapshot {
    data class UserMsg(val userId: String, val message: String)

    companion object {
        val JOIN_MSG = "Joined..."
        val LEFT_MSG = "Left..."
    }

    private var latestVersion = Version()
    private var msgs = mutableListOf<UserMsg>()
    private var usersOnline = mutableListOf<String>()

    override fun applyCommit(commit: Commit) {
        val cc = commit.content
        val userMsg = when (cc) {
            is Join -> {
                usersOnline.add(cc.userId)
                UserMsg(cc.userId, JOIN_MSG)
            }
            is Leave -> {
                usersOnline.remove(cc.userId)
                UserMsg(cc.userId, LEFT_MSG)
            }
            is Post -> UserMsg(cc.userId, cc.message)
            else -> throw InvalidState("Unknown commit content")
        }
        msgs.add(userMsg)
        latestVersion = latestVersion.increment(commit.id)
    }

    override fun getResourceId(): String {
        return chatRoomId
    }

    override fun getVersion(): Version {
        return latestVersion
    }

    override fun isCommitValid(commit: Commit): ValidationResult {
        println("Testing validity commit ${commit.id}")
        val cc = commit.content
        return when (cc) {
            is Join -> ValidationResult(!usersOnline.contains(cc.userId), "User already joined.")
            is Leave -> ValidationResult(usersOnline.contains(cc.userId), "User already left.")
            is Post -> ValidationResult(usersOnline.contains(cc.userId), "User must be in room to post.")
            else -> ValidationResult(false, "Unknown commit content")
        }
    }
}
