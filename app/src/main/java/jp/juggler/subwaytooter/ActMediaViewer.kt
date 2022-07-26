package jp.juggler.subwaytooter

import android.Manifest
import android.annotation.SuppressLint
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.*
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.view.View
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.Player.TimelineChangeReason
import com.google.android.exoplayer2.source.LoadEventInfo
import com.google.android.exoplayer2.source.MediaLoadData
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MediaSourceEventListener
import com.google.android.exoplayer2.util.RepeatModeUtil.REPEAT_TOGGLE_MODE_ONE
import jp.juggler.subwaytooter.api.ApiTask
import jp.juggler.subwaytooter.api.TootApiClient
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.entity.*
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.databinding.ActMediaViewerBinding
import jp.juggler.subwaytooter.dialog.ActionsDialog
import jp.juggler.subwaytooter.drawable.MediaBackgroundDrawable
import jp.juggler.subwaytooter.global.appPref
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.put
import jp.juggler.subwaytooter.util.ProgressResponseBody
import jp.juggler.subwaytooter.view.PinchBitmapView
import jp.juggler.util.*
import kotlinx.coroutines.yield
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*
import javax.net.ssl.HttpsURLConnection
import kotlin.math.max

class ActMediaViewer : AppCompatActivity(), View.OnClickListener {

    companion object {

        internal val log = LogCategory("ActMediaViewer")

        internal val download_history_list = LinkedList<DownloadHistory>()
        internal const val DOWNLOAD_REPEAT_EXPIRE = 3000L
        internal const val short_limit = 5000L

        private const val PERMISSION_REQUEST_CODE = 1

        internal const val EXTRA_IDX = "idx"
        internal const val EXTRA_DATA = "data"
        internal const val EXTRA_SERVICE_TYPE = "serviceType"
        internal const val EXTRA_SHOW_DESCRIPTION = "showDescription"

        internal const val STATE_PLAYER_POS = "playerPos"
        internal const val STATE_PLAYER_PLAY_WHEN_READY = "playerPlayWhenReady"
        internal const val STATE_LAST_VOLUME = "lastVolume"

        internal fun <T : TootAttachmentLike> encodeMediaList(list: ArrayList<T>?) =
            list?.encodeJson()?.toString() ?: "[]"

        internal fun decodeMediaList(src: String?) =
            ArrayList<TootAttachment>().apply {
                src?.decodeJsonArray()?.forEach {
                    if (it !is JsonObject) return@forEach
                    add(TootAttachment.decodeJson(it))
                }
            }

        fun open(
            activity: ActMain,
            showDescription: Boolean,
            serviceType: ServiceType,
            list: ArrayList<TootAttachmentLike>,
            idx: Int,
        ) {
            val intent = Intent(activity, ActMediaViewer::class.java)
            intent.putExtra(EXTRA_IDX, idx)
            intent.putExtra(EXTRA_SERVICE_TYPE, serviceType.ordinal)
            intent.putExtra(EXTRA_DATA, encodeMediaList(list))
            intent.putExtra(EXTRA_SHOW_DESCRIPTION, showDescription)
            activity.startActivity(intent)
            activity.overridePendingTransition(R.anim.slide_from_bottom, android.R.anim.fade_out)
        }
    }

    class DownloadHistory(val time: Long, val url: String)

    internal var idx: Int = 0
    private lateinit var mediaList: ArrayList<TootAttachment>
    private lateinit var serviceType: ServiceType
    private var showDescription = true

    private val views by lazy {
        ActMediaViewerBinding.inflate(layoutInflater)
    }

    private lateinit var exoPlayer: ExoPlayer

    private var lastVolume = Float.NaN

    internal var bufferingLastShown: Long = 0

    private var lastVideoUrl: String? = null

    private val tileStep by lazy {
        val density = resources.displayMetrics.density
        (density * 12f + 0.5f).toInt()
    }

