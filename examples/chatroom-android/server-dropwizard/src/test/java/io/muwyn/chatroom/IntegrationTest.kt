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
import io.dropwizard.testing.junit.ResourceTestRule
import io.muwyn.core.Resource
import io.muwyn.core.data.Version
import org.glassfish.jersey.test.grizzly.GrizzlyTestContainerFactory
import org.hamcrest.Matchers.*
import org.junit.After
import org.junit.Assert.*
import org.junit.ClassRule
import org.junit.Test
import javax.ws.rs.client.Entity
import javax.ws.rs.core.MediaType

class IntegrationTest {
    companion object {
        var chatResource = Resource(ChatCommitsRepository(), ChatSnapshotMgr())
        var chatRestApi = ChatRestApiV1(chatResource)

        @ClassRule @JvmField
        var api = ResourceTestRule.builder().addResource(chatRestApi)
                .setTestContainerFactory(GrizzlyTestContainerFactory())
                .build()
    }

    val chatRoomPath = "/v1/chatrooms"
    val chatRoomId = "hi"
    val userId = "user1"
    val getCommitsResourcePath = "$chatRoomPath/$chatRoomId/commits"
    val postCommitsResourcePath = "$chatRoomPath/$chatRoomId/users/$userId/commits"

    @After
    fun cleanUp() {
        chatResource = Resource(ChatCommitsRepository(), ChatSnapshotMgr())
        chatRestApi.chatResource = chatResource
    }

