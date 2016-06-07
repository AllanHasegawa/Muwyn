/*
 * Copyright 2016 Allan Yoshio Hasegawa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hasegawa.muwyn

import io.muwyn.chatroom.messages.ChatRoomV1
import rx.Observable
import rx.subjects.BehaviorSubject
import java.util.*

data class CommitEntry(val syncedToLocal: Boolean,
                       val syncedToCloud: Boolean,
                       val commit: ChatRoomV1.Commit)

data class PendingPost(val userId: String,
                       val message: String)

object ChatRepository {
    // <ChatRoomId, CommitEntry>
    private val commits = HashMap<String, MutableList<CommitEntry>>()
    private val commitsLock = Any()

    // <ChatRoomId, CommitEntry>
    private val getSubjects = HashMap<String, MutableList<BehaviorSubject<List<CommitEntry>>>>()
    private val pendingPostsSubjects = HashMap<String, MutableList<BehaviorSubject<List<PendingPost>>>>()

    // <ChatRoomId, CommitEntry>
    private val pendingPosts = HashMap<String, MutableList<PendingPost>>()
    private val pendingPostsLock = Any()

    fun getPendingPosts(chatRoomId: String): Observable<List<PendingPost>> {
        val sub = BehaviorSubject.create(pendingPosts[chatRoomId]?.toList() ?: emptyList())
        synchronized(pendingPostsLock) {
            val list = pendingPostsSubjects[chatRoomId]
            if (list == null) {
                pendingPostsSubjects.put(chatRoomId, mutableListOf(sub))
            } else {
                list.add(sub)
            }
        }
        return sub
    }

    fun addPendingPost(chatRoomId: String, pendingPost: PendingPost) {
        synchronized(pendingPostsLock) {
            val list = pendingPosts[chatRoomId]
            if (list == null) {
                pendingPosts[chatRoomId] = mutableListOf(pendingPost)
            } else {
                list.add(pendingPost)
            }
            pendingPostsSubjects[chatRoomId]?.forEach {
                it.onNext(pendingPosts[chatRoomId]?.toList() ?: emptyList())
            }
        }
    }

    fun removePendingPost(chatRoomId: String, pendingPost: PendingPost) {
        synchronized(pendingPostsLock) {
            pendingPosts[chatRoomId]?.remove(pendingPost)
            pendingPostsSubjects[chatRoomId]?.forEach {
                it.onNext(pendingPosts[chatRoomId]?.toList() ?: emptyList())
            }
        }
    }

    fun getLatestVersion(chatRoomId: String): ChatRoomV1.Version {
        val list = commits[chatRoomId]
        return synchronized(commitsLock) {
            if (list == null) {
                ChatRoomV1.Version()
            } else {
                val seq = list.size - 1
                ChatRoomV1.Version(list.last().commit.id!!, seq.toLong())
            }
        }
    }

    fun getLatestVersionOfSynced(chatRoomId: String): ChatRoomV1.Version {
        val list = commits[chatRoomId]
        return synchronized(commitsLock) {
            if (list == null) {
                ChatRoomV1.Version()
            } else {
                val fList = list.filter { it.syncedToCloud }
                val id = fList.lastOrNull()?.commit?.id
                if (id == null) {
                    ChatRoomV1.Version()
                } else {
                    val seq = fList.size - 1
                    ChatRoomV1.Version(id, seq.toLong())
                }
            }
        }
    }

    fun removeCommitById(chatRoomId: String, id: String) {
        synchronized(commitsLock) {
            val oldIndex = commits[chatRoomId]?.indexOfFirst { it.commit.id == id } ?: -1
            if (oldIndex >= 0) {
                commits[chatRoomId]?.removeAt(oldIndex)
            }
        }
    }

    fun getCommitById(chatRoomId: String, id: String): CommitEntry? {
        return synchronized(commitsLock) { commits[chatRoomId]?.firstOrNull { it.commit.id == id } }
    }

    fun upsertCommit(chatRoomId: String, commit: CommitEntry) {
        val newInc = commit.copy(syncedToLocal = true)
        synchronized(commitsLock) {
            val oldIndex = commits[chatRoomId]?.indexOfFirst {
                it.commit.id == commit.commit.id
            } ?: -1
            if (oldIndex >= 0) {
                commits[chatRoomId]?.set(oldIndex, newInc)
            } else {
                val list = commits[chatRoomId]
                if (list == null) {
                    commits[chatRoomId] = mutableListOf(newInc)
                } else {
                    list.add(newInc)
                }
            }
        }
    }

    fun getCommits(chatRoomId: String): Observable<List<CommitEntry>> {
        var sub: BehaviorSubject<List<CommitEntry>>? = null
        synchronized(commitsLock) {
            sub = BehaviorSubject.create(commits[chatRoomId]?.toList() ?: emptyList())
            val list = getSubjects[chatRoomId]
            if (list == null) {
                getSubjects.put(chatRoomId, mutableListOf(sub!!))
            } else {
                list.add(sub!!)
            }
        }
        return sub!!
    }

    fun isUserOnline(chatRoomId: String, userId: String): Boolean {
        return commits[chatRoomId]?.filter { it.commit.userId == userId }?.fold(false) {
            isOnline, entry ->
            if (entry.commit.type == ChatRoomV1.CommitType.Leave.name) {
                false
            } else {
                true
            }
        } ?: false
    }

    fun notifyChange(chatRoomId: String) {
        synchronized(commitsLock) {
            getSubjects[chatRoomId]?.forEach {
                it.onNext(commits[chatRoomId]?.toList() ?: emptyList())
            }
        }
    }

    fun reset() {
        commits.clear()
        getSubjects.values.forEach { it.forEach { it.onCompleted() } }
        getSubjects.clear()
        pendingPostsSubjects.values.forEach { it.forEach { it.onCompleted() } }
        pendingPostsSubjects.clear()
    }
}
