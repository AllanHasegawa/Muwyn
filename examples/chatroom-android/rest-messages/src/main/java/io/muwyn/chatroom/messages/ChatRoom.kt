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

package io.muwyn.chatroom.messages

import com.fasterxml.jackson.annotation.JsonInclude

class ChatRoomV1 {
    enum class CommitType { Join, Leave, Post }

    data class Version(val commitId: String = INITIAL_ID, val seq: Long = INITIAL_SEQ) {
        companion object {
            val INITIAL_ID = "-1"
            val INITIAL_SEQ = -1L
        }
    }

    data class Commit(
            val id: String? = null,
            val type: String? = null,
            val userId: String? = null,
            val message: String? = null)

    data class PostCommits(
            val previousCommit: String? = null,
            val commits: List<Commit>? = null)

    enum class ResponseType {
        GetCommits, PostCommits, InvalidState,
        OutOfSync, SequenceBroken, InvalidCommitId
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    data class Response(
            val postCommits: Responses.PostCommits? = null,
            val getCommits: Responses.GetCommits? = null,
            val invalidState: Responses.InvalidState? = null,
            val outOfSync: Responses.OutOfSync? = null,
            val sequenceBroken: Responses.SequenceBroken? = null,
            val invalidCommitId: Responses.InvalidCommitId? = null)

    class Responses {

        data class PostCommits(val latestVersion: Version? = null,
                               val ackCommits: List<String>? = null,
                               val type: String = ResponseType.PostCommits.name)

        data class GetCommits(val previousVersion: Version? = null,
                              val commits: List<Commit>? = null,
                              val type: String = ResponseType.GetCommits.name)

        data class InvalidState(val latestVersion: Version? = null,
                                val ackCommits: List<String>? = null,
                                val message: String? = null,
                                val type: String = ResponseType.InvalidState.name)

        data class OutOfSync(val latestVersion: Version? = null,
                             val type: String = ResponseType.OutOfSync.name)

        data class SequenceBroken(val latestVersion: Version? = null,
                                  val type: String = ResponseType.SequenceBroken.name)

        data class InvalidCommitId(val invalidCommitId: String? = null,
                                   val type: String = ResponseType.InvalidCommitId.name)
    }

}


fun ChatRoomV1.Commit.isValid(): Boolean {
    if (type != null && id != null && id.isNotEmpty()) {
        when (type) {
            ChatRoomV1.CommitType.Join.name -> return true
            ChatRoomV1.CommitType.Leave.name -> return true
            ChatRoomV1.CommitType.Post.name -> return message != null
            else -> return false
        }
    }
    return false
}
