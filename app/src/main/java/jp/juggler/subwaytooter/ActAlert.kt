package jp.juggler.subwaytooter

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import jp.juggler.subwaytooter.databinding.ActAlertBinding
import jp.juggler.subwaytooter.view.wrapTitleTextView
import jp.juggler.util.data.encodePercent
import jp.juggler.util.data.notEmpty
import jp.juggler.util.ui.setContentViewAndInsets
import jp.juggler.util.ui.setNavigationBack

class ActAlert : AppCompatActivity() {
    companion object {
        private const val EXTRA_MESSAGE = "message"
        private const val EXTRA_TITLE = "title"

        fun Context.intentActAlert(
            tag: String,
            message: String,
            title: String,
        ) = Intent(this, ActAlert::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            data = "app://error/${tag.encodePercent()}".toUri()
            putExtra(EXTRA_MESSAGE, message)
            putExtra(EXTRA_TITLE, title)
        }
    }

    private val views by lazy {
        ActAlertBinding.inflate(layoutInflater)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val title = intent?.getStringExtra(EXTRA_TITLE).notEmpty()
        val message = intent?.getStringExtra(EXTRA_MESSAGE).notEmpty()

        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)
        setContentViewAndInsets(views.root)
        setSupportActionBar(views.toolbar)
        wrapTitleTextView(title)
        setNavigationBack(views.toolbar)

        views.tvMessage.text = message ?: ""
    }
}
