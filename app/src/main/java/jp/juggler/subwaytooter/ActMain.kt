package jp.juggler.subwaytooter

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.KeyEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import jp.juggler.subwaytooter.action.accessTokenPrompt
import jp.juggler.subwaytooter.action.timeline
import jp.juggler.subwaytooter.actmain.ActMainPhoneViews
import jp.juggler.subwaytooter.actmain.ActMainTabletViews
import jp.juggler.subwaytooter.actmain.SideMenuAdapter
import jp.juggler.subwaytooter.actmain.afterNotificationGranted
import jp.juggler.subwaytooter.actmain.closePopup
import jp.juggler.subwaytooter.actmain.defaultInsertPosition
import jp.juggler.subwaytooter.actmain.handleIntentUri
import jp.juggler.subwaytooter.actmain.handleSharedIntent
import jp.juggler.subwaytooter.actmain.importAppData
import jp.juggler.subwaytooter.actmain.initPhoneTablet
import jp.juggler.subwaytooter.actmain.initUIQuickPost
import jp.juggler.subwaytooter.actmain.isOrderChanged
import jp.juggler.subwaytooter.actmain.justifyWindowContentPortrait
import jp.juggler.subwaytooter.actmain.launchDialogs
import jp.juggler.subwaytooter.actmain.onBackPressedImpl
import jp.juggler.subwaytooter.actmain.onClickImpl
import jp.juggler.subwaytooter.actmain.onCompleteActPost
import jp.juggler.subwaytooter.actmain.onMyClickableSpanClickedImpl
import jp.juggler.subwaytooter.actmain.phoneTab
import jp.juggler.subwaytooter.actmain.refreshAfterPost
import jp.juggler.subwaytooter.actmain.reloadAccountSetting
import jp.juggler.subwaytooter.actmain.reloadBoostAlpha
import jp.juggler.subwaytooter.actmain.reloadColors
import jp.juggler.subwaytooter.actmain.reloadFonts
import jp.juggler.subwaytooter.actmain.reloadIconSize
import jp.juggler.subwaytooter.actmain.reloadMediaHeight
import jp.juggler.subwaytooter.actmain.reloadRoundRatio
import jp.juggler.subwaytooter.actmain.reloadTextSize
import jp.juggler.subwaytooter.actmain.reloadTimeZone
import jp.juggler.subwaytooter.actmain.resizeColumnWidth
import jp.juggler.subwaytooter.actmain.scrollColumnStrip
import jp.juggler.subwaytooter.actmain.scrollToColumn
import jp.juggler.subwaytooter.actmain.scrollToLastColumn
import jp.juggler.subwaytooter.actmain.searchFromActivityResult
import jp.juggler.subwaytooter.actmain.setColumnsOrder
import jp.juggler.subwaytooter.actmain.showFooterColor
import jp.juggler.subwaytooter.actmain.showQuickPostVisibility
import jp.juggler.subwaytooter.actmain.tabOnly
import jp.juggler.subwaytooter.actmain.updateColumnStrip
import jp.juggler.subwaytooter.actmain.updateColumnStripSelection
import jp.juggler.subwaytooter.actpost.CompletionHelper
import jp.juggler.subwaytooter.api.entity.Acct
import jp.juggler.subwaytooter.api.entity.EntityId
import jp.juggler.subwaytooter.api.entity.TootVisibility
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.ColumnLoadReason
import jp.juggler.subwaytooter.column.ColumnType
import jp.juggler.subwaytooter.column.fireColumnColor
import jp.juggler.subwaytooter.column.fireRelativeTime
import jp.juggler.subwaytooter.column.fireShowColumnHeader
import jp.juggler.subwaytooter.column.fireShowContent
import jp.juggler.subwaytooter.column.onActivityStart
import jp.juggler.subwaytooter.column.onLanguageFilterChanged
import jp.juggler.subwaytooter.column.removeColumnViewHolderByActivity
import jp.juggler.subwaytooter.column.saveScrollPosition
import jp.juggler.subwaytooter.column.startLoading
import jp.juggler.subwaytooter.column.viewHolder
import jp.juggler.subwaytooter.databinding.ActMainBinding
import jp.juggler.subwaytooter.dialog.DlgQuickTootMenu
import jp.juggler.subwaytooter.itemviewholder.StatusButtonsPopup
import jp.juggler.subwaytooter.notification.checkNotificationImmediateAll
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.span.MyClickableSpan
import jp.juggler.subwaytooter.span.MyClickableSpanHandler
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.ui.languageFilter.LanguageFilterActivity
import jp.juggler.subwaytooter.util.DecodeOptions.Companion.reloadEmojiScale
import jp.juggler.subwaytooter.util.EmojiDecoder
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.subwaytooter.util.permissionSpecNotification
import jp.juggler.subwaytooter.util.requester
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.anyArrayOf
import jp.juggler.util.data.notEmpty
import jp.juggler.util.data.notZero
import jp.juggler.util.int
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.benchmark
import jp.juggler.util.log.showToast
import jp.juggler.util.long
import jp.juggler.util.string
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.isNotOk
import jp.juggler.util.ui.setContentViewAndInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference
import java.util.LinkedList