    private val playerListener = object : Player.Listener {

        override fun onTimelineChanged(
            timeline: Timeline,
            @TimelineChangeReason reason: Int,
        ) {
            log.d("exoPlayer onTimelineChanged reason=$reason")
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        }

        private fun showBufferingToast() {
            val playWhenReady = exoPlayer.playWhenReady
            val playbackState = exoPlayer.playbackState
            if (playWhenReady && playbackState == Player.STATE_BUFFERING) {
                val now = SystemClock.elapsedRealtime()
                if (now - bufferingLastShown >= short_limit && exoPlayer.duration >= short_limit) {
                    bufferingLastShown = now
                    showToast(false, R.string.video_buffering)
                }
                /*
                    exoPlayer.getDuration() may returns negative value (TIME_UNSET ,same as Long.MIN_VALUE + 1).
                */
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            showBufferingToast()
        }

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            log.d("onPlayWhenReadyChanged playWhenReady=$playWhenReady, reason=$reason")
            showBufferingToast()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            log.d("exoPlayer onRepeatModeChanged $repeatMode")
        }

        override fun onPlayerError(error: PlaybackException) {
            log.w(error, "exoPlayer onPlayerError")
            if (recoverLocalVideo()) return
            showToast(error, "exoPlayer onPlayerError")
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            log.d("exoPlayer onPositionDiscontinuity reason=$reason, oldPosition=$oldPosition, newPosition=$newPosition")
        }
    }

    private val mediaSourceEventListener = object : MediaSourceEventListener {
        override fun onLoadStarted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log.d("onLoadStarted")
        }

        override fun onDownstreamFormatChanged(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            mediaLoadData: MediaLoadData,
        ) {
            log.d("onDownstreamFormatChanged")
        }

        override fun onUpstreamDiscarded(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId,
            mediaLoadData: MediaLoadData,
        ) {
            log.d("onUpstreamDiscarded")
        }

        override fun onLoadCompleted(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log.d("onLoadCompleted")
        }

        override fun onLoadCanceled(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
        ) {
            log.d("onLoadCanceled")
        }

        override fun onLoadError(
            windowIndex: Int,
            mediaPeriodId: MediaSource.MediaPeriodId?,
            loadEventInfo: LoadEventInfo,
            mediaLoadData: MediaLoadData,
            error: IOException,
            wasCanceled: Boolean,
        ) {
            showError(error.withCaption("load error."))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        log.d("onSaveInstanceState")

        outState.putInt(EXTRA_IDX, idx)
        outState.putInt(EXTRA_SERVICE_TYPE, serviceType.ordinal)
        outState.putString(EXTRA_DATA, encodeMediaList(mediaList))

        outState.putLong(STATE_PLAYER_POS, exoPlayer.currentPosition)
        outState.putBoolean(STATE_PLAYER_PLAY_WHEN_READY, exoPlayer.playWhenReady)
        outState.putFloat(STATE_LAST_VOLUME, lastVolume)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        App1.setActivityTheme(this, noActionBar = true, forceDark = true)

        this.showDescription = intent.getBooleanExtra(EXTRA_SHOW_DESCRIPTION, showDescription)

        this.serviceType = ServiceType.values()[
                savedInstanceState?.getInt(EXTRA_SERVICE_TYPE)
                    ?: intent.getIntExtra(EXTRA_SERVICE_TYPE, 0)
        ]

        this.mediaList = decodeMediaList(
            savedInstanceState?.getString(EXTRA_DATA)
                ?: intent.getStringExtra(EXTRA_DATA)
        )

        this.idx = (savedInstanceState?.getInt(EXTRA_IDX)
            ?: intent.getIntExtra(EXTRA_IDX, 0))
            .takeIf { it in mediaList.indices }
            ?: 0

        initUI()

        load(savedInstanceState)
    }

    override fun onDestroy() {
        super.onDestroy()
        views.pbvImage.setBitmap(null)
        exoPlayer.release()
    }

    override fun onStart() {
        super.onStart()
        views.exoView.onResume()
    }

    override fun onStop() {
        super.onStop()
        views.exoView.onPause()
    }

    override fun finish() {
        super.finish()
        overridePendingTransition(R.anim.fade_in, R.anim.slide_to_bottom)
    }

