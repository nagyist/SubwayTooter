package jp.juggler.subwaytooter

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ImageView
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.jrummyapps.android.colorpicker.dialogColorPicker
import jp.juggler.subwaytooter.api.TootApiResult
import jp.juggler.subwaytooter.api.runApiTask
import jp.juggler.subwaytooter.column.Column
import jp.juggler.subwaytooter.column.getAcctColor
import jp.juggler.subwaytooter.column.getBackgroundImageDir
import jp.juggler.subwaytooter.column.getColumnName
import jp.juggler.subwaytooter.column.getContentColor
import jp.juggler.subwaytooter.column.getHeaderBackgroundColor
import jp.juggler.subwaytooter.column.getHeaderNameColor
import jp.juggler.subwaytooter.column.getIconId
import jp.juggler.subwaytooter.column.setHeaderBackground
import jp.juggler.subwaytooter.databinding.ActColumnCustomizeBinding
import jp.juggler.subwaytooter.view.wrapTitleTextView
import jp.juggler.util.backPressed
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.checkMimeTypeAndGrant
import jp.juggler.util.data.defaultLocale
import jp.juggler.util.data.intentGetContent
import jp.juggler.util.data.mayUri
import jp.juggler.util.data.notZero
import jp.juggler.util.int
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.log.withCaption
import jp.juggler.util.media.createResizedBitmap
import jp.juggler.util.ui.ActivityResultHandler
import jp.juggler.util.ui.hideKeyboard
import jp.juggler.util.ui.isNotOk
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack
import jp.juggler.util.ui.vg
import org.jetbrains.anko.textColor
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import kotlin.math.max

class ActColumnCustomize : AppCompatActivity(), View.OnClickListener {

    companion object {

        internal val log = LogCategory("ActColumnCustomize")

        internal const val EXTRA_COLUMN_INDEX = "column_index"

        internal const val PROGRESS_MAX = 65536

        fun createIntent(activity: ActMain, idx: Int) =
            Intent(activity, ActColumnCustomize::class.java).apply {
                putExtra(EXTRA_COLUMN_INDEX, idx)
            }
    }

    private var columnIndex: Int = 0
    internal lateinit var column: Column
    internal lateinit var appState: AppState
    internal var density: Float = 0f

    private val views by lazy {
        ActColumnCustomizeBinding.inflate(layoutInflater)
    }

    internal var loadingBusy: Boolean = false

    private var lastImageUri: String? = null
    private var lastImageBitmap: Bitmap? = null

    private val arColumnBackgroundImage = ActivityResultHandler(log) { r ->
        if (r.isNotOk) return@ActivityResultHandler
        r.data?.checkMimeTypeAndGrant(contentResolver)
            ?.firstOrNull()?.uri?.let { updateBackground(it) }
    }

    private fun makeResult() {
        val data = Intent()
        data.putExtra(EXTRA_COLUMN_INDEX, columnIndex)
        setResult(RESULT_OK, data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backPressed {
            makeResult()
            finish()
        }
        arColumnBackgroundImage.register(this)
        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)
        initUI()

        appState = App1.getAppState(this)
        density = appState.density
        columnIndex = intent.int(EXTRA_COLUMN_INDEX) ?: 0
        column = appState.column(columnIndex)!!
        show()
    }

    override fun onDestroy() {
        closeBitmaps()
        super.onDestroy()
    }