class ActMain : AppCompatActivity(),
    View.OnClickListener,
    ViewPager.OnPageChangeListener,
    DrawerLayout.DrawerListener,
    MyClickableSpanHandler {

    companion object {
        private val log = LogCategory("ActMain")

        const val COLUMN_WIDTH_MIN_DP = 300

        const val STATE_CURRENT_PAGE = "current_page"

        const val RESULT_APP_DATA_IMPORT = Activity.RESULT_FIRST_USER

        // ActPostから参照される
        var refActMain: WeakReference<ActMain>? = null

        // 外部からインテントを受信した後、アカウント選択中に画面回転したらアカウント選択からやり直す
        internal var sharedIntent2: Intent? = null

        // アプリ設定のキャッシュ
        var boostButtonSize = 1
        var replyIconSize = 1
        var headerIconSize = 1
        var stripIconSize = 1
        var screenBottomPadding = 0
        var timelineFont: Typeface = Typeface.DEFAULT
        var timelineFontBold: Typeface = Typeface.DEFAULT_BOLD
        var eventFadeAlpha = 1f

        var timelineFontSizeSp = Float.NaN
        var timelineSpacing: Float? = null
    }

    // アプリ設定のキャッシュ
    var density = 0f
    var acctPadLr = 0
    var acctFontSizeSp = Float.NaN
    var notificationTlFontSizeSp = Float.NaN
    var headerTextSizeSp = Float.NaN
    var avatarIconSize: Int = 0
    var notificationTlIconSize: Int = 0

    // マルチウィンドウモードで子ウィンドウを閉じるのに使う
    val closeList = LinkedList<WeakReference<AppCompatActivity>>()

    // onResume() .. onPause() の間なら真
    private var isResumed = false

    // onStart() .. onStop() の間なら真
    var isStartedEx = false

    // onActivityResultで設定されてonResumeで消化される
    // 状態保存の必要なし
    var postedAcct: Acct? = null // acctAscii
    var postedStatusId: EntityId? = null
    var postedReplyId: EntityId? = null
    var postedRedraftId: EntityId? = null

    // 画面上のUI操作で生成されて
    // onPause,onPageDestroy 等のタイミングで閉じられる
    // 状態保存の必要なし
    internal var popupStatusButtons: StatusButtonsPopup? = null

    var phoneViews: ActMainPhoneViews? = null
    var tabletViews: ActMainTabletViews? = null

    var nScreenColumn: Int = 0
    var nColumnWidth: Int = 0 // dividerの幅を含む

    var nAutoCwCellWidth = 0
    var nAutoCwLines = 0

    var dlgPrivacyPolicy: WeakReference<Dialog>? = null

    var quickPostVisibility: TootVisibility = TootVisibility.AccountSetting

    val views by lazy {
        ActMainBinding.inflate(layoutInflater)
    }

    lateinit var completionHelper: CompletionHelper
    lateinit var handler: Handler
    lateinit var appState: AppState
    lateinit var sideMenuAdapter: SideMenuAdapter

    var subscriptionUpdaterCalled = false

    //////////////////////////////////////////////////////////////////
    // 読み取り専用のプロパティ

    val followCompleteCallback: () -> Unit =
        { showToast(false, R.string.follow_succeeded) }

    val unfollowCompleteCallback: () -> Unit =
        { showToast(false, R.string.unfollow_succeeded) }

    val cancelFollowRequestCompleteCallback: () -> Unit =
        { showToast(false, R.string.follow_request_cancelled) }

    val favouriteCompleteCallback: () -> Unit =
        { showToast(false, R.string.favourite_succeeded) }

    val unfavouriteCompleteCallback: () -> Unit =
        { showToast(false, R.string.unfavourite_succeeded) }

    val bookmarkCompleteCallback: () -> Unit =
        { showToast(false, R.string.bookmark_succeeded) }

    val unbookmarkCompleteCallback: () -> Unit =
        { showToast(false, R.string.unbookmark_succeeded) }

    val boostCompleteCallback: () -> Unit =
        { showToast(false, R.string.boost_succeeded) }

    val unboostCompleteCallback: () -> Unit =
        { showToast(false, R.string.unboost_succeeded) }

    val reactionCompleteCallback: () -> Unit =
        { showToast(false, R.string.reaction_succeeded) }

    // 相対時刻の表記を定期的に更新する
    private val procUpdateRelativeTime = object : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            if (!isStartedEx) return
            if (PrefB.bpRelativeTimestamp.value) {
                appState.columnList.forEach { it.fireRelativeTime() }
                handler.postDelayed(this, 10000L)
            }
        }
    }

    val dlgQuickTootMenu = DlgQuickTootMenu(this, object : DlgQuickTootMenu.Callback {
        override var visibility: TootVisibility
            get() = quickPostVisibility
            set(value) {
                if (value != quickPostVisibility) {
                    quickPostVisibility = value
                    PrefS.spQuickTootVisibility.value = value.id.toString()
                    showQuickPostVisibility()
                }
            }

        override fun onMacro(text: String) {
            with(views.etQuickToot) {
                val editable = this.text
                if (editable.isNullOrEmpty()) {
                    setText(text)
                    requestFocus()
                    setSelection(text.length)
                } else {
                    val replaceEnd = selectionStart + text.length
                    editable.replace(selectionStart, selectionEnd, text)
                    requestFocus()
                    setSelection(replaceEnd)
                }
            }
        }
    })

    val viewPool = RecyclerView.RecycledViewPool()

    val arColumnColor = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        appState.saveColumnList()
        r.data?.int(ActColumnCustomize.EXTRA_COLUMN_INDEX)
            ?.let { appState.column(it) }
            ?.let {
                it.fireColumnColor()
                it.fireShowContent(
                    reason = "ActMain column color changed",
                    reset = true
                )
            }
        updateColumnStrip()
    }

    val arLanguageFilter = ActivityResultHandler(log) { r ->
        LanguageFilterActivity.decodeResult(r)?.let { columnIndex ->
            appState.saveColumnList()
            appState.column(columnIndex)?.onLanguageFilterChanged()
        }
    }

    val arNickname = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        updateColumnStrip()
        appState.columnList.forEach { it.fireShowColumnHeader() }
    }

    val arAppSetting = ActivityResultHandler(log) { r ->
        Column.reloadDefaultColor(this)
        showFooterColor()
        updateColumnStrip()
        enableEdgeToEdgeEx(forceDark = false)
        if (r.resultCode == RESULT_APP_DATA_IMPORT) {
            r.data?.data?.let { importAppData(it) }
        }
    }

    val arAbout = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.string(ActAbout.EXTRA_SEARCH)?.notEmpty()?.let { search ->
            timeline(
                defaultInsertPosition,
                ColumnType.SEARCH,
                args = anyArrayOf(search, true)
            )
        }
    }

    val arAccountSetting = ActivityResultHandler(log) { r ->
        launchAndShowError {
            updateColumnStrip()
            appState.columnList.forEach { it.fireShowColumnHeader() }
            when (r.resultCode) {
                RESULT_OK -> r.data?.data?.let { openBrowser(it) }

                ActAccountSetting.RESULT_INPUT_ACCESS_TOKEN ->
                    r.data?.long(ActAccountSetting.EXTRA_DB_ID)
                        ?.let { daoSavedAccount.loadAccount(it) }
                        ?.let { accessTokenPrompt(it.apiHost) }
            }
        }
    }

    val arColumnList = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.getIntegerArrayListExtra(ActColumnList.EXTRA_ORDER)
            ?.takeIf { isOrderChanged(it) }
            ?.let { setColumnsOrder(it) }
        r.data?.int(ActColumnList.EXTRA_SELECTION)
            ?.takeIf { it in 0 until appState.columnCount }
            ?.let { scrollToColumn(it) }
    }

    val arActText = ActivityResultHandler(log) { r ->
        when (r.resultCode) {
            // ActText.RESULT_SEARCH_MSP -> searchFromActivityResult(r.data, ColumnType.SEARCH_MSP)
            // ActText.RESULT_SEARCH_TS -> searchFromActivityResult(r.data, ColumnType.SEARCH_TS)
            ActText.RESULT_SEARCH_NOTESTOCK -> searchFromActivityResult(
                r.data,
                ColumnType.SEARCH_NOTESTOCK
            )
        }
    }

    val arActPost = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.let { data ->
            views.etQuickToot.setText("")
            onCompleteActPost(data)
        }
    }

    val prNotification = permissionSpecNotification.requester {
        launchAndShowError {
            afterNotificationGranted()
        }
    }

    private var startAfterJob: WeakReference<Job>? = null

    //////////////////////////////////////////////////////////////////
    // ライフサイクルイベント

    override fun onCreate(savedInstanceState: Bundle?) {
        log.d("onCreate")
        installSplashScreen()
        refActMain = WeakReference(this)
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        super.onCreate(savedInstanceState)
        backPressed { onBackPressedImpl() }

        prNotification.register(this)
        arColumnColor.register(this)
        arLanguageFilter.register(this)
        arNickname.register(this)
        arAppSetting.register(this)
        arAbout.register(this)
        arAccountSetting.register(this)
        arColumnList.register(this)
        arActPost.register(this)
        arActText.register(this)

        appState = App1.getAppState(this)
        handler = appState.handler
        density = appState.density
        completionHelper = CompletionHelper(this, appState.handler)

        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)

        EmojiDecoder.useTwemoji = PrefB.bpUseTwemoji.value

        acctPadLr = (0.5f + 4f * density).toInt()
        reloadTextSize()
        reloadEmojiScale()

        initUI()

        updateColumnStrip()
        scrollToLastColumn()

        if (savedInstanceState == null) {
            checkNotificationImmediateAll(this)
        }

        if (savedInstanceState != null) {
            sharedIntent2?.let { handleSharedIntent(it) }
        }
    }

    override fun onDestroy() {
        log.d("onDestroy")
        super.onDestroy()
        refActMain = null
        completionHelper.onDestroy()

        // 子画面を全て閉じる
        closeList.forEach {
            try {
                it.get()?.finish()
            } catch (ex: Throwable) {
                log.e(ex, "close failed?")
            }
        }
        closeList.clear()

        // このアクティビティに関連する ColumnViewHolder への参照を全カラムから除去する
        appState.columnList.forEach {
            it.removeColumnViewHolderByActivity(this)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        log.w("onNewIntent: isResumed=$isResumed")
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        log.w("onConfigurationChanged")
        super.onConfigurationChanged(newConfig)
        if (newConfig.screenHeightDp > 0 || newConfig.screenHeightDp > 0) {
            tabOnly { env -> resizeColumnWidth(env) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        log.d("onSaveInstanceState")
        super.onSaveInstanceState(outState)
        phoneTab(
            { env -> outState.putInt(STATE_CURRENT_PAGE, env.pager.currentItem) },
            { env ->
                env.tabletLayoutManager.findLastVisibleItemPosition()
                    .takeIf { it != RecyclerView.NO_POSITION }
                    ?.let { outState.putInt(STATE_CURRENT_PAGE, it) }
            }
        )
        appState.columnList.forEach { it.saveScrollPosition() }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        log.d("onRestoreInstanceState")
        super.onRestoreInstanceState(savedInstanceState)
        val pos = savedInstanceState.getInt(STATE_CURRENT_PAGE)
        // 注意：開始は0じゃなく1
        if (pos in 1 until appState.columnCount) {
            phoneTab(
                { env -> env.pager.currentItem = pos },
                { env ->
                    env.tabletLayoutManager.smoothScrollToPosition(env.tabletPager, null, pos)
                }
            )
        }
    }

    override fun onStart() {
        log.d("onStart")
        isStartedEx = true
        super.onStart()
        galaxyBackgroundWorkaround()
        benchmark("onStart total") {
            reloadEmojiScale()
            benchmark("reload color") { reloadColors() }
            benchmark("reload timezone") { reloadTimeZone() }

            sideMenuAdapter.onActivityStart()

            launchDialogs()

            // 残りの処理はActivityResultの処理より後回しにしたい
            lifecycleScope.launch {
                try {
                    delay(1L)
                    benchmark("onStartAfter total") {

                        benchmark("sweepBuggieData") {
                            // バグいアカウントデータを消す
                            try {
                                daoSavedAccount.sweepBuggieData()
                            } catch (ex: Throwable) {
                                log.e(ex, "sweepBuggieData failed.")
                            }
                        }

                        val newAccounts = benchmark("loadAccountList") {
                            daoSavedAccount.loadAccountList()
                        }

                        benchmark("removeColumnByAccount") {
                            val setDbId = newAccounts.map { it.db_id }.toSet()
                            // アカウント設定から戻ってきたら、カラムを消す必要があるかもしれない
                            appState.columnList
                                .mapIndexedNotNull { index, column ->
                                    when {
                                        column.accessInfo.isNA -> index
                                        setDbId.contains(column.accessInfo.db_id) -> index
                                        else -> null
                                    }
                                }.takeIf { it.size != appState.columnCount }
                                ?.let { setColumnsOrder(it) }
                        }

                        benchmark("fireColumnColor") {
                            // 背景画像を表示しない設定が変更された時にカラムの背景を設定しなおす
                            appState.columnList.forEach { column ->
                                column.viewHolder?.lastAnnouncementShown = 0L
                                column.fireColumnColor()
                            }
                        }
                        benchmark("reloadAccountSetting") {
                            // 各カラムのアカウント設定を読み直す
                            reloadAccountSetting(newAccounts)
                        }
                        benchmark("refreshAfterPost") {
                            // 投稿直後ならカラムの再取得を行う
                            refreshAfterPost()
                        }
                        benchmark("column.onActivityStart") {
                            // 画面復帰時に再取得などを行う
                            appState.columnList.forEach { it.onActivityStart() }
                        }
                        benchmark("streamManager.onScreenStart") {
                            // 画面復帰時にストリーミング接続を開始する
                            appState.streamManager.onScreenStart()
                        }
                        benchmark("updateColumnStripSelection") {
                            // カラムの表示範囲インジケータを更新
                            updateColumnStripSelection(-1, -1f)
                        }
                        benchmark("fireShowContent") {
                            appState.columnList.forEach {
                                it.fireShowContent(reason = "ActMain onStart", reset = true)
                            }
                        }
                        benchmark("proc_updateRelativeTime") {
                            // 相対時刻表示の更新
                            procUpdateRelativeTime.run()
                        }
                        benchmark("enableSpeech") {
                            // スピーチの開始
                            appState.enableSpeech()
                        }
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "startAfter failed.")
                }
            }.let { startAfterJob = WeakReference(it) }
        }
    }

    override fun onStop() {
        log.d("onStop")
        isStartedEx = false
        startAfterJob?.get()?.cancel()
        startAfterJob = null
        handler.removeCallbacks(procUpdateRelativeTime)

        completionHelper.closeAcctPopup()

        closePopup()

        appState.streamManager.onScreenStop()

        appState.columnList.forEach { it.saveScrollPosition() }

        appState.saveColumnList(bEnableSpeech = false)

        super.onStop()
    }

    override fun onResume() {
        log.d("onResume")
        isResumed = true

        super.onResume()
        /*
           super.onResume() から呼ばれる isTopOfTask() が android.os.RemoteException 例外をたまに出すが、放置することにした。
            java.lang.RuntimeException:
            at android.app.ActivityThread.performResumeActivity (ActivityThread.java:4430)
            at android.app.ActivityThread.handleResumeActivity (ActivityThread.java:4470)
            Caused by: java.lang.IllegalArgumentException:
            at android.os.Parcel.createException (Parcel.java:1957)
            at android.os.Parcel.readException (Parcel.java:1921)
            at android.os.Parcel.readException (Parcel.java:1871)
            at android.app.IActivityManager$Stub$Proxy.isTopOfTask (IActivityManager.java:7912)
            at android.app.Activity.isTopOfTask (Activity.java:6724)
            at android.app.Activity.onResume (Activity.java:1425)
            at androidx.fragment.app.FragmentActivity.onResume (FragmentActivity.java:456)
            at jp.juggler.subwaytooter.ActMain.onResume (ActMain.kt:685)
            at android.app.Instrumentation.callActivityOnResume (Instrumentation.java:1456)
            at android.app.Activity.performResume (Activity.java:7614)
            at android.app.ActivityThread.performResumeActivity (ActivityThread.java:4412)
            Caused by: android.os.RemoteException:
            at com.android.server.am.ActivityManagerService.isTopOfTask (ActivityManagerService.java:16128)
            at android.app.IActivityManager$Stub.onTransact (IActivityManager.java:2376)
            at com.android.server.am.ActivityManagerService.onTransact (ActivityManagerService.java:3648)
            at com.android.server.am.HwActivityManagerService.onTransact (HwActivityManagerService.java:609)
            at android.os.Binder.execTransact (Binder.java:739)
         */

        if (PrefB.bpDontScreenOff.value) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // 外部から受け取ったUriの処理
        val uri = ActCallback.lastUri.getAndSet(null)
        if (uri != null) {
            handleIntentUri(uri)
        }

        // 外部から受け取ったUriの処理
        val intent = ActCallback.sharedIntent.getAndSet(null)
        if (intent != null) {
            handleSharedIntent(intent)
        }
    }

    override fun onPause() {
        log.d("onPause")
        isResumed = false

        // 最後に表示していたカラムの位置
        val lastPos = phoneTab(
            { env -> env.pager.currentItem },
            { env -> env.visibleColumnsIndices.first })
        log.d("ipLastColumnPos save $lastPos")
        PrefI.ipLastColumnPos.value = lastPos

        appState.columnList.forEach { it.saveScrollPosition() }

        appState.saveColumnList(bEnableSpeech = false)

        super.onPause()
    }

    //////////////////////////////////////////////////////////////////
    // UIイベント

    override fun onPageScrollStateChanged(state: Int) {}

    override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {
        updateColumnStripSelection(position, positionOffset)
    }

    override fun onPageSelected(position: Int) {
        handler.post {
            appState.column(position)?.let { column ->
                column.startLoading(ColumnLoadReason.PageSelect)
                scrollColumnStrip(position)
                completionHelper.setInstance(column.accessInfo.takeIf { !it.isNA })
            }
        }
    }

    override fun onClick(v: View) = onClickImpl(v)

    override fun onMyClickableSpanClicked(viewClicked: View, span: MyClickableSpan) =
        onMyClickableSpanClickedImpl(viewClicked, span)

    override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerOpened(drawerView: View) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerClosed(drawerView: View) {
        completionHelper.closeAcctPopup()
    }

    override fun onDrawerStateChanged(newState: Int) {
        completionHelper.closeAcctPopup()
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent?): Boolean {
        return when {
            super.onKeyShortcut(keyCode, event) -> true
            event?.isCtrlPressed == true && keyCode == KeyEvent.KEYCODE_N -> {
                views.btnToot.performClick()
                true
            }

            else -> false
        }
    }

    //////////////////////////////////////////////////////////////////
    // UI初期化

    // ビューのlateinit変数を初期化する
    private fun findViews() {
        views.btnToot.setOnClickListener(this)
        views.btnMenu.setOnClickListener(this)
        views.ivQuickTootAccount.setOnClickListener(this)
        views.btnQuickToot.setOnClickListener(this)
        views.btnQuickTootMenu.setOnClickListener(this)
    }

    internal fun initUI() {

        quickPostVisibility =
            TootVisibility.parseSavedVisibility(PrefS.spQuickTootVisibility.value)
                ?: quickPostVisibility

        Column.reloadDefaultColor(this)

        galaxyBackgroundWorkaround()

        reloadFonts()
        reloadIconSize()
        reloadRoundRatio()
        reloadBoostAlpha()

        findViews()

        views.drawerLayout.addDrawerListener(this)
        views.drawerLayout.setExclusionSize(stripIconSize)

        sideMenuAdapter = SideMenuAdapter(this, handler, findViewById(R.id.nav_view), views.drawerLayout)

        views.vBottomPadding.layoutParams?.height = screenBottomPadding

        justifyWindowContentPortrait()

        initUIQuickPost()
        views.svColumnStrip.isHorizontalFadingEdgeEnabled = true
        reloadMediaHeight()
        initPhoneTablet()
        showFooterColor()
    }

    private fun galaxyBackgroundWorkaround() {
        log.i(
            "galaxyBackgroundWorkaround: Build MANUFACTURER=${
                Build.MANUFACTURER
            }, BRAND=${
                Build.BRAND
            }, MODEL=${
                Build.MODEL
            }"
        )
        if (Build.MANUFACTURER?.contains("samsung", ignoreCase = true) == true) {
            val colorBarBg = PrefI.ipWindowInsetsColor.value.notZero()
                ?: attrColor(R.attr.colorWindowInsetsBg)
            // Window Insets の色を再設定する
            window.setBackgroundDrawable(ColorDrawable(colorBarBg))
            // 余計なオーバードローを一回追加する
            window.decorView.rootView.setBackgroundColor(colorBarBg)
        }
    }
}
