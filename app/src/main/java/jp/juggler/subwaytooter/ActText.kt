package jp.juggler.subwaytooter

import android.app.SearchManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import jp.juggler.subwaytooter.api.entity.TootAccount
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.databinding.ActTextBinding
import jp.juggler.subwaytooter.dialog.pickAccount
import jp.juggler.subwaytooter.table.SavedAccount
import jp.juggler.subwaytooter.table.daoMutedWord
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.util.CustomShare
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.TootTextEncoder
import jp.juggler.subwaytooter.util.copyToClipboard
import jp.juggler.util.*
import jp.juggler.util.coroutine.AppDispatchers
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.coroutine.launchMain
import jp.juggler.util.data.*
import jp.juggler.util.log.LogCategory
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActText : AppCompatActivity() {

    companion object {

        internal val log = LogCategory("ActText")

        // internal const val RESULT_SEARCH_MSP = RESULT_FIRST_USER + 1
        // internal const val RESULT_SEARCH_TS = RESULT_FIRST_USER + 2
        internal const val RESULT_SEARCH_NOTESTOCK = RESULT_FIRST_USER + 3

        internal const val EXTRA_TEXT = "text"
        internal const val EXTRA_CONTENT_START = "content_start"
        internal const val EXTRA_CONTENT_END = "content_end"
        internal const val EXTRA_ACCOUNT_DB_ID = "account_db_id"

        fun createIntent(
            activity: ActMain,
            accessInfo: SavedAccount,
            status: TootStatus,
        ) = Intent(activity, ActText::class.java).apply {
            putExtra(EXTRA_ACCOUNT_DB_ID, accessInfo.db_id)
            TootTextEncoder.encodeStatus(this, activity, accessInfo, status)
        }

        fun createIntent(
            activity: ActMain,
            accessInfo: SavedAccount,
            who: TootAccount,
        ) = Intent(activity, ActText::class.java).apply {
            putExtra(EXTRA_ACCOUNT_DB_ID, accessInfo.db_id)
            TootTextEncoder.encodeAccount(this, activity, accessInfo, who)
        }
    }

    private class SearchResultSpan(color: Int) : BackgroundColorSpan(color)

    private val views by lazy {
        ActTextBinding.inflate(layoutInflater)
    }

    private val searchTextChannel = Channel<Long>(capacity = Channel.CONFLATED)

    private var account: SavedAccount? = null

    private var searchResult: List<IntRange> = emptyList()

    private var searchError: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        App1.setActivityTheme(this)

        setContentView(views.root)
        setSupportActionBar(views.toolbar)
        setNavigationBack(views.toolbar)
        fixHorizontalMargin(views.etText)
        views.etSearch.addTextChangedListener { postSearchText() }
        views.btnSearchClear.setOnClickListener { views.etSearch.setText("") }
        views.btnSearchPrev.setOnClickListener { searchPrev() }
        views.btnSearchNext.setOnClickListener { searchNext() }
        views.btnToggleRegex.setOnCheckedChangeListener { _, _ -> postSearchText() }

        lifecycleScope.launch {
            while (true) {
                try {
                    searchTextChannel.receive()
                    searchTextImpl()
                } catch (ex: Throwable) {
                    if (ex is CancellationException) break
                    log.e(ex, "searchTextChannel failed.")
                }
            }
        }

        launchAndShowError {
            account = intent.long(EXTRA_ACCOUNT_DB_ID)
                ?.let { daoSavedAccount.loadAccount(it) }

            if (savedInstanceState == null) {
                searchHighlight(null)

                val sv = intent.string(EXTRA_TEXT) ?: ""
                val contentStart = intent.int(EXTRA_CONTENT_START) ?: 0
                val contentEnd = intent.int(EXTRA_CONTENT_END) ?: sv.length
                views.etText.setText(sv)

                // Android 9 以降ではフォーカスがないとsetSelectionできない
                if (Build.VERSION.SDK_INT >= 28) {
                    views.etText.requestFocus()
                    views.etText.hideKeyboard()
                }

                views.etText.setSelection(contentStart, contentEnd)
            }
        }
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        postSearchText()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.act_text, menu)
        super.onCreateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.miCopy -> selectionOrAll.copyToClipboard(this)
            R.id.miSearch -> search()
            R.id.miSend -> send()
            R.id.miMuteWord -> muteWord()
            R.id.miSearchNotestock -> searchToot(RESULT_SEARCH_NOTESTOCK)
            R.id.miKeywordFilter -> keywordFilter()
            R.id.miHighlight -> highlight()
            // MSP検索ボタン -> searchToot(RESULT_SEARCH_MSP)
            // R.id.btnSearchTS -> searchToot(RESULT_SEARCH_TS)

            R.id.miTranslate -> CustomShare.invokeText(
                CustomShareTarget.Translate,
                this,
                selectionOrAll,
            )

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /**
     * 選択範囲、またはテキスト全体
     */
    private val selectionOrAll: String
        get() {
            val et = views.etText
            val s = et.selectionStart
            val e = et.selectionEnd
            val text = et.text.toString()
            return when (s) {
                e -> text
                else -> text.substring(s, e)
            }
        }

    private fun send() {
        selectionOrAll.trim().notEmpty()?.let {
            try {

                val intent = Intent()
                intent.action = Intent.ACTION_SEND
                intent.type = "text/plain"
                intent.putExtra(Intent.EXTRA_TEXT, it)
                startActivity(intent)
            } catch (ex: Throwable) {
                log.e(ex, "send failed.")
                showToast(ex, "send failed.")
            }
        }
    }

    private fun search() {
        selectionOrAll.trim().notEmpty()?.also {
            try {
                val intent = Intent(Intent.ACTION_WEB_SEARCH)
                intent.putExtra(SearchManager.QUERY, it)
                if (intent.resolveActivity(packageManager) != null) {
                    startActivity(intent)
                }
            } catch (ex: Throwable) {
                log.e(ex, "search failed.")
                showToast(ex, "search failed.")
            }
        }
    }

    private fun searchToot(@Suppress("SameParameterValue") resultCode: Int) {
        selectionOrAll.trim().notEmpty()?.let {
            try {
                val data = Intent()
                data.putExtra(Intent.EXTRA_TEXT, it)
                setResult(resultCode, data)
                finish()
            } catch (ex: Throwable) {
                log.e(ex, "searchToot failed.")
                showToast(ex, "searchToot failed.")
            }
        }
    }

    private fun muteWord() {
        launchAndShowError {
            selectionOrAll.trim().notEmpty()?.let {
                daoMutedWord.save(it)
                App1.getAppState(this@ActText).onMuteUpdated()
                showToast(false, R.string.word_was_muted)
            }
        }
    }

    private fun keywordFilter() {
        selectionOrAll.trim().notEmpty()?.let { text ->
            val account = this.account
            if (account?.isPseudo == false && account.isMastodon) {
                ActKeywordFilter.open(this, account, initialPhrase = text)
            } else {
                launchMain {
                    pickAccount(
                        bAllowPseudo = false,
                        bAllowMisskey = false,
                        bAllowMastodon = true,
                        bAuto = false,
                    )?.let {
                        ActKeywordFilter.open(this@ActText, it, initialPhrase = text)
                    }
                }
            }
        }
    }

    private fun highlight() {
        selectionOrAll.trim().notEmpty()?.let {
            startActivity(ActHighlightWordEdit.createIntent(this, it))
        }
    }

    private fun postSearchText() {
        lifecycleScope.launch {
            try {
                searchTextChannel.send(SystemClock.elapsedRealtime())
            } catch (ex: Throwable) {
                log.e(ex, "postSearchText failed.")
            }
        }
    }

    private suspend fun searchTextImpl() {
        val keyword = views.etSearch.text?.toString() ?: ""
        val content = views.etText.text?.toString() ?: ""
        val useRegex = views.btnToggleRegex.isChecked
        val searchResult: List<IntRange> = withContext(AppDispatchers.IO) {
            buildList {
                searchError = null
                if (keyword.isEmpty()) {
                    // nothing to do.
                } else if (useRegex) {
                    try {
                        val re = keyword.toRegex(RegexOption.IGNORE_CASE)
                        re.findAll(content).forEach { mr ->
                            add(mr.range)
                        }
                    } catch (ex: Throwable) {
                        log.e(ex, "search error.")
                        searchError = ex.message
                    }
                } else {
                    var nextStart = 0
                    while (nextStart < content.length) {
                        val pos = content.indexOf(
                            keyword,
                            startIndex = nextStart,
                            ignoreCase = true
                        )
                        if (pos == -1) break
                        val end = pos + keyword.length
                        add(pos until end)
                        nextStart = end
                    }
                }
            }
        }
        this.searchResult = searchResult
        when {
            searchResult.isEmpty() -> searchHighlight(null)
            else -> searchNext(byTextUpdate = true)
        }
    }

    private fun searchNext(byTextUpdate: Boolean = false) {
        try {
            val curPos = views.etText.selectionStart
            val newPos = when {
                byTextUpdate -> searchResult.find { it.first >= curPos }
                else -> searchResult.find { it.first > curPos }
            } ?: searchResult.firstOrNull()
            searchJump(newPos)
        } catch (ex: Throwable) {
            log.e(ex, "searchNext failed.")
        }
    }

    private fun searchPrev() {
        try {
            val curPos = views.etText.selectionStart.takeIf { it >= 0 }
                ?: views.etText.text?.length
                ?: return
            val newPos = searchResult.findLast { it.first < curPos }
                ?: searchResult.lastOrNull()
            searchJump(newPos)
        } catch (ex: Throwable) {
            log.e(ex, "searchPrev failed.")
        }
    }

    private fun searchJump(newPos: IntRange?) {
        searchHighlight(newPos)
    }

    private fun searchHighlight(newPos: IntRange?) {
        val hasKeyword = !views.etSearch.text.isNullOrEmpty()

        views.btnSearchClear.isEnabledAlpha = hasKeyword

        views.llSearchResult.vg(hasKeyword)?.let {
            views.btnSearchPrev.isEnabledAlpha = searchResult.size > 1
            views.btnSearchNext.isEnabledAlpha = searchResult.size > 1

            val idx = newPos?.let {
                val end = views.etText.text?.length ?: 0
                views.etText.setSelection(
                    newPos.first.clip(0, end),
                    (newPos.last + 1).clip(0, end),
                )
                searchResult.indexOf(newPos).takeIf { it >= 0 }
            }

            views.tvSearchCount.text = getString(
                R.string.search_result,
                idx?.plus(1) ?: 0,
                searchResult.size
            )
        }

        views.tvSearchError.vg(hasKeyword && !searchError.isNullOrBlank())
            ?.text = searchError

        views.etText.text?.let { e ->
            for (span in e.getSpans(0, e.length, SearchResultSpan::class.java)) {
                try {
                    e.removeSpan(span)
                } catch (ignored: Throwable) {
                }
            }
            for (pos in searchResult) {
                val attrId = when (newPos) {
                    pos -> R.attr.colorSearchFormBackground
                    else -> R.attr.colorButtonBgCw
                }
                e.setSpan(
                    SearchResultSpan(attrColor(attrId)),
                    pos.first,
                    pos.last + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}