    override fun onClick(v: View) {
        when (v.id) {

            R.id.btnHeaderBackgroundEdit -> launchAndShowError {
                column.headerBgColor = Color.BLACK or dialogColorPicker(
                    colorInitial = column.getHeaderBackgroundColor(),
                    alphaEnabled = false,
                )
            }

            R.id.btnHeaderBackgroundReset -> {
                column.headerBgColor = 0
                show()
            }

            R.id.btnHeaderTextEdit -> launchAndShowError {
                column.headerFgColor = Color.BLACK or dialogColorPicker(
                    colorInitial = column.getHeaderNameColor(),
                    alphaEnabled = false,
                )
            }

            R.id.btnHeaderTextReset -> {
                column.headerFgColor = 0
                show()
            }

            R.id.btnColumnBackgroundColor -> launchAndShowError {
                column.columnBgColor = Color.BLACK or dialogColorPicker(
                    colorInitial = column.columnBgColor.notZero(),
                    alphaEnabled = false,
                )
            }

            R.id.btnColumnBackgroundColorReset -> {
                column.columnBgColor = 0
                show()
            }

            R.id.btnAcctColor -> launchAndShowError {
                column.acctColor = dialogColorPicker(
                    colorInitial = column.getAcctColor(),
                    alphaEnabled = true,
                ).notZero() ?: 1
            }

            R.id.btnAcctColorReset -> {
                column.acctColor = 0
                show()
            }

            R.id.btnContentColor -> launchAndShowError {
                column.contentColor = dialogColorPicker(
                    colorInitial = column.getContentColor(),
                    alphaEnabled = true,
                ).notZero() ?: 1
            }

            R.id.btnContentColorReset -> {
                column.contentColor = 0
                show()
            }

            R.id.btnColumnBackgroundImage -> {
                val intent = intentGetContent(
                    false,
                    getString(R.string.pick_image),
                    arrayOf("image/*")
                )
                arColumnBackgroundImage.launch(intent)
            }

            R.id.btnColumnBackgroundImageReset -> {
                column.columnBgImage = ""
                show()
            }
        }
    }

