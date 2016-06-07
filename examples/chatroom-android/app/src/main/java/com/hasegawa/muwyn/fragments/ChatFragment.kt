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

package com.hasegawa.muwyn.fragments

import android.content.Context
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TextAppearanceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.hasegawa.muwyn.CommitEntry
import com.hasegawa.muwyn.ItemChatView
import com.hasegawa.muwyn.R
import io.muwyn.chatroom.messages.ChatRoomV1.CommitType

class ChatFragment : Fragment() {
    interface ChatListener {
        var renderIncrements: (List<CommitEntry>) -> Unit
        var renderNPendingPosts: (n: Int) -> Unit
        fun textPosted(text: String)

        fun pendingPostsDeleteListener()
        fun pendingPostsRepostListener()
    }

    class RvAdapter(val context: Context) : RecyclerView.Adapter<RvAdapter.ViewHolder>() {
        class ViewHolder(val chatView: ItemChatView) : RecyclerView.ViewHolder(chatView) {

        }

        val chatItems = mutableListOf<CommitEntry>()

        override fun getItemCount(): Int = chatItems.size

        override fun onBindViewHolder(holder: RvAdapter.ViewHolder?, position: Int) {
            val entry = chatItems[position]
            holder!!.chatView.setCloudSynced(entry.syncedToCloud)
            holder.chatView.setLocalSynced(entry.syncedToLocal)
            val text = when (entry.commit.type) {
                CommitType.Join.name -> joinStr(position.toLong(), entry.commit.userId!!)
                CommitType.Leave.name -> leftStr(position.toLong(), entry.commit.userId!!)
                CommitType.Post.name -> postStr(position.toLong(), entry.commit.userId!!, entry.commit.message!!)
                else -> throw RuntimeException("error, missing commit type...")
            }
            holder.chatView.setText(text)
        }

        override fun onCreateViewHolder(parent: ViewGroup?, viewType: Int): RvAdapter.ViewHolder? {
            return RvAdapter.ViewHolder(ItemChatView(parent!!.context))
        }


        private fun joinStr(seq: Long, userId: String): String {
            return context.resources.getString(R.string.chat_joined, seq, userId)
        }

        private fun leftStr(seq: Long, userId: String): String {
            return context.resources.getString(R.string.chat_left, seq, userId)
        }

        private fun postStr(seq: Long, userId: String, text: String): String {
            return context.resources.getString(R.string.chat_post, seq, userId, text)
        }
    }

    private lateinit var chatRv: RecyclerView
    private lateinit var postBt: Button
    private lateinit var postEt: EditText
    private lateinit var ppTv: TextView
    private lateinit var ppDeleteBt: Button
    private lateinit var ppRepostBt: Button


    private var chatListener: ChatListener? = null
    private lateinit var adapter: RvAdapter

    override fun onAttach(context: Context?) {
        super.onAttach(context)
        if (context is ChatListener) {
            chatListener = context
            context.renderIncrements = {
                handleRenderIncrements(it)
            }
            context.renderNPendingPosts = {
                handleRenderPendingPosts(it)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater?, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val root = inflater!!.inflate(R.layout.fragment_chat, container, false)

        chatRv = root.findViewById(R.id.chat_rv) as RecyclerView
        postBt = root.findViewById(R.id.chat_post_bt) as Button
        postBt.setOnClickListener { handlePostBtTouch() }
        postEt = root.findViewById(R.id.chat_et) as EditText

        ppTv = root.findViewById(R.id.chat_pp_tv) as TextView
        ppDeleteBt = root.findViewById(R.id.chat_pp_delete_bt) as Button
        ppDeleteBt.setOnClickListener { chatListener?.pendingPostsDeleteListener() }
        ppRepostBt = root.findViewById(R.id.chat_pp_repost_bt) as Button
        ppRepostBt.setOnClickListener { chatListener?.pendingPostsRepostListener() }


        postEt.setOnEditorActionListener { textView, action, keyEvent ->
            if (action == EditorInfo.IME_ACTION_DONE) {
                handlePostBtTouch()
                true
            } else {
                false
            }
        }

        adapter = RvAdapter(activity)

        val llm = LinearLayoutManager(activity)
        llm.stackFromEnd = true
        chatRv.layoutManager = llm
        chatRv.adapter = adapter

        return root
    }

    private fun handlePostBtTouch() {
        chatListener?.textPosted(postEt.text.toString())
        postEt.text.clear()
        postEt.clearFocus()
    }

    private fun handleRenderIncrements(commits: List<CommitEntry>) {
        adapter.chatItems.clear()
        adapter.chatItems.addAll(commits)
        adapter.notifyDataSetChanged()
        if (commits.size > 0) {
            chatRv.scrollToPosition(commits.size - 1)
        }
    }

    private fun handleRenderPendingPosts(nPendingPosts: Int) {
        val ssb = SpannableStringBuilder()
        val numberStyle = TextAppearanceSpan(activity, R.style.ChatPendingPostsNumber)
        val textStyle = TextAppearanceSpan(activity, R.style.ChatPendingPostsText)
        val numberText = nPendingPosts.toString() + " "
        ssb.append(numberText)
        ssb.setSpan(numberStyle, 0, numberText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        ssb.append(resources.getString(R.string.chat_pp_n_pending_posts))
        ssb.setSpan(textStyle, numberText.length, ssb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        ppTv.text = ssb
    }
}
