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

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.ActorSystem
import io.muwyn.core.actors.ActorMsgs.GetCommitsRouter
import io.muwyn.core.actors.ActorMsgs.PostCommitsRouter
import io.muwyn.core.actors.CommitActor
import io.muwyn.core.actors.CommitQueueActor
import io.muwyn.core.actors.CommitRouterActor
import io.muwyn.core.data.Commit
import io.muwyn.core.GetCommitsResponse
import io.muwyn.core.PostCommitsResponse
import io.muwyn.core.storage.CommitsRepository
import io.muwyn.core.storage.SnapshotMgr
import rx.Observable
import rx.subjects.BehaviorSubject
import rx.subjects.PublishSubject
import java.util.concurrent.CompletableFuture

class Resource(val commitsRepository: CommitsRepository, val snapshot: SnapshotMgr,
               var actorSystem: ActorSystem? = null) {

    private val commitActorFactory = object : CommitActor.Factory {
        override fun create(context: ActorContext?, resourceId: String): ActorRef {
            return context!!.actorOf(CommitActor.props(resourceId, commitsRepository, snapshot), resourceId)
        }
    }
    private val queueActorFactory = object : CommitQueueActor.Factory {
        override fun create(context: ActorContext?, resourceId: String): ActorRef {
            return context!!.actorOf(CommitQueueActor.props(resourceId, commitActorFactory), resourceId)
        }
    }

    private val routers: ActorRef

    init {
        if (actorSystem == null) {
            actorSystem = ActorSystem.create()
        }

        routers = actorSystem!!.actorOf(CommitRouterActor.props(queueActorFactory))
    }

    fun postCommits(resourceId: String, fromCommitId: String, commits: List<Commit>): CompletableFuture<PostCommitsResponse> {
        val future = CompletableFuture<PostCommitsResponse>()
        routers.tell(PostCommitsRouter(resourceId, fromCommitId, commits, future), ActorRef.noSender())
        return future
    }

    fun getCommits(resourceId: String, fromCommitId: String): CompletableFuture<GetCommitsResponse> {
        val future = CompletableFuture<GetCommitsResponse>()
        routers.tell(GetCommitsRouter(resourceId, fromCommitId, future), ActorRef.noSender())
        return future
    }
}