    @Test
    fun testEmptyGetCommits() {
        val response = api.jerseyTest.target(getCommitsResourcePath)
                .queryParam("from", Version.INITIAL_ID)
                .request()
                .get()

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.getCommits, notNullValue())
        assertThat(ent.getCommits!!.previousVersion, `is`(Version().toRestMsg()))
        assertThat(ent.getCommits!!.commits, notNullValue())
        assertThat(ent.getCommits!!.commits!!.size, `is`(0))
    }

    @Test
    fun testPostCommits() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i2", ChatRoomV1.CommitType.Post.name, userId, "Hello"),
                ChatRoomV1.Commit("i3", ChatRoomV1.CommitType.Leave.name, userId)
        )
        val request = ChatRoomV1.PostCommits(Version().id, incs)
        val response = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.postCommits, notNullValue())
        assertThat(ent.postCommits!!.latestVersion!!.seq, `is`(2L))
        assertThat(ent.postCommits!!.latestVersion!!.commitId, notNullValue())
        assertThat(ent.postCommits!!.ackCommits, notNullValue())
        assertThat(ent.postCommits!!.ackCommits!!.size, `is`(3))
        assertThat(ent.postCommits!!.type, `is`(ChatRoomV1.ResponseType.PostCommits.name))
    }

    @Test
    fun testEmptyPostCommits() {
        val incs = emptyList<ChatRoomV1.Commit>()
        val request = ChatRoomV1.PostCommits(Version().id, incs)
        val response = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.postCommits, notNullValue())
        assertThat(ent.postCommits!!.latestVersion!!.seq, `is`(Version.INITIAL_SEQUENCE))
        assertThat(ent.postCommits!!.latestVersion!!.commitId, `is`(Version.INITIAL_ID))
        assertThat(ent.postCommits!!.ackCommits, notNullValue())
        assertThat(ent.postCommits!!.ackCommits!!.size, `is`(0))
        assertThat(ent.postCommits!!.type, `is`(ChatRoomV1.ResponseType.PostCommits.name))
    }

    @Test
    fun testPostCommitsWithInvalidState() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i2", ChatRoomV1.CommitType.Post.name, userId, "Hello"),
                ChatRoomV1.Commit("i3", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i4", ChatRoomV1.CommitType.Leave.name, userId)
        )
        val request = ChatRoomV1.PostCommits(Version().id, incs)
        val response = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.invalidState, notNullValue())
        assertThat(ent.invalidState!!.latestVersion!!.seq, `is`(1L))
        assertThat(ent.invalidState!!.latestVersion!!.commitId, notNullValue())
        assertThat(ent.invalidState!!.ackCommits, notNullValue())
        assertThat(ent.invalidState!!.ackCommits!!.size, `is`(2))
        assertThat(ent.invalidState!!.type, `is`(ChatRoomV1.ResponseType.InvalidState.name))
    }

    @Test
    fun testPostCommitsWithSequenceBroken() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i2", ChatRoomV1.CommitType.Post.name, userId, "Hello"),
                ChatRoomV1.Commit("i3", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i4", ChatRoomV1.CommitType.Leave.name, userId)
        )
        val request = ChatRoomV1.PostCommits("hi", incs)
        val response = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(request, MediaType.APPLICATION_JSON_TYPE))

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.sequenceBroken, notNullValue())
        assertThat(ent.sequenceBroken!!.latestVersion!!.seq, `is`(Version.INITIAL_SEQUENCE))
        assertThat(ent.sequenceBroken!!.latestVersion!!.commitId, notNullValue())
        assertThat(ent.sequenceBroken!!.type, `is`(ChatRoomV1.ResponseType.SequenceBroken.name))
    }

    @Test
    fun testEmptyGetCommitsWithSequenceBroken() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId)
        )
        val postReq = ChatRoomV1.PostCommits(Version().id, incs)
        api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(postReq, MediaType.APPLICATION_JSON_TYPE))

        val response = api.jerseyTest.target(getCommitsResourcePath)
                .queryParam("from", "i2")
                .request()
                .get()

        val ent = response.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.sequenceBroken, notNullValue())
        assertThat(ent.sequenceBroken!!.latestVersion, `is`(Version("i1", 0L).toRestMsg()))
        assertThat(ent.sequenceBroken!!.type, `is`(ChatRoomV1.ResponseType.SequenceBroken.name))
    }

    @Test
    fun testGetCommits() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i2", ChatRoomV1.CommitType.Post.name, userId, "Hello"),
                ChatRoomV1.Commit("i3", ChatRoomV1.CommitType.Leave.name, userId)
        )
        val postReq = ChatRoomV1.PostCommits(Version().id, incs)
        val postRes = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(postReq, MediaType.APPLICATION_JSON_TYPE))
        val postEn = postRes.readEntity(ChatRoomV1.Response::class.java)
        println(postEn)

        val getRes = api.jerseyTest.target(getCommitsResourcePath)
                .queryParam("from", postEn.postCommits!!.ackCommits!![0])
                .request()
                .get()

        val ent = getRes.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.getCommits, notNullValue())
        assertThat(ent.getCommits!!.previousVersion, `is`(
                Version(postEn.postCommits!!.ackCommits!![0], 0).toRestMsg()))
        assertThat(ent.getCommits!!.commits, notNullValue())
        assertThat(ent.getCommits!!.commits!!.size, `is`(2))


        assertThat(ent.getCommits!!.commits!![0].id, `is`("i2"))
        assertThat(ent.getCommits!!.commits!![0].type, `is`(ChatRoomV1.CommitType.Post.name))
        assertThat(ent.getCommits!!.commits!![0].userId, `is`(userId))
        assertThat(ent.getCommits!!.commits!![0].message, `is`("Hello"))

        assertThat(ent.getCommits!!.commits!![1].id, `is`("i3"))
        assertThat(ent.getCommits!!.commits!![1].type, `is`(ChatRoomV1.CommitType.Leave.name))
        assertThat(ent.getCommits!!.commits!![1].userId, `is`(userId))
    }


    @Test
    fun testGetCommitsClientAhead() {
        val incs = listOf(
                ChatRoomV1.Commit("i1", ChatRoomV1.CommitType.Join.name, userId),
                ChatRoomV1.Commit("i2", ChatRoomV1.CommitType.Post.name, userId, "Hello"),
                ChatRoomV1.Commit("i3", ChatRoomV1.CommitType.Leave.name, userId)
        )
        val postReq = ChatRoomV1.PostCommits(Version().id, incs)
        val postRes = api.jerseyTest.target(postCommitsResourcePath)
                .request()
                .post(Entity.entity(postReq, MediaType.APPLICATION_JSON_TYPE))
        val postEn = postRes.readEntity(ChatRoomV1.Response::class.java)

        val getRes = api.jerseyTest.target(getCommitsResourcePath)
                .queryParam("from", "i4")
                .request()
                .get()

        val ent = getRes.readEntity(ChatRoomV1.Response::class.java)
        assertThat(ent, notNullValue())
        assertThat(ent.sequenceBroken, notNullValue())
        assertThat(ent.sequenceBroken!!.latestVersion, `is`(Version(postEn.postCommits!!.ackCommits!![2], 2L).toRestMsg()))
        assertThat(ent.sequenceBroken!!.type, `is`(ChatRoomV1.ResponseType.SequenceBroken.name))
    }
}
