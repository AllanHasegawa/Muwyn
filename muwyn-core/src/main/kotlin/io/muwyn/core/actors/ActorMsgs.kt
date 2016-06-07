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

import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version
import io.muwyn.core.GetCommitsResponse
import io.muwyn.core.PostCommitsResponse
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.concurrent.CompletableFuture


internal class ActorMsgs {
    // Router Requests
    data class GetCommitsRouter(val resourceId: String, val fromCommitId: String,
                                val getCommitsSub: CompletableFuture<GetCommitsResponse>)

    data class PostCommitsRouter(val resourceId: String, val previousCommitId: String, val commits: List<Commit>,
                                 val postCommitsSub: CompletableFuture<PostCommitsResponse>)

    // CommitActors Requests
    data class PostCommits(val msgId: String, val previousCommitId: String, val commits: List<Commit>)

    data class GetCommits(val msgId: String, val fromCommitId: String)

    data class GetLatestVersion(val msgId: String)

    data class PostCommit(val msgId: String, val previousCommitId: String, val commit: Commit)

    // Responses
    data class AckPostCommits(val msgId: String, val latestVersion: Version, val commitIdsAcks: List<String>)

    data class Commits(val msgId: String, val previousVersion: Version, val commits: List<Commit>)

    data class LatestVersion(val msgId: String, val latestVersion: Version)

    data class InvalidState(val msgId: String, val msg: String, val invalidCommitId: String,
                            val lastVersion: Version, val commitIdsAcks: List<String>)

    data class OutOfSync(val msgId: String, val latestVersion: Version)

    data class SequenceBroken(val msgId: String, val latestVersion: Version)

    data class InvalidCommitIdAcks(val msgId: String, val invalidCommitId: String, val latestVersion: Version,
                                   val commitIdsAcks: List<String>)

    data class InvalidCommitId(val msgId: String, val invalidCommitId: String, val latestVersion: Version)

    data class AckCommit(val msgId: String, val version: Version)

    data class InvalidStateCommit(val msgId: String, val commitId: String, val msg: String, val latestVersion: Version)

    data class OutOfSyncCommit(val msgId: String, val commitId: String, val latestVersion: Version)
}

