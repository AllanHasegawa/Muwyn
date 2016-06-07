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
import io.muwyn.core.storage.CommitsRepository
import io.muwyn.core.storage.ResourceNonExistant
import java.util.*

class ChatCommitsRepository : CommitsRepository {

    val repository = HashMap<String, MutableList<Commit>>()

    override fun add(resourceId: String, commit: Commit) {
        val list = repository[resourceId]
        if (list == null) {
            repository.put(resourceId, mutableListOf(commit))
        } else {
            list.add(commit)
        }
    }

    override fun existsId(resourceId: String, commitId: String): Boolean {
        return getById(resourceId, commitId) != null
    }

    override fun getAllCommitsFromId(resourceId: String, commitId: String): List<Commit> {
        val list = repository[resourceId] ?: emptyList<Commit>()
        val iof = list.indexOfFirst { it.id == commitId }
        return list.subList(iof + 1, list.size)
    }

    override fun getById(resourceId: String, commitId: String): Commit? {
        return repository[resourceId]?.firstOrNull { it.id == commitId }
    }

    override fun getLatestVersion(resourceId: String): Version {
        val list = repository[resourceId]
        return if (list == null) {
            Version()
        } else {
            Version(list.last().id, list.size.toLong() - 1L)
        }
    }

    override fun getSequenceById(resourceId: String, commitId: String): Long {
        val indexMaybe = repository[resourceId]?.indexOfFirst { it.id == commitId }
        return indexMaybe?.toLong() ?:
                throw ResourceNonExistant("Commit with id $commitId does not exists")
    }
}