    private fun updateBackground(uriArg: Uri) {
        launchMain {
            var resultUri: String? = null
            runApiTask { client ->
                try {
                    val backgroundDir = getBackgroundImageDir(this@ActColumnCustomize)
                    val file =
                        File(backgroundDir, "${column.columnId}:${System.currentTimeMillis()}")
                    val fileUri = Uri.fromFile(file)

                    client.publishApiProgress("loading image from $uriArg")
                    contentResolver.openInputStream(uriArg)?.use { inStream ->
                        FileOutputStream(file).use { outStream ->
                            inStream.copyTo(outStream)
                        }
                    }

                    // リサイズや回転が必要ならする
                    client.publishApiProgress("check resize/rotation…")

                    val size = (max(
                        resources.displayMetrics.widthPixels,
                        resources.displayMetrics.heightPixels
                    ) * 1.5f).toInt()

                    val bitmap = createResizedBitmap(
                        this,
                        fileUri,
                        size,
                        skipIfNoNeedToResizeAndRotate = true,
                    )
                    if (bitmap != null) {
                        try {
                            client.publishApiProgress("save resized(${bitmap.width}x${bitmap.height}) image to $file")
                            FileOutputStream(file).use { os ->
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, os)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }

                    resultUri = fileUri.toString()
                    TootApiResult()
                } catch (ex: Throwable) {
                    log.e(ex, "can't update background image.")
                    TootApiResult(ex.withCaption("can't update background image."))
                }
            }?.let { result ->
                when (val bgUri = resultUri) {
                    null -> showToast(true, result.error ?: "?")
                    else -> {
                        column.columnBgImage = bgUri
                        show()
                    }
                }
            }
        }
    }

    private fun initUI() {
        setSupportActionBar(views.toolbar)
        wrapTitleTextView()
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.svContent)

        arrayOf(
            views.btnHeaderBackgroundEdit,
            views.btnHeaderBackgroundReset,
            views.btnHeaderTextEdit,
            views.btnHeaderTextReset,
            views.btnColumnBackgroundColor,
            views.btnColumnBackgroundColorReset,
            views.btnColumnBackgroundImage,
            views.btnColumnBackgroundImageReset,
            views.btnAcctColor,
            views.btnAcctColorReset,
            views.btnContentColor,
            views.btnContentColorReset,
        ).forEach {
            it.setOnClickListener(this)
        }

        views.sbColumnBackgroundAlpha.max = PROGRESS_MAX

        views.sbColumnBackgroundAlpha.setOnSeekBarChangeListener(object :
            SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}

            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                if (loadingBusy) return
                if (!fromUser) return
                column.columnBgImageAlpha = progress / PROGRESS_MAX.toFloat()
                showAlpha(updateText = true, updateSeek = false)
            }
        })

        views.etAlpha.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable) {
                if (loadingBusy) return
                try {
                    var f = NumberFormat.getInstance(defaultLocale(this@ActColumnCustomize))
                        .parse(views.etAlpha.text.toString())?.toFloat()
                    if (f != null && !f.isNaN()) {
                        if (f < 0f) f = 0f
                        if (f > 1f) f = 1f
                        column.columnBgImageAlpha = f
                        showAlpha(updateText = false, updateSeek = true)
                    }
                } catch (ex: Throwable) {
                    log.e(ex, "alpha parse failed.")
                }
            }
        })

        views.etAlpha.setOnEditorActionListener { _, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE -> {
                    views.etAlpha.hideKeyboard()
                    true
                }

                else -> false
            }
        }
    }

    private fun show() {
        try {
            loadingBusy = true

            column.setHeaderBackground(views.llColumnHeader)

            val c = column.getHeaderNameColor()
            views.tvColumnName.textColor = c
            views.ivColumnHeader.setImageResource(column.getIconId())
            views.ivColumnHeader.imageTintList = ColorStateList.valueOf(c)

            views.tvColumnName.text = column.getColumnName(false)

            when (column.columnBgColor) {
                0 -> views.flColumnBackground.background = null
                else -> views.flColumnBackground.setBackgroundColor(column.columnBgColor)
            }

            showAlpha(updateText = true, updateSeek = true)

            loadImage(views.ivColumnBackground, column.columnBgImage)

            views.tvSampleAcct.setTextColor(column.getAcctColor())
            views.tvSampleContent.setTextColor(column.getContentColor())
        } finally {
            loadingBusy = false
        }
    }

    private fun showAlpha(updateText: Boolean, updateSeek: Boolean) {
        var alpha = column.columnBgImageAlpha
        if (alpha.isNaN()) {
            alpha = 1f
            column.columnBgImageAlpha = alpha
        }
        views.ivColumnBackground.alpha = alpha
        val hasAlphaWarning = alpha < 0.3 && column.columnBgImage.isNotEmpty()
        views.tvBackgroundError.vg(hasAlphaWarning)?.text =
            getString(R.string.image_alpha_too_low)
        if (updateText) {
            views.etAlpha.setText("%.4f".format(column.columnBgImageAlpha))
        }
        if (updateSeek) {
            views.sbColumnBackgroundAlpha.progress = (0.5f + alpha * PROGRESS_MAX).toInt()
        }
    }

    private fun closeBitmaps() {
        try {
            views.ivColumnBackground.setImageDrawable(null)
            lastImageUri = null

            lastImageBitmap?.recycle()
            lastImageBitmap = null
        } catch (ex: Throwable) {
            log.e(ex, "closeBitmaps failed.")
        }
    }

    private fun loadImage(ivColumnBackground: ImageView, url: String) {
        try {
            if (url.isEmpty()) {
                closeBitmaps()
                return
            } else if (url == lastImageUri) {
                // 今表示してるのと同じ
                return
            }

            // 直前のBitmapを掃除する
            closeBitmaps()

            val uri = url.mayUri() ?: return

            // 画像をロードして、成功したら表示してURLを覚える
            val resizeMax = (0.5f + 64f * density).toInt()
            lastImageBitmap = createResizedBitmap(this, uri, resizeMax)
            if (lastImageBitmap != null) {
                ivColumnBackground.setImageBitmap(lastImageBitmap)
                lastImageUri = url
            }
        } catch (ex: Throwable) {
            log.e(ex, "loadImage failed.")
        }
    }
}
