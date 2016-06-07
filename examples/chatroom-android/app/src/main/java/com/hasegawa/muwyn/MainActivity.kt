package com.hasegawa.muwyn

import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentPagerAdapter
import android.support.v4.view.ViewPager
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.widget.Toast
import com.hasegawa.muwyn.fragments.ChatFragment
import com.hasegawa.muwyn.fragments.ChooseChatRoomFragment
import com.hasegawa.muwyn.fragments.ChooseUserFragment
import com.hasegawa.muwyn.utils.unsubIfSubbed
import io.muwyn.chatroom.messages.ChatRoomV1
import retrofit2.Response
import rx.Observable
import rx.Subscriber
import rx.Subscription
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity(), ChooseUserFragment.UserListener,
        ChooseChatRoomFragment.ChatRoomListener, ChatFragment.ChatListener {

    companion object {
        val USERID_PAGE = 0
        val CHATROOMID_PAGE = 1
        val CHAT_PAGE = 2

        val SYNC_INTERVAL_MS = 500L
    }

    private lateinit var viewPager: ViewPager
    private lateinit var restApi: RetrofitRestApi

    private var userId: String? = null
    private var chatRoomId: String? = null
    private var connected = false
    override var renderIncrements: (List<CommitEntry>) -> Unit = {}
    override var renderNPendingPosts: (Int) -> Unit = {}

    private var chatIncsSub: Subscription? = null
    private var ppsSub: Subscription? = null
    private var tryToSyncSub: Subscription? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        restApi = RetrofitRestApi()

        viewPager = findViewById(R.id.view_pager) as ViewPager
        viewPager.adapter = viewPagerAdapter
        viewPager.addOnPageChangeListener(viewPagerListener)
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
    }

    override fun onPause() {
        super.onPause()
        chatIncsSub?.unsubIfSubbed()
        ppsSub?.unsubIfSubbed()
        tryToSyncSub?.unsubIfSubbed()
        disconnect()
    }

    override fun onBackPressed() {
        if (viewPager.currentItem > 0) {
            viewPager.setCurrentItem(viewPager.currentItem - 1, true)
        } else {
            super.onBackPressed()
        }
    }

    private val viewPagerListener = object : ViewPager.OnPageChangeListener {
        override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        }

        override fun onPageScrollStateChanged(state: Int) {
        }

        override fun onPageSelected(position: Int) {
            when (position) {
                CHAT_PAGE -> connect()
                else -> disconnect()
            }
        }
    }

    private val viewPagerAdapter = object : FragmentPagerAdapter(supportFragmentManager) {
        override fun getItem(position: Int): Fragment? {
            when (position) {
                USERID_PAGE -> return ChooseUserFragment()
                CHATROOMID_PAGE -> return ChooseChatRoomFragment()
                CHAT_PAGE -> return ChatFragment()
                else -> throw RuntimeException("This should never have happened :3")
            }
        }

        override fun getCount(): Int {
            if (userId == null) return 1
            if (chatRoomId == null) return 2
            else return 3
        }
    }

    override fun userChosen(id: String) {
        userId = id
        updateTitle()
        viewPagerAdapter.notifyDataSetChanged()
        viewPager.setCurrentItem(CHATROOMID_PAGE, true)
    }

    override fun chatRoomChosen(id: String) {
        chatRoomId = id
        updateTitle()
        viewPagerAdapter.notifyDataSetChanged()
        viewPager.setCurrentItem(CHAT_PAGE, true)
    }

    override fun textPosted(text: String) {
        saveInc(ChatRoomV1.CommitType.Post, text)
    }

    override fun pendingPostsDeleteListener() {
        ChatRepository.getPendingPosts(chatRoomId!!).take(1)
                .map { it.filter { it.userId == userId } }
                .forEach { it.forEach { ChatRepository.removePendingPost(chatRoomId!!, it) } }
    }

    override fun pendingPostsRepostListener() {
        ChatRepository.getPendingPosts(chatRoomId!!).take(1)
                .map { it.filter { it.userId == userId } }
                .forEach {
                    it.forEach {
                        saveInc(ChatRoomV1.CommitType.Post, it.message)
                    }
                }
        pendingPostsDeleteListener()
    }

    private fun connect() {
        if (!connected) {
            connected = true
            Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
            chatIncsSub?.unsubIfSubbed()
            chatIncsSub = ChatRepository.getCommits(chatRoomId!!)
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        Log.d("MA", "Rendering Incs $it")
                        renderIncrements(it)
                    }
            saveInc(ChatRoomV1.CommitType.Join)

            ppsSub?.unsubIfSubbed()
            ppsSub = ChatRepository.getPendingPosts(chatRoomId!!)
                    .map { it.filter { it.userId == userId } }
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { renderNPendingPosts(it.size) }


            tryToSyncSub?.unsubIfSubbed()
            tryToSyncSub = Observable.interval(SYNC_INTERVAL_MS, TimeUnit.MILLISECONDS)
                    .flatMap { tryToSync() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe {
                        object : Subscriber<Any>() {
                            override fun onCompleted() {
                            }

                            override fun onError(e: Throwable?) {
                                Log.d("Sync", "Error ${e!!.message}", e)
                            }

                            override fun onNext(t: Any?) {
                                Log.d("Sync", "Synced...")
                            }
                        }
                    }
        }

    }

    private fun disconnect() {
        if (connected) {
            connected = false
            Toast.makeText(this, "Disconnected", Toast.LENGTH_SHORT).show()
            chatIncsSub?.unsubIfSubbed()
            saveInc(ChatRoomV1.CommitType.Leave)

            ppsSub?.unsubIfSubbed()

            tryToSyncSub?.unsubIfSubbed()
        }
    }

    private fun tryToSync(): Observable<Any> {
        if (chatRoomId == null || userId == null) {
            return Observable.empty()
        }

        // pseudo code
        // get commits from latest already synced local version
        // if server returns no changes, then go to <post local commits>
        // if server returns changes, then go to
        //         <move local posts to pending posts | save get commits | post local commits>
        // if for some reason server returns an error message, <clean local cache and sync all>

        val roomLatestVc = ChatRepository.getLatestVersionOfSynced(chatRoomId!!)
        return restApi.getCommits(chatRoomId!!, roomLatestVc.commitId)
                .flatMap {
                    if (!it.isSuccessful) {
                        Observable.empty<Any>()
                    } else {
                        val res = it.body()
                        if (res.getCommits != null) {
                            if (res.getCommits!!.commits!!.isEmpty()) {
                                postLocalCommits()
                            } else {
                                movePostsToPendingState().flatMap {
                                    saveGetCommits(res.getCommits!!).flatMap {
                                        postLocalCommits()
                                    }
                                }
                            }
                        } else if (res.sequenceBroken != null) {
                            cleanLocalCacheAndSyncAll()
                        } else {
                            throw RuntimeException("Can't handle $res")
                        }

                    }
                }
    }

    private fun postLocalCommits(): Observable<Any> {
        return ChatRepository.getCommits(chatRoomId!!).take(1)
                .map { it.filter { !it.syncedToCloud } }
                .flatMap {
                    if (it.isEmpty()) {
                        Observable.empty<Response<ChatRoomV1.Response>>()
                    } else {
                        val latestVc = ChatRepository.getLatestVersionOfSynced(chatRoomId!!)
                        val incs = it.map { it.commit }
                        val req = ChatRoomV1.PostCommits(latestVc.commitId, incs)
                        restApi.postCommits(chatRoomId!!, userId!!, req).doOnError {
                            Log.e("SyncNetwork", "${it.message}")
                        }
                    }
                }
                .flatMap {
                    if (!it.isSuccessful) {
                        Observable.empty<Any>()
                    } else {
                        val res = it.body()
                        if (res.postCommits != null) {
                            saveSuccessSyncs(res.postCommits!!)
                        } else if (res.outOfSync != null) {
                            Observable.empty()
                        } else {
                            cleanLocalCacheAndSyncAll()
                        }
                    }
                }
    }

    private fun movePostsToPendingState(): Observable<Any> {
        return ChatRepository.getCommits(chatRoomId!!).take(1)
                .map {
                    // Get posts not synced and place as pending posts
                    it.filter { !it.syncedToCloud && it.commit.type == ChatRoomV1.CommitType.Post.name }
                            .forEach {
                                val pP = PendingPost(it.commit.userId!!, it.commit.message!!)
                                ChatRepository.addPendingPost(chatRoomId!!, pP)
                            }
                    // Remove all incremental changes not synced
                    it.filter { !it.syncedToCloud }
                            .forEach {
                                ChatRepository.removeCommitById(chatRoomId!!,
                                        it.commit.id!!)
                            }
                }
    }

    private fun saveGetCommits(msg: ChatRoomV1.Responses.GetCommits): Observable<Any> {
        return Observable.just(msg)
                .map {
                    val pVc = msg.previousVersion!!
                    it.commits?.mapIndexed { i, inc ->
                        val vc = ChatRoomV1.Version(inc.id!!, pVc.seq + i + 1)
                        CommitEntry(false, true,
                                ChatRoomV1.Commit(vc.commitId, inc.type, inc.userId, inc.message))
                    }?.forEach {
                        ChatRepository.upsertCommit(chatRoomId!!, it)
                    }
                    if (!ChatRepository.isUserOnline(chatRoomId!!, userId!!)) {
                        saveInc(ChatRoomV1.CommitType.Join)
                    }
                    ChatRepository.notifyChange(chatRoomId!!)
                }
    }

    private fun saveSuccessSyncs(obs: ChatRoomV1.Responses.PostCommits): Observable<Any> {
        return Observable.just(obs)
                .map { it.ackCommits!! }
                .map {
                    it.forEach {
                        val inc = ChatRepository.getCommitById(chatRoomId!!, it)
                        ChatRepository.upsertCommit(chatRoomId!!, inc!!.copy(syncedToCloud = true))
                    }
                    ChatRepository.notifyChange(chatRoomId!!)
                }
    }

    private fun cleanLocalCacheAndSyncAll(): Observable<Any> {
        return ChatRepository.getCommits(chatRoomId!!).take(1).map {
            it.forEach { ChatRepository.removeCommitById(chatRoomId!!, it.commit.id!!) }
        }.flatMap {
            restApi.getCommits(chatRoomId!!, "-1").doOnError {
                Log.e("SyncNetwork", "${it.message}")
            }
        }.map {
            if (it.isSuccessful) {
                val res = it.body()
                if (res.getCommits != null) {
                    res.getCommits!!.commits?.forEachIndexed { i, it ->
                        val inc = CommitEntry(false, true,
                                ChatRoomV1.Commit(it.id, it.type, it.userId, it.message))
                        ChatRepository.upsertCommit(chatRoomId!!, inc)

                    }
                    if (!ChatRepository.isUserOnline(chatRoomId!!, userId!!)) {
                        saveInc(ChatRoomV1.CommitType.Join)
                    }
                    ChatRepository.notifyChange(chatRoomId!!)
                }
            }
        }
    }

    private fun saveInc(type: ChatRoomV1.CommitType, text: String? = null) {
        if (chatRoomId == null || userId == null) {
            return
        }
        val chatRoomId = chatRoomId!!
        val userId = userId!!
        val latestVc = ChatRepository.getLatestVersion(chatRoomId)
        val incVc = ChatRoomV1.Version(UUID.randomUUID().toString(), latestVc.seq + 1)
        val inc = ChatRoomV1.Commit(incVc.commitId, type.name, userId, text)
//        Log.d("MainActivity", "Saving inc: $chatRoomId, $userId, $inc")
//        Log.d("MainActivity", "$latestVc --> $incVc")
        ChatRepository.upsertCommit(chatRoomId, CommitEntry(false, false, inc))
        ChatRepository.notifyChange(chatRoomId)
    }

    private fun updateTitle() {
        val userStr = if (userId == null) "" else "- $userId"
        val chatStr = if (chatRoomId == null) "" else "in $chatRoomId"
        supportActionBar?.title = "Muwyn $userStr $chatStr"
    }

}