    internal fun initUI() {
        setContentView(views.root)
        App1.initEdgeToEdge(this)

        views.pbvImage.background = MediaBackgroundDrawable(
            tileStep = tileStep,
            kind = MediaBackgroundDrawable.Kind.fromIndex(PrefI.ipMediaBackground(this))
        )

        val enablePaging = mediaList.size > 1
        views.btnPrevious.isEnabledAlpha = enablePaging
        views.btnNext.isEnabledAlpha = enablePaging

        views.btnPrevious.setOnClickListener(this)
        views.btnNext.setOnClickListener(this)
        findViewById<View>(R.id.btnDownload).setOnClickListener(this)
        findViewById<View>(R.id.btnMore).setOnClickListener(this)

        views.cbMute.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                // mute
                lastVolume = exoPlayer.volume
                exoPlayer.volume = 0f
            } else {
                // unmute
                exoPlayer.volume = when {
                    lastVolume.isNaN() -> 1f
                    lastVolume <= 0f -> 1f
                    else -> lastVolume
                }
                lastVolume = Float.NaN
            }
        }

        views.pbvImage.setCallback(object : PinchBitmapView.Callback {
            override fun onSwipe(deltaX: Int, deltaY: Int) {
                if (isDestroyed) return
                if (deltaX != 0) {
                    loadDelta(deltaX)
                } else {
                    log.d("finish by vertical swipe")
                    finish()
                }
            }

            override fun onMove(
                bitmapW: Float,
                bitmapH: Float,
                tx: Float,
                ty: Float,
                scale: Float,
            ) {
                App1.getAppState(this@ActMediaViewer).handler.post(Runnable {
                    if (isDestroyed) return@Runnable
                    if (views.tvStatus.visibility == View.VISIBLE) {
                        views.tvStatus.text = getString(
                            R.string.zooming_of,
                            bitmapW.toInt(),
                            bitmapH.toInt(),
                            scale
                        )
                    }
                })
            }
        })

        exoPlayer = ExoPlayer.Builder(this).build()
        exoPlayer.addListener(playerListener)
        views.exoView.run {
            player = exoPlayer
            controllerAutoShow = false
            setShowRewindButton(false)
            setShowFastForwardButton(false)
            setShowPreviousButton(false)
            setShowNextButton(false)
            setRepeatToggleModes(REPEAT_TOGGLE_MODE_ONE)
        }
    }

    internal fun loadDelta(delta: Int) {
        if (mediaList.size < 2) return
        val size = mediaList.size
        idx = (idx + size + delta) % size
        load()
    }

    internal fun load(state: Bundle? = null) {

        exoPlayer.stop()

        // いったんすべて隠す
        views.run {
            pbvImage.gone()
            exoView.gone()
            tvError.gone()
            svDescription.gone()
            tvStatus.gone()
        }

        if (idx < 0 || idx >= mediaList.size) {
            showError(getString(R.string.media_attachment_empty))
            return
        }
        val ta = mediaList[idx]
        val description = ta.description
        if (showDescription && description?.isNotEmpty() == true) {
            views.svDescription.visible()
            views.tvDescription.text = description
        }

        when (ta.type) {
            TootAttachmentType.Unknown,
            -> showError(getString(R.string.media_attachment_type_error, ta.type.id))

            TootAttachmentType.Image,
            -> loadBitmap(ta)

            TootAttachmentType.Video,
            TootAttachmentType.GIFV,
            TootAttachmentType.Audio,
            -> loadVideo(ta, state)
        }
    }

    private fun showError(message: String) {
        views.run {
            exoView.gone()
            pbvImage.gone()
            tvError.visible().text = message
        }
    }

    private fun loadVideo(
        ta: TootAttachment,
        state: Bundle? = null,
        forceLocalUrl: Boolean = false,
    ) {

        views.cbMute.visible().run {
            if (isChecked && lastVolume.isFinite()) {
                exoPlayer.volume = 0f
            }
        }

        val url = when {
            forceLocalUrl -> ta.url
            else -> ta.getLargeUrl(appPref)
        }
        if (url == null) {
            showError("missing media attachment url.")
            return
        }
        val uri = url.mayUri()
        if (uri == null) {
            showError("can't parse URI: $url")
            return
        }
        lastVideoUrl = url

        // https://github.com/google/ExoPlayer/issues/1819
        HttpsURLConnection.setDefaultSSLSocketFactory(MySslSocketFactory)
        views.exoView.visibility = View.VISIBLE
        exoPlayer.setMediaItem(MediaItem.fromUri(uri))
        exoPlayer.prepare()
        exoPlayer.repeatMode = when (ta.type) {
            TootAttachmentType.Video -> Player.REPEAT_MODE_OFF
            // GIFV or AUDIO
            else -> Player.REPEAT_MODE_ALL
        }
        if (state == null) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.playWhenReady = state.getBoolean(STATE_PLAYER_PLAY_WHEN_READY, true)
            exoPlayer.seekTo(max(0L, state.getLong(STATE_PLAYER_POS, 0L)))
            lastVolume = state.getFloat(STATE_LAST_VOLUME, 1f)
        }
    }

    private fun decodeBitmap(
        options: BitmapFactory.Options,
        data: ByteArray,
        @Suppress("SameParameterValue") pixelMax: Int,
    ): Pair<Bitmap?, String?> {

        val orientation: Int? = ByteArrayInputStream(data).imageOrientation()

        // detects image size
        options.inJustDecodeBounds = true
        options.inScaled = false
        options.outWidth = 0
        options.outHeight = 0
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        var w = options.outWidth
        var h = options.outHeight
        if (w <= 0 || h <= 0) {
            return Pair(null, "can't decode image bounds.")
        }

        // calc bits to reduce size
        var bits = 0
        while (w > pixelMax || h > pixelMax) {
            ++bits
            w = w shr 1
            h = h shr 1
        }
        options.inJustDecodeBounds = false
        options.inSampleSize = 1 shl bits

        // decode image
        val bitmap1 = BitmapFactory.decodeByteArray(data, 0, data.size, options)
            ?: return Pair(null, "BitmapFactory.decodeByteArray returns null.")

        val srcWidth = bitmap1.width.toFloat()
        val srcHeight = bitmap1.height.toFloat()
        if (srcWidth <= 0f || srcHeight <= 0f) {
            bitmap1.recycle()
            return Pair(null, "image size <= 0")
        }

        val dstSize = rotateSize(orientation, srcWidth, srcHeight)
        val dstSizeInt = Point(
            max(1, (dstSize.x + 0.5f).toInt()),
            max(1, (dstSize.y + 0.5f).toInt())
        )

        // 回転行列を作る
        val matrix = Matrix()
        matrix.reset()

        // 画像の中心が原点に来るようにして
        matrix.postTranslate(srcWidth * -0.5f, srcHeight * -0.5f)

        // orientationに合わせた回転指定
        matrix.resolveOrientation(orientation)

        // 表示領域に埋まるように平行移動
        matrix.postTranslate(dstSize.x * 0.5f, dstSize.y * 0.5f)

        // 回転後の画像
        val bitmap2 = try {
            Bitmap.createBitmap(dstSizeInt.x, dstSizeInt.y, Bitmap.Config.ARGB_8888)
                ?: return Pair(bitmap1, "createBitmap returns null")
        } catch (ex: Throwable) {
            log.trace(ex)
            return Pair(bitmap1, ex.withCaption("createBitmap failed."))
        }

        try {
            Canvas(bitmap2).drawBitmap(
                bitmap1,
                matrix,
                Paint().apply { isFilterBitmap = true }
            )
        } catch (ex: Throwable) {
            log.trace(ex)
            bitmap2.recycle()
            return Pair(bitmap1, ex.withCaption("drawBitmap failed."))
        }

        try {
            bitmap1.recycle()
        } catch (ignored: Throwable) {
        }
        return Pair(bitmap2, null)
    }

    private suspend fun getHttpCached(
        client: TootApiClient,
        url: String,
    ): Pair<TootApiResult?, ByteArray?> {
        val result = TootApiResult.makeWithCaption(url)

        val request = try {
            Request.Builder()
                .url(url)
                .cacheControl(App1.CACHE_CONTROL)
                .addHeader("Accept", "image/webp,image/*,*/*;q=0.8")
                .build()
        } catch (ex: Throwable) {
            result.setError(ex.withCaption("incorrect URL."))
            return Pair(result, null)
        }

        if (!client.sendRequest(result, tmpOkhttpClient = App1.ok_http_client_media_viewer) {
                request
            }) return Pair(result, null)

        if (client.isApiCancelled()) return Pair(null, null)

        val response = result.response!!
        if (!response.isSuccessful) {
            result.parseErrorResponse()
            return Pair(result, null)
        }

        try {
            val ba = ProgressResponseBody.bytes(response) { bytesRead, bytesTotal ->
                // 50MB以上のデータはキャンセルする
                if (max(bytesRead, bytesTotal) >= 50000000) {
                    error("media attachment is larger than 50000000")
                }
                client.publishApiProgressRatio(bytesRead.toInt(), bytesTotal.toInt())
            }
            if (client.isApiCancelled()) return Pair(null, null)
            return Pair(result, ba)
        } catch (ignored: Throwable) {
            result.parseErrorResponse("?")
            return Pair(result, null)
        }
    }

    @SuppressLint("StaticFieldLeak")
    private fun loadBitmap(ta: TootAttachment) {

        views.run {
            cbMute.gone()
            tvStatus.visible().text = null
            pbvImage.visible().setBitmap(null)
        }

        val urlList = ta.getLargeUrlList(appPref)
        if (urlList.isEmpty()) {
            showError("missing media attachment url.")
            return
        }

        launchMain {
            val options = BitmapFactory.Options()

            var resultBitmap: Bitmap? = null

            runApiTask(progressStyle = ApiTask.PROGRESS_HORIZONTAL) { client ->
                if (urlList.isEmpty()) return@runApiTask TootApiResult("missing url")
                var lastResult: TootApiResult? = null
                for (url in urlList) {
                    val (result, ba) = getHttpCached(client, url)
                    lastResult = result
                    if (ba != null) {
                        client.publishApiProgress("decoding image…")

                        val (bitmap, error) = decodeBitmap(options, ba, 2048)
                        if (bitmap != null) {
                            resultBitmap = bitmap
                            break
                        }
                        if (error != null) lastResult = TootApiResult(error)
                    }
                }
                lastResult
            }.let { result -> // may null
                when (val bitmap = resultBitmap) {
                    null -> if (result != null) showToast(true, result.error)
                    else -> views.pbvImage.setBitmap(bitmap)
                }
            }
        }
    }

    override fun onClick(v: View) {
        try {
            when (v) {
                views.btnPrevious -> loadDelta(-1)
                views.btnNext -> loadDelta(+1)
                views.btnDownload -> download(mediaList[idx])
                views.btnMore -> more(mediaList[idx])
            }
        } catch (ex: Throwable) {
            showToast(ex, "action failed.")
        }
    }

    private fun download(ta: TootAttachmentLike) {
        if (!checkPermission()) return

        val downLoadManager: DownloadManager = systemService(this)
            ?: error("missing DownloadManager system service")

        val url = if (ta is TootAttachment) {
            ta.getLargeUrl(appPref)
        } else {
            null
        } ?: return

        // ボタン連打対策
        run {
            val now = SystemClock.elapsedRealtime()

            // 期限切れの履歴を削除
            val it = download_history_list.iterator()
            while (it.hasNext()) {
                val dh = it.next()
                if (now - dh.time >= DOWNLOAD_REPEAT_EXPIRE) {
                    // この履歴は十分に古いので捨てる
                    it.remove()
                } else if (url == dh.url) {
                    // 履歴に同じURLがあればエラーとする
                    showToast(false, R.string.dont_repeat_download_to_same_url)
                    return
                }
            }
            // 履歴の末尾に追加(履歴は古い順に並ぶ)
            download_history_list.addLast(DownloadHistory(now, url))
        }

        var fileName: String? = null

        try {
            val pathSegments = url.toUri().pathSegments
            if (pathSegments != null) {
                val size = pathSegments.size
                for (i in size - 1 downTo 0) {
                    val s = pathSegments[i]
                    if (s?.isNotEmpty() == true) {
                        fileName = s
                        break
                    }
                }
            }
        } catch (ex: Throwable) {
            log.trace(ex)
        }

        if (fileName == null) {
            fileName = url
                .replaceFirst("https?://".asciiPattern(), "")
                .replaceAll("[^.\\w\\d]+".asciiPattern(), "-")
        }
        if (fileName.length >= 20) fileName = fileName.substring(fileName.length - 20)

        val request = DownloadManager.Request(url.toUri())
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
        request.setTitle(fileName)
        request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_MOBILE or DownloadManager.Request.NETWORK_WIFI)

        // Android 10 以降では allowScanningByMediaScanner は無視される
        if (Build.VERSION.SDK_INT < 29) {
            //メディアスキャンを許可する
            @Suppress("DEPRECATION")
            request.allowScanningByMediaScanner()
        }

        //ダウンロード中・ダウンロード完了時にも通知を表示する
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        downLoadManager.enqueue(request)
        showToast(false, R.string.downloading)
    }

    private fun share(action: String, url: String) {

        try {
            val intent = Intent(action)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (action == Intent.ACTION_SEND) {
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, url)
            } else {
                intent.data = url.toUri()
            }

            startActivity(intent)
        } catch (ex: Throwable) {
            showToast(ex, "can't open app.")
        }
    }

    private fun copy(url: String) {
        val cm = getSystemService(CLIPBOARD_SERVICE) as? ClipboardManager
            ?: throw NotImplementedError("missing ClipboardManager system service")

        try {
            //クリップボードに格納するItemを作成
            val item = ClipData.Item(url)

            val mimeType = arrayOfNulls<String>(1)
            mimeType[0] = ClipDescription.MIMETYPE_TEXT_PLAIN

            //クリップボードに格納するClipDataオブジェクトの作成
            val cd = ClipData(ClipDescription("media URL", mimeType), item)

            //クリップボードにデータを格納
            cm.setPrimaryClip(cd)

            showToast(false, R.string.url_is_copied)
        } catch (ex: Throwable) {
            showToast(ex, "clipboard access failed.")
        }
    }

    private fun more(ta: TootAttachmentLike) {
        val ad = ActionsDialog()
        if (ta is TootAttachment) {
            val url = ta.getLargeUrl(appPref) ?: return
            ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
            ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
            ad.addAction(getString(R.string.copy_url)) { copy(url) }
            addMoreMenu(ad, "url", ta.url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "remote_url", ta.remote_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "preview_url", ta.preview_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "preview_remote_url", ta.preview_remote_url, Intent.ACTION_VIEW)
            addMoreMenu(ad, "text_url", ta.text_url, Intent.ACTION_VIEW)
        } else if (ta is TootAttachmentMSP) {
            val url = ta.preview_url
            ad.addAction(getString(R.string.open_in_browser)) { share(Intent.ACTION_VIEW, url) }
            ad.addAction(getString(R.string.share_url)) { share(Intent.ACTION_SEND, url) }
            ad.addAction(getString(R.string.copy_url)) { copy(url) }
        }

        if (TootAttachmentType.Image == mediaList.elementAtOrNull(idx)?.type) {
            ad.addAction(getString(R.string.background_pattern)) { mediaBackgroundDialog() }
        }

        ad.show(this, null)
    }

    private fun addMoreMenu(
        ad: ActionsDialog,
        captionPrefix: String,
        url: String?,
        @Suppress("SameParameterValue") action: String,
    ) {
        val uri = url.mayUri() ?: return
        val caption = getString(R.string.open_browser_of, captionPrefix)
        ad.addAction(caption) {
            try {
                val intent = Intent(action, uri)
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
            } catch (ex: Throwable) {
                showToast(ex, "can't open app.")
            }
        }
    }

    private fun checkPermission(): Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) return true

        if (Build.VERSION.SDK_INT >= 23) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                PERMISSION_REQUEST_CODE
            )
        } else {
            showToast(true, R.string.missing_permission_to_access_media)
        }
        return false
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray,
    ) {
        if (requestCode == PERMISSION_REQUEST_CODE) {
            when (permissions.indices.all { grantResults[it] == PackageManager.PERMISSION_GRANTED }) {
                false -> showToast(true, R.string.missing_permission_to_access_media)
                else -> download(mediaList[idx])
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    private fun mediaBackgroundDialog() {
        val ad = ActionsDialog()
        for (k in MediaBackgroundDrawable.Kind.values()) {
            ad.addAction(k.name) {
                val idx = k.toIndex()
                appPref.edit().put(PrefI.ipMediaBackground, idx).apply()

                views.pbvImage.background = MediaBackgroundDrawable(
                    tileStep = tileStep,
                    kind = k
                )
            }
        }
        ad.show(this, getString(R.string.background_pattern))
    }

    /**
     * remote_urlを再生できなかった場合、自サーバで再生し直す
     */
    private fun recoverLocalVideo(): Boolean {
        val ta = mediaList.elementAtOrNull(idx)
        if (ta != null &&
            lastVideoUrl == ta.remote_url &&
            !ta.url.isNullOrEmpty() &&
            ta.url != ta.remote_url
        ) {
            launchMain {
                yield()
                loadVideo(ta, forceLocalUrl = true)
            }
            return true
        }
        return false
    }
}
