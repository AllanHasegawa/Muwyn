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

package io.muwyn.core

import io.muwyn.core.data.Commit
import io.muwyn.core.data.Version

interface GetCommitsResponse
interface PostCommitsResponse

data class Commits(val commits: List<Commit>, val previousVersion: Version) : GetCommitsResponse
data class CommitsAcks(val commitsAcks: List<String>, val latestVersion: Version) : PostCommitsResponse
data class SequenceBroken(val latestVersion: Version) : GetCommitsResponse, PostCommitsResponse
data class OutOfSync(val latestVersion: Version) : PostCommitsResponse
data class InvalidCommitId(val invalidCommitId: String, val commitsAcks: List<String>, val latestVersion: Version) : PostCommitsResponse
data class InvalidState(val invalidCommitId: String, val commitsAcks: List<String>, val latestVersion: Version,
                        val message: String) : PostCommitsResponse
