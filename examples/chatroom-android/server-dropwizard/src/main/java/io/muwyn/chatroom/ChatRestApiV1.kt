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

package io.muwyn.chatroom

import io.muwyn.chatroom.messages.ChatRoomV1
import io.muwyn.core.*
import java.util.concurrent.TimeUnit
import javax.validation.Valid
import javax.ws.rs.*
import javax.ws.rs.container.AsyncResponse
import javax.ws.rs.container.Suspended
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

@Path("/v1/chatrooms")
@Produces(MediaType.APPLICATION_JSON + ";charset=utf-8")
class ChatRestApiV1(var chatResource: Resource) {

    @GET
    @Path("/{chatId}/commits")
    fun getCommits(@PathParam("chatId") chatRoomId: String,
                   @QueryParam("from") commit: String?,
                   @Suspended asyncResponse: AsyncResponse) {
        asyncResponse.setTimeout(1, TimeUnit.SECONDS)
        if (commit == null) {
            asyncResponse.resume(Response
                    .status(Response.Status.BAD_REQUEST)
                    .entity("Missing commit id 'from' query parameter.")
                    .build())
            return
        }

        chatResource.getCommits(chatRoomId, commit).whenComplete { getCommitsRequest, throwable ->
            if (throwable != null) {
                asyncResponse.resume(WebApplicationException(throwable,
                        Response.Status.INTERNAL_SERVER_ERROR))
            } else {
                val response = when (getCommitsRequest) {
                    is Commits -> getCommitsRequest.toRestMsg()
                    is SequenceBroken -> getCommitsRequest.toRestMsg()
                    else -> throw RuntimeException("Unknown commit type.")
                }
                asyncResponse.resume(response)
            }
        }
    }

    @POST
    @Path("/{chatId}/users/{userId}/commits")
    fun postCommits(@PathParam("chatId") chatRoomId: String,
                    @PathParam("userId") userId: String,
                    @Valid body: ChatRoomV1.PostCommits,
                    @Suspended asyncResponse: AsyncResponse) {
        asyncResponse.setTimeout(1, TimeUnit.SECONDS)
        chatResource.postCommits(chatRoomId, body.previousCommit!!, body.commits!!.map { it.toCommit() })
                .whenComplete { postCommitsRequest, throwable ->
                    if (throwable != null) {
                        asyncResponse.resume(WebApplicationException(throwable,
                                Response.Status.INTERNAL_SERVER_ERROR))
                    } else {
                        val response = when (postCommitsRequest) {
                            is CommitsAcks -> postCommitsRequest.toRestMsg()
                            is SequenceBroken -> postCommitsRequest.toRestMsg()
                            is OutOfSync -> postCommitsRequest.toRestMsg()
                            is InvalidCommitId -> postCommitsRequest.toRestMsg()
                            is InvalidState -> postCommitsRequest.toRestMsg()
                            else -> throw RuntimeException("Unknown commit type.")
                        }
                        asyncResponse.resume(response)
                    }
                }
    }
}
