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
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import rx.Observable

class RetrofitRestApi {

    private val retrofit: Retrofit
    private val calls: RetrofitCalls

    init {
        val url = "http://192.168.1.184:8080/application/v1/chatrooms/"
        val interceptor = HttpLoggingInterceptor()
        interceptor.level = HttpLoggingInterceptor.Level.BODY
        val logAllRequests = false
        val client = if (logAllRequests) {
            OkHttpClient.Builder().addInterceptor(interceptor).build()
        } else {
            OkHttpClient.Builder().build()
        }
        retrofit = Retrofit.Builder()
                .client(client)
                .baseUrl(url)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        calls = retrofit.create(RetrofitCalls::class.java)
    }

    fun getCommits(chatRoomId: String, myLatestCommit: String):
            Observable<Response<ChatRoomV1.Response>> {
        val c = calls.callGetCommits(chatRoomId, myLatestCommit)
        return Observable.fromCallable { c.execute() }.onErrorResumeNext(Observable.empty())
    }

    fun postCommits(chatRoomId: String, userId: String,
                    request: ChatRoomV1.PostCommits): Observable<Response<ChatRoomV1.Response>> {
        val c = calls.callPostCommits(chatRoomId, userId, request)
        return Observable.fromCallable { c.execute() }.onErrorResumeNext(Observable.empty())
    }


    private interface RetrofitCalls {
        @GET("{chatRoomId}/commits")
        fun callGetCommits(@Path("chatRoomId") chatRoomId: String,
                           @Query("from") id: String): Call<ChatRoomV1.Response>

        @POST("{chatRoomId}/users/{userId}/commits")
        fun callPostCommits(@Path("chatRoomId") chatRoomId: String,
                            @Path("userId") userId: String, @Body body: ChatRoomV1.PostCommits):
                Call<ChatRoomV1.Response>
    }
}
