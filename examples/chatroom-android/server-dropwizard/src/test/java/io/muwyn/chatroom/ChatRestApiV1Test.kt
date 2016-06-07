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

import akka.actor.ActorSystem
import akka.testkit.TestProbe
import io.muwyn.chatroom.messages.ChatRoomV1
import io.muwyn.chatroom.messages.isValid
import io.dropwizard.jackson.Jackson
import io.dropwizard.testing.junit.ResourceTestRule
import io.muwyn.chatroom.ChatRestApiV1
import org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory
import org.hamcrest.Matchers.*
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import javax.ws.rs.client.Entity

class ChatRestApiV1Test {

    companion object {
        val actorSystem = ActorSystem.create()
        val dispatcher = TestProbe(actorSystem)
        val defaultDuration = FiniteDuration(1, TimeUnit.SECONDS)

//        @ClassRule @JvmField
//        val api = ResourceTestRule.builder().addResource(ChatRestApiV1(dispatcher.ref()))
//                .setTestContainerFactory(GrizzlyTestContainerFactory())
//                .build()
    }

    val chatRoomPath = "/v1/chatrooms"

    val chatRoomId = "hi"

    val userId = "user1"

    val getIncrementResourcePath = "$chatRoomPath/$chatRoomId/commits"
    val postIncrementResourcePath = "$chatRoomPath/$chatRoomId/users/$userId/commits"

    @Test
    fun testGetIncrements() {
//        api.jerseyTest.target(getIncrementResourcePath)
//                .queryParam("from", "blah")
//                .request()
//                .get()

//        dispatcher.expectMsgClass(defaultDuration,
//                ChatRestRouterActor.Msgs.GetCommitsRequest::class.java)
    }

    @Test
    fun testPostIncrements() {
        val increments =
                listOf(ChatRoomV1.Commit("id0", ChatRoomV1.CommitType.Join.name, userId),
                        ChatRoomV1.Commit("id1", ChatRoomV1.CommitType.Post.name, userId, "hello"))
                        .filter { it.isValid() }

//        val response = api.jerseyTest.target(postIncrementResourcePath)
//                .request()
//                .post(Entity.json(ChatRoomV1.PostCommits("blah", increments)))

//        dispatcher.expectMsgClass(defaultDuration,
//                ChatRestRouterActor.Msgs.PostCommitsRequest::class.java)
    }

    @Test
    fun testJsonPostSerialization() {
//        val vcStr = """"latestVersion": {"sequence": 42, "id": "foo"}"""
        val previousCommit = """"previousCommit": "foo""""
        val userStr = """"userId": "user1""""
        val chatRoomStr = """"chatRoomId": "hello""""
        val joinStr = """{"id": "inc1", "type": "Join", "userId": "$userId"}"""
        val postStr = """{"id": "inc2", "type": "Post", "userId": "$userId", "message":"hi :3"}"""
        val invalidStr = """{"type": "Wat", "message":"bar"}"""
        val incrementsStr = """"commits": [$joinStr, $postStr, $invalidStr]"""

        val request = """{$previousCommit, $incrementsStr}"""

        val om = Jackson.newObjectMapper()

        val postSync = om.readValue(request, ChatRoomV1.PostCommits::class.java)

//        assertThat(postSync.chatRoomId, `is`("hello"))
//        assertThat(postSync.userId, `is`("user1"))
//        assertThat(postSync.latestVersion, `is`(VersionControl(42, "foo")))
        assertThat(postSync.previousCommit, `is`("foo"))
//        assertThat(postSync.increments!!.size, `is`(3))
        assertThat(postSync.commits!!.size, `is`(3))

        val incs = postSync.commits!!.filter { it.isValid() }
        assertThat(incs.size, `is`(2))
        assertThat(incs[0], `is`(ChatRoomV1.Commit("inc1", ChatRoomV1.CommitType.Join.name, userId)))
        assertThat(incs[1], `is`(ChatRoomV1.Commit("inc2", ChatRoomV1.CommitType.Post.name, userId, "hi :3")))
    }
}
