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

import io.muwyn.chatroom.messages.ChatRoomV1
import io.muwyn.core.*
import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import io.muwyn.core.observables.*


fun ChatRoomV1.Commit.toCommit(): Commit {
    val commitType = when (this.type) {
        ChatRoomV1.CommitType.Post.name -> Post(userId!!, message!!)
        ChatRoomV1.CommitType.Join.name -> Join(userId!!)
        ChatRoomV1.CommitType.Leave.name -> Leave(userId!!)
        else -> throw RuntimeException("Unknown commit type: $type")
    }
    return Commit(id!!, commitType)
}

fun Version.toRestMsg() = ChatRoomV1.Version(this.id, this.sequence)

fun Commit.toRestMsg(): ChatRoomV1.Commit {
    val cc = this.content
    return when (cc) {
        is Post -> ChatRoomV1.Commit(this.id, ChatRoomV1.CommitType.Post.name, cc.userId, cc.message)
        is Join -> ChatRoomV1.Commit(this.id, ChatRoomV1.CommitType.Join.name, cc.userId)
        is Leave -> ChatRoomV1.Commit(this.id, ChatRoomV1.CommitType.Leave.name, cc.userId)
        else -> throw RuntimeException("Unknown commit.")
    }
}

fun Commits.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(getCommits = ChatRoomV1.Responses.GetCommits(
            previousVersion.toRestMsg(), commits.map { it.toRestMsg() }))
}

fun CommitsAcks.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(postCommits = ChatRoomV1.Responses.PostCommits(
            latestVersion.toRestMsg(), commitsAcks))
}

fun SequenceBroken.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(sequenceBroken = ChatRoomV1.Responses.SequenceBroken(
            latestVersion.toRestMsg()))
}

fun OutOfSync.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(outOfSync = ChatRoomV1.Responses.OutOfSync(
            latestVersion.toRestMsg()))
}

fun InvalidCommitId.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(invalidCommitId = ChatRoomV1.Responses.InvalidCommitId(
            invalidCommitId))
}

fun InvalidState.toRestMsg(): ChatRoomV1.Response {
    return ChatRoomV1.Response(invalidState = ChatRoomV1.Responses.InvalidState(
            latestVersion.toRestMsg(), commitsAcks, message))
}
