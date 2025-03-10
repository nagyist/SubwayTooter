package jp.juggler.subwaytooter.appsetting

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.widget.AppCompatImageView
import jp.juggler.subwaytooter.ActAppSetting
import jp.juggler.subwaytooter.ActDrawableList
import jp.juggler.subwaytooter.ActExitReasons
import jp.juggler.subwaytooter.ActGlideTest
import jp.juggler.subwaytooter.App1
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.actmain.selectPushDistributor
import jp.juggler.subwaytooter.dialog.runInProgress
import jp.juggler.subwaytooter.drawable.MediaBackgroundDrawable
import jp.juggler.subwaytooter.enableEdgeToEdgeEx
import jp.juggler.subwaytooter.itemviewholder.AdditionalButtonsPosition
import jp.juggler.subwaytooter.notification.showAlertNotification
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefF
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.pref.PrefL
import jp.juggler.subwaytooter.pref.PrefS
import jp.juggler.subwaytooter.pref.impl.BasePref
import jp.juggler.subwaytooter.pref.impl.BooleanPref
import jp.juggler.subwaytooter.pref.impl.FloatPref
import jp.juggler.subwaytooter.pref.impl.IntPref
import jp.juggler.subwaytooter.pref.impl.LongPref
import jp.juggler.subwaytooter.pref.impl.StringPref
import jp.juggler.subwaytooter.table.daoSavedAccount
import jp.juggler.subwaytooter.table.sortedByNickname
import jp.juggler.subwaytooter.util.CustomShareTarget
import jp.juggler.subwaytooter.util.openBrowser
import jp.juggler.subwaytooter.util.reNotAllowedInUserAgent
import jp.juggler.subwaytooter.util.userAgentDefault
import jp.juggler.util.coroutine.launchAndShowError
import jp.juggler.util.data.cast
import jp.juggler.util.data.intentOpenDocument
import jp.juggler.util.data.notZero
import jp.juggler.util.log.showToast
import jp.juggler.util.ui.InputTypeEx
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.getAdaptiveRippleDrawable
import jp.juggler.util.ui.getAdaptiveRippleDrawableRound
import kotlinx.coroutines.delay
import org.jetbrains.anko.backgroundDrawable
import java.util.concurrent.atomic.AtomicInteger

enum class SettingType(val id: Int) {
    Path(0),
    Divider(1),
    Switch(2),
    EditText(3),
    Spinner(4),
    ColorOpaque(5),
    ColorAlpha(6),
    Action(7),
    Sample(8),
    Group(9),
    TextWithSelector(10),
    CheckBox(11),
    Section(12),

    ;

    companion object {
        val map = entries.associateBy { it.id }
    }
}

class AppSettingItem(
    val parent: AppSettingItem?,
    val type: SettingType,
    @StringRes val caption: Int,
    val pref: BasePref<*>? = null,
) {
    val id = idSeed.incrementAndGet()

    @StringRes
    var desc: Int = 0
    var descClickSet = false
    var descClick: ActAppSetting.() -> Unit = {}
        set(value) {
            field = value
            descClickSet = true
        }

    var getError: ActAppSetting.(String) -> String? = { null }

    // may be open exportAppData() or importAppData()
    var action: ActAppSetting.() -> Unit = {}

    var changed: ActAppSetting.() -> Unit = {}

    // used for EditText
    var inputType = InputTypeEx.text

    var sampleLayoutId: Int = 0
    var sampleUpdate: (ActAppSetting, View) -> Unit = { _, _ -> }

    var spinnerArgs: IntArray? = null
    var spinnerArgsProc: (ActAppSetting) -> List<String> = { _ -> emptyList() }
    var spinnerInitializer: ActAppSetting.(Spinner) -> Unit = {}
    var spinnerOnSelected: ActAppSetting.(Spinner, Int) -> Unit = { _, _ -> }

    var enabled: Boolean = true

    var onClickEdit: ActAppSetting.() -> Unit = {}
    var onClickReset: ActAppSetting.() -> Unit = {}
    var showTextView: ActAppSetting.(TextView) -> Unit = {}
    var showEditText: ActAppSetting.(EditText) -> Unit = {}

    // for EditText
    var hint: String? = null
    var filter: (String) -> String = { it.trim() }
    var captionFontSize: ActAppSetting.() -> Float? = { null }
    var captionSpacing: ActAppSetting.() -> Float? = { null }

    // cast before save
    var toFloat: ActAppSetting.(String) -> Float = { 0f }
    var fromFloat: ActAppSetting.(Float) -> String = { it.toString() }

    val items = ArrayList<AppSettingItem>()

    fun section(
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) {
        items.add(AppSettingItem(this, SettingType.Section, caption).apply { initializer() })
    }

    fun group(
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) {
        items.add(AppSettingItem(this, SettingType.Group, caption).apply { initializer() })
    }

    fun item(
        type: SettingType,
        pref: BasePref<*>?,
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ): AppSettingItem {
        val item = AppSettingItem(this, type, caption, pref).apply { initializer() }
        items.add(item)
        return item
    }

    fun spinnerSimple(
        pref: IntPref,
        @StringRes caption: Int,
        vararg args: Int,
        initializer: (AppSettingItem.() -> Unit)? = null,
    ) = item(SettingType.Spinner, pref, caption) {
        spinnerArgs = args
        initializer?.invoke(this)
    }

    fun spinner(
        pref: IntPref,
        @StringRes caption: Int,
        argsProc: (ActAppSetting) -> List<String>,
    ) = item(SettingType.Spinner, pref, caption) {
        spinnerArgsProc = argsProc
    }

    fun sw(
        pref: BooleanPref,
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.Switch, pref, caption, initializer)

    fun checkbox(
        pref: BooleanPref,
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.CheckBox, pref, caption, initializer)

    fun action(
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.Action, null, caption, initializer)

    fun colorOpaque(
        pref: IntPref,
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.ColorOpaque, pref, caption, initializer)

    fun colorAlpha(
        pref: IntPref,
        @StringRes caption: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.ColorAlpha, pref, caption, initializer)

    fun text(
        pref: StringPref,
        @StringRes caption: Int,
        inputType: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.EditText, pref, caption) {
        this.inputType = inputType
        this.initializer()
    }

    fun textX(
        pref: BasePref<*>,
        @StringRes caption: Int,
        inputType: Int,
        initializer: AppSettingItem.() -> Unit = {},
    ) = item(SettingType.EditText, pref, caption) {
        this.inputType = inputType
        this.initializer()
    }

    fun sample(
        sampleLayoutId: Int = 0,
        sampleUpdate: (ActAppSetting, View) -> Unit = { _, _ -> },
        // ,initializer : AppSettingItem.() -> Unit = {}
    ) = item(SettingType.Sample, pref, caption) {
        this.sampleLayoutId = sampleLayoutId
        this.sampleUpdate = sampleUpdate
    }

    fun scan(block: (AppSettingItem) -> Unit) {
        block(this)
        for (item in items) item.scan(block)
    }

    override fun hashCode() = id
    override fun equals(other: Any?) = (other as? AppSettingItem)?.id == this.id

    // 項目のcaptionがqueryにマッチするなら真
    fun match(
        context: Context,
        query: String,
    ): Boolean = when (caption) {
        0 -> false
        else -> context.getString(caption).contains(query, ignoreCase = true)
    }

    companion object {
        var idSeed = AtomicInteger(0)

        var SAMPLE_CCD_HEADER: AppSettingItem? = null
        var SAMPLE_CCD_BODY: AppSettingItem? = null
        var SAMPLE_FOOTER: AppSettingItem? = null

        var TIMELINE_FONT: AppSettingItem? = null
        var TIMELINE_FONT_BOLD: AppSettingItem? = null

        var FONT_SIZE_TIMELINE: AppSettingItem? = null
        var FONT_SIZE_NOTIFICATION_TL: AppSettingItem? = null
    }
}

val appSettingRoot = AppSettingItem(null, SettingType.Section, R.string.app_setting).apply {

    section(R.string.notifications) {
        action(R.string.push_distributor) {
            action = { selectPushDistributor() }
            desc = R.string.push_distributor_desc
        }

        text(
            PrefS.spPullNotificationCheckInterval,
            R.string.pull_notification_check_interval,
            InputTypeEx.number
        )

        sw(PrefB.bpShowAcctInSystemNotification, R.string.show_acct_in_system_notification)

        sw(PrefB.bpSeparateReplyNotificationGroup, R.string.separate_notification_group_for_reply)

        sw(PrefB.bpDivideNotification, R.string.divide_notification)

        // sw(PrefB.bpMisskeyNotificationCheck, R.string.enable_misskey_notification_check)

        sample(R.layout.setting_sample_notification_desc)
    }

    section(R.string.behavior) {

        sw(PrefB.bpDontConfirmBeforeCloseColumn, R.string.dont_confirm_before_close_column)

        spinnerSimple(
            PrefI.ipBackButtonAction,
            R.string.back_button_action,
            R.string.ask_always,
            R.string.close_column,
            R.string.open_column_list,
            R.string.app_exit
        )

        sw(PrefB.bpExitAppWhenCloseProtectedColumn, R.string.exit_app_when_close_protected_column)
        sw(PrefB.bpScrollTopFromColumnStrip, R.string.scroll_top_from_column_strip)
        sw(PrefB.bpDontScreenOff, R.string.dont_screen_off)
        sw(PrefB.bpDontUseCustomTabs, R.string.dont_use_custom_tabs)
        sw(PrefB.bpPriorChrome, R.string.prior_chrome_custom_tabs)

//        item(
//            SettingType.TextWithSelector,
//            PrefS.spWebBrowser,
//            R.string.web_browser
//        ) {
//            onClickEdit = {
//                openWebBrowserChooser(
//                    this@item,
//                    intent = Intent(Intent.ACTION_VIEW, "https://joinmastodon.org/".toUri()).apply {
//                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//                        addCategory(Intent.CATEGORY_BROWSABLE)
//                    },
//                    filter = {
//                        when {
//                            it.activityInfo.packageName == packageName -> false
//                            !it.activityInfo.exported -> false
//
//                            // Huaweiの謎Activityのせいでうまく働かないことがある
//                            -1 != it.activityInfo.packageName.indexOf("com.huawei.android.internal") -> false
//
//                            // 標準アプリが設定されていない場合、アプリを選択するためのActivityが出てくる場合がある
//                            it.activityInfo.packageName == "android" -> false
//                            it.activityInfo.javaClass.name.startsWith("com.android.internal") -> false
//                            it.activityInfo.javaClass.name.startsWith("com.android.systemui") -> false
//
//                            // たぶんChromeとかfirefoxとか
//                            else -> true
//                        }
//                    }
//                )
//            }
//            onClickReset = { setWebBrowser(this@item, "") }
//            showTextView = {
//                showWebBrowser(it, this@item.pref.cast<StringPref>()!!.invoke(pref))
//            }
//        }

        sw(PrefB.bpAllowColumnDuplication, R.string.allow_column_duplication)
        sw(PrefB.bpForceGap, R.string.force_gap_when_refresh)
        spinnerSimple(
            PrefI.ipGapHeadScrollPosition,
            R.string.scroll_position_after_read_gap_from_head,
            R.string.gap_head,
            R.string.gap_tail,
        )
        spinnerSimple(
            PrefI.ipGapTailScrollPosition,
            R.string.scroll_position_after_read_gap_from_tail,
            R.string.gap_head,
            R.string.gap_tail,
        )
        text(PrefS.spClientName, R.string.client_name, InputTypeEx.text)

        text(PrefS.spUserAgent, R.string.user_agent, InputTypeEx.textMultiLine) {
            showEditText = { it.hint = userAgentDefault() }
            filter = { it.replace(ActAppSetting.reLinefeed, " ").trim() }
            getError = {
                val m = reNotAllowedInUserAgent.matcher(it)
                when (m.find()) {
                    true -> getString(R.string.user_agent_error, m.group())
                    else -> null
                }
            }
        }

        sw(PrefB.bpDontRemoveDeletedToot, R.string.dont_remove_deleted_toot_from_timeline)
        sw(PrefB.bpShowTranslateButton, R.string.show_translate_button)

        spinnerSimple(
            PrefI.ipAdditionalButtonsPosition,
            R.string.additional_buttons_position,
            *(AdditionalButtonsPosition.entries.sortedBy { it.idx }.map { it.captionId }
                .toIntArray())
        )

        sw(PrefB.bpEnablePixelfed, R.string.enable_connect_to_pixelfed_server)

        sw(PrefB.bpShowFilteredWord, R.string.show_filtered_word)
        sw(PrefB.bpShowUsernameFilteredPost, R.string.show_username_on_filtered_post)

        sw(PrefB.bpEnableDomainTimeline, R.string.enable_domain_timeline)
    }

    section(R.string.translate_or_custom_share) {
        for (target in CustomShareTarget.entries) {
            item(
                SettingType.TextWithSelector,
                target.pref,
                target.captionId,
            ) {
                onClickEdit = { openCustomShareChooser(this@item, target) }
                onClickReset = { setCustomShare(this@item, target, "") }
                showTextView = { showCustomShareIcon(it, target) }
            }
        }
    }

    section(R.string.post) {

        item(
            SettingType.Spinner,
            PrefL.lpDefaultPostAccount,
            R.string.post_compose_button_default_account
        ) {
            val lp = pref.cast<LongPref>()!!
            spinnerInitializer = { spinner ->
                launchAndShowError {
                    val list = daoSavedAccount.loadRealAccounts()
                        .sortedByNickname()
                    val listAdapter = AccountAdapter(list)
                    spinner.adapter = listAdapter
                    spinner.setSelection(listAdapter.getIndexFromId(lp.value))
                }
            }
            spinnerOnSelected = { spinner, index ->
                spinner.adapter.cast<ActAppSetting.AccountAdapter>()
                    ?.getIdFromIndex(index)
                    ?.let { lp.value = it }
            }
        }

        spinnerSimple(
            PrefI.ipRefreshAfterToot,
            R.string.refresh_after_toot,
            R.string.refresh_scroll_to_toot,
            R.string.refresh_no_scroll,
            R.string.dont_refresh
        )

        sw(PrefB.bpPostButtonBarTop, R.string.show_post_button_bar_top)

        sw(
            PrefB.bpDontDuplicationCheck,
            R.string.dont_add_duplication_check_header
        )

        sw(PrefB.bpQuickPostBar, R.string.show_quick_toot_bar)

        sw(
            PrefB.bpDontUseActionButtonWithQuickPostBar,
            R.string.dont_use_action_button_with_quick_toot_bar
        )

        text(PrefS.spQuoteNameFormat, R.string.format_of_quote_name, InputTypeEx.text) {
            filter = { it } // don't trim
        }

        sw(
            PrefB.bpAppendAttachmentUrlToContent,
            R.string.append_attachment_url_to_content
        )

        sw(
            PrefB.bpWarnHashtagAsciiAndNonAscii,
            R.string.warn_hashtag_ascii_and_non_ascii
        )

        sw(PrefB.bpIgnoreTextInSharedMedia, R.string.ignore_text_in_shared_media)

        sw(
            PrefB.bpUseWebP,
            R.string.use_webp_format_if_server_accepts,
        )
    }

    section(R.string.tablet_mode) {

        sw(PrefB.bpDisableTabletMode, R.string.disable_tablet_mode)

        text(PrefS.spColumnWidth, R.string.minimum_column_width, InputTypeEx.number)

        sw(
            PrefB.bpQuickTootOmitAccountSelection,
            R.string.quick_toot_omit_account_selection
        )

        spinnerSimple(
            PrefI.ipJustifyWindowContentPortrait,
            R.string.justify_window_content_portrait,
            R.string.default_,
            R.string.start,
            R.string.end
        )

        sw(
            PrefB.bpMultiWindowPost,
            R.string.multi_window_post
        )
        sw(
            PrefB.bpManyWindowPost,
            R.string.many_window_post
        )
        sw(
            PrefB.bpTabletSnap,
            R.string.tablet_snap
        )
    }

    section(R.string.media_attachment) {
        sw(PrefB.bpUseInternalMediaViewer, R.string.use_internal_media_viewer)

        spinner(PrefI.ipMediaBackground, R.string.background_pattern) {
            MediaBackgroundDrawable.Kind.entries
                .filter { it.isMediaBackground }
                .map { it.name }
        }

        sw(PrefB.bpPriorLocalURL, R.string.prior_local_url_when_open_attachment)
        text(PrefS.spMediaThumbHeight, R.string.media_thumbnail_height, InputTypeEx.number)
        sw(PrefB.bpDontCropMediaThumb, R.string.dont_crop_media_thumbnail)
        sw(PrefB.bpVerticalArrangeThumbnails, R.string.thumbnails_arrange_vertically)
    }

    section(R.string.animation) {
        sw(PrefB.bpImageAnimationEnable, R.string.image_animation_enable)
        sw(PrefB.bpDisableEmojiAnimation, R.string.disable_custom_emoji_animation)
    }

    section(R.string.emoji) {

        sw(PrefB.bpUseTwemoji, R.string.use_twemoji_emoji)

        sw(PrefB.bpEmojioneShortcode, R.string.emojione_shortcode_support) {
            desc = R.string.emojione_shortcode_support_desc
        }

        sw(PrefB.bpEmojiPickerCategoryOther, R.string.show_emoji_picker_other_category)

        sw(
            PrefB.bpEmojiPickerCloseOnSelected,
            R.string.close_emoji_picker_when_selected
        )

        sw(PrefB.bpCustomEmojiSeparatorZwsp, R.string.custom_emoji_separator_zwsp)

        text(
            PrefS.spEmojiSizeMastodon,
            R.string.emoji_size_mastodon,
            InputTypeEx.number
        )
        text(
            PrefS.spEmojiSizeMisskey,
            R.string.emoji_size_misskey,
            InputTypeEx.number
        )
        text(
            PrefS.spEmojiSizeReaction,
            R.string.emoji_size_reaction,
            InputTypeEx.number
        )
        text(
            PrefS.spEmojiSizeUserName,
            R.string.emoji_size_user_name,
            InputTypeEx.number
        )

        spinnerSimple(
            PrefI.ipEmojiWideMode,
            R.string.emoji_wide_mode,
            R.string.auto,
            R.string.enabled,
            R.string.disabled,
        )
        text(
            PrefS.spEmojiPixels,
            R.string.emoji_texture_pixels,
            InputTypeEx.number
        )
        sw(
            PrefB.bpCollapseEmojiPickerCategory,
            R.string.emoji_picker_category_collapse
        )
    }

    section(R.string.appearance) {
        sw(PrefB.bpSimpleList, R.string.simple_list)
        sw(PrefB.bpShowFollowButtonInButtonBar, R.string.show_follow_button_in_button_bar)
        sw(PrefB.bpDontShowPreviewCard, R.string.dont_show_preview_card)
        sw(PrefB.bpShortAcctLocalUser, R.string.short_acct_local_user)
        sw(PrefB.bpMentionFullAcct, R.string.mention_full_acct)
        sw(PrefB.bpRelativeTimestamp, R.string.relative_timestamp)

        item(
            SettingType.Spinner,
            PrefS.spTimeZone,
            R.string.timezone
        ) {
            val sp: StringPref = pref.cast()!!
            spinnerInitializer = { spinner ->
                val adapter = TimeZoneAdapter()
                spinner.adapter = adapter
                spinner.setSelection(adapter.getIndexFromId(sp.value))
            }
            spinnerOnSelected = { spinner, index ->
                val adapter = spinner.adapter.cast<ActAppSetting.TimeZoneAdapter>()
                    ?: error("spinnerOnSelected: missing TimeZoneAdapter")
                sp.value = adapter.getIdFromIndex(index)
            }
        }

        sw(PrefB.bpShowAppName, R.string.always_show_application)
        sw(PrefB.bpShowLanguage, R.string.always_show_language)
        text(PrefS.spAutoCWLines, R.string.auto_cw, InputTypeEx.number)
        text(PrefS.spCardDescriptionLength, R.string.card_description_length, InputTypeEx.number)

        spinnerSimple(
            PrefI.ipRepliesCount,
            R.string.display_replies_count,
            R.string.replies_count_simple,
            R.string.replies_count_actual,
            R.string.replies_count_none
        )
        spinnerSimple(
            PrefI.ipBoostsCount,
            R.string.display_boost_count,
            R.string.replies_count_simple,
            R.string.replies_count_actual,
            R.string.replies_count_none
        )
        spinnerSimple(
            PrefI.ipFavouritesCount,
            R.string.display_favourite_count,
            R.string.replies_count_simple,
            R.string.replies_count_actual,
            R.string.replies_count_none
        )

        spinnerSimple(
            PrefI.ipVisibilityStyle,
            R.string.visibility_style,
            R.string.visibility_style_by_account,
            R.string.mastodon,
            R.string.misskey
        )

        AppSettingItem.TIMELINE_FONT = item(
            SettingType.TextWithSelector,
            PrefS.spTimelineFont,
            R.string.timeline_font
        ) {
            val item = this
            onClickEdit = {
                try {
                    val intent = intentOpenDocument("*/*")
                    arTimelineFont.launch(intent)
                } catch (ex: Throwable) {
                    showToast(ex, "could not open picker for font")
                }
            }
            onClickReset = {
                item.pref?.removeValue()
                showTimelineFont(item)
            }
            showTextView = { showTimelineFont(item, it) }
        }

        AppSettingItem.TIMELINE_FONT_BOLD = item(
            SettingType.TextWithSelector,
            PrefS.spTimelineFontBold,
            R.string.timeline_font_bold
        ) {
            val item = this
            onClickEdit = {
                try {
                    val intent = intentOpenDocument("*/*")
                    arTimelineFontBold.launch(intent)
                } catch (ex: Throwable) {
                    showToast(ex, "could not open picker for font")
                }
            }
            onClickReset = {
                item.pref?.removeValue()
                showTimelineFont(AppSettingItem.TIMELINE_FONT_BOLD)
            }
            showTextView = { showTimelineFont(item, it) }
        }

        AppSettingItem.FONT_SIZE_TIMELINE = textX(
            PrefF.fpTimelineFontSize,
            R.string.timeline_font_size,
            InputTypeEx.numberDecimal
        ) {

            val item = this
            val fp: FloatPref = item.pref.cast()!!

            toFloat = { parseFontSize(it) }
            fromFloat = { formatFontSize(it) }

            captionFontSize = {
                val fv = fp.value
                when {
                    !fv.isFinite() -> PrefF.default_timeline_font_size
                    fv < 1f -> 1f
                    else -> fv
                }
            }
            captionSpacing = {
                PrefS.spTimelineSpacing.value.toFloatOrNull()
            }
            changed = {
                findItemViewHolder(item)?.updateCaption()
            }
        }

        textX(PrefF.fpAcctFontSize, R.string.acct_font_size, InputTypeEx.numberDecimal) {
            val item = this
            val fp: FloatPref = item.pref.cast()!!

            toFloat = { parseFontSize(it) }
            fromFloat = { formatFontSize(it) }

            captionFontSize = {
                val fv = fp.value
                when {
                    !fv.isFinite() -> PrefF.default_acct_font_size
                    fv < 1f -> 1f
                    else -> fv
                }
            }

            changed = { findItemViewHolder(item)?.updateCaption() }
        }

        AppSettingItem.FONT_SIZE_NOTIFICATION_TL = textX(
            PrefF.fpNotificationTlFontSize,
            R.string.notification_tl_font_size,
            InputTypeEx.numberDecimal
        ) {
            val item = this
            val fp: FloatPref = item.pref.cast()!!

            toFloat = { parseFontSize(it) }
            fromFloat = { formatFontSize(it) }

            captionFontSize = {
                val fv = fp.value
                when {
                    !fv.isFinite() -> PrefF.default_notification_tl_font_size
                    fv < 1f -> 1f
                    else -> fv
                }
            }
            captionSpacing = {
                PrefS.spTimelineSpacing.value.toFloatOrNull()
            }
            changed = {
                findItemViewHolder(item)?.updateCaption()
            }
        }

        text(
            PrefS.spNotificationTlIconSize,
            R.string.notification_tl_icon_size,
            InputTypeEx.numberDecimal
        )

        text(PrefS.spTimelineSpacing, R.string.timeline_line_spacing, InputTypeEx.numberDecimal) {
            changed = {
                findItemViewHolder(AppSettingItem.FONT_SIZE_TIMELINE)?.updateCaption()
                findItemViewHolder(AppSettingItem.FONT_SIZE_NOTIFICATION_TL)?.updateCaption()
            }
        }

        text(PrefS.spBoostButtonSize, R.string.boost_button_size, InputTypeEx.numberDecimal)

        spinnerSimple(
            PrefI.ipBoostButtonJustify,
            R.string.boost_button_alignment,
            R.string.start,
            R.string.center,
            R.string.end
        )

        text(PrefS.spAvatarIconSize, R.string.avatar_icon_size, InputTypeEx.numberDecimal)
        text(PrefS.spRoundRatio, R.string.avatar_icon_round_ratio, InputTypeEx.numberDecimal)
        sw(PrefB.bpDontRound, R.string.avatar_icon_dont_round)
        text(PrefS.spReplyIconSize, R.string.reply_icon_size, InputTypeEx.numberDecimal)
        text(PrefS.spHeaderIconSize, R.string.header_icon_size, InputTypeEx.numberDecimal)
        textX(PrefF.fpHeaderTextSize, R.string.header_text_size, InputTypeEx.numberDecimal) {
            val item = this
            val fp: FloatPref = item.pref.cast()!!

            toFloat = { parseFontSize(it) }
            fromFloat = { formatFontSize(it) }

            captionFontSize = {
                val fv = fp.value
                when {
                    !fv.isFinite() -> PrefF.default_header_font_size
                    fv < 1f -> 1f
                    else -> fv
                }
            }

            changed = {
                findItemViewHolder(item)?.updateCaption()
            }
        }

        text(PrefS.spStripIconSize, R.string.strip_icon_size, InputTypeEx.numberDecimal)

        text(PrefS.spScreenBottomPadding, R.string.screen_bottom_padding, InputTypeEx.numberDecimal)

        sw(PrefB.bpOpenSticker, R.string.show_open_sticker) {
            desc = R.string.powered_by_open_sticker
            descClick = { openBrowser("https://github.com/cutls/OpenSticker") }
        }

        sw(PrefB.bpLinksInContextMenu, R.string.show_links_in_context_menu)
        sw(PrefB.bpShowLinkUnderline, R.string.show_link_underline)

        sw(PrefB.bpMfmDecorationEnabled, R.string.mfm_decoration_enabled)
        sw(PrefB.bpMfmDecorationShowUnsupportedMarkup, R.string.mfm_show_unsupported_markup)

        sw(
            PrefB.bpMoveNotificationsQuickFilter,
            R.string.move_notifications_quick_filter_to_column_setting
        )
        sw(PrefB.bpShowSearchClear, R.string.show_clear_button_in_search_bar)
        sw(
            PrefB.bpDontShowColumnBackgroundImage,
            R.string.dont_show_column_background_image
        )

        group(R.string.show_in_directory) {
            checkbox(PrefB.bpDirectoryLastActive, R.string.last_active)
            checkbox(PrefB.bpDirectoryFollowers, R.string.followers)
            checkbox(PrefB.bpDirectoryTootCount, R.string.toot_count)
            checkbox(PrefB.bpDirectoryNote, R.string.note)
        }

        sw(
            PrefB.bpAlwaysExpandContextMenuItems,
            R.string.always_expand_context_menu_sub_items
        )
        sw(PrefB.bpShowBookmarkButton, R.string.show_bookmark_button)
        sw(PrefB.bpHideFollowCount, R.string.hide_followers_count)

        sw(PrefB.bpKeepReactionSpace, R.string.keep_reaction_space)

        text(PrefS.spEventTextAlpha, R.string.event_text_alpha, InputTypeEx.numberDecimal)
    }

    section(R.string.color) {

        spinnerSimple(
            PrefI.ipUiTheme,
            R.string.ui_theme,
            R.string.theme_light,
            R.string.theme_dark,
            R.string.theme_mastodon_dark,
        )

        colorAlpha(PrefI.ipListDividerColor, R.string.list_divider_color)
        colorAlpha(PrefI.ipLinkColor, R.string.link_color)

        group(R.string.toot_background_color) {
            colorAlpha(PrefI.ipTootColorUnlisted, R.string.unlisted_visibility)
            colorAlpha(PrefI.ipTootColorFollower, R.string.followers_visibility)
            colorAlpha(PrefI.ipTootColorDirectUser, R.string.direct_with_user_visibility)
            colorAlpha(PrefI.ipTootColorDirectMe, R.string.direct_only_me_visibility)
        }

        group(R.string.event_background_color) {
            colorAlpha(PrefI.ipEventBgColorBoost, R.string.boost)
            colorAlpha(PrefI.ipEventBgColorFavourite, R.string.favourites)
            colorAlpha(PrefI.ipEventBgColorBookmark, R.string.bookmarks)
            colorAlpha(PrefI.ipEventBgColorMention, R.string.reply)
            colorAlpha(PrefI.ipEventBgColorFollow, R.string.follow)
            colorAlpha(PrefI.ipEventBgColorUnfollow, R.string.unfollow_misskey)
            colorAlpha(PrefI.ipEventBgColorFollowRequest, R.string.follow_request)
            colorAlpha(PrefI.ipEventBgColorReaction, R.string.reaction)
            colorAlpha(PrefI.ipEventBgColorQuote, R.string.quote_renote)
            colorAlpha(PrefI.ipEventBgColorVote, R.string.vote_polls)
            colorAlpha(PrefI.ipEventBgColorStatus, R.string.status)
            colorAlpha(PrefI.ipEventBgColorUpdate, R.string.notification_type_update)
            colorAlpha(
                PrefI.ipEventBgColorStatusReference,
                R.string.notification_type_status_reference
            )
            colorAlpha(PrefI.ipEventBgColorSignUp, R.string.notification_type_signup)
            colorAlpha(PrefI.ipEventBgColorReport, R.string.notification_type_report)

            colorAlpha(
                PrefI.ipConversationMainTootBgColor,
                R.string.conversation_main_toot_background_color
            )

            colorAlpha(PrefI.ipEventBgColorGap, R.string.gap)
            colorAlpha(PrefI.ipEventBgColorFiltered, R.string.filtered)
        }

        group(R.string.button_accent_color) {
            colorAlpha(PrefI.ipButtonBoostedColor, R.string.boost)
            colorAlpha(PrefI.ipButtonFavoritedColor, R.string.favourites)
            colorAlpha(PrefI.ipButtonBookmarkedColor, R.string.bookmarks)
            colorAlpha(PrefI.ipButtonFollowingColor, R.string.follow)
            colorAlpha(PrefI.ipButtonFollowRequestColor, R.string.follow_request)
            colorAlpha(PrefI.ipButtonReactionedColor, R.string.reaction)
        }

        group(R.string.column_color_default) {
            AppSettingItem.SAMPLE_CCD_HEADER =
                sample(R.layout.setting_sample_column_header) { activity, viewRoot ->
                    showSampleCcdHeader(activity, viewRoot)
                }

            colorOpaque(PrefI.ipCcdHeaderBg, R.string.header_background_color) {
                changed = { showSample(AppSettingItem.SAMPLE_CCD_HEADER) }
            }
            colorOpaque(PrefI.ipCcdHeaderFg, R.string.header_foreground_color) {
                changed = { showSample(AppSettingItem.SAMPLE_CCD_HEADER) }
            }

            AppSettingItem.SAMPLE_CCD_BODY =
                sample(R.layout.setting_sample_column_body) { activity, viewRoot ->
                    showSampleColumnBody(activity, viewRoot)
                }

            colorOpaque(PrefI.ipCcdContentBg, R.string.content_background_color) {
                changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
            }
            colorAlpha(PrefI.ipCcdContentAcct, R.string.content_acct_color) {
                changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
            }
            colorAlpha(PrefI.ipCcdContentText, R.string.content_text_color) {
                changed = { showSample(AppSettingItem.SAMPLE_CCD_BODY) }
            }
        }

        text(PrefS.spBoostAlpha, R.string.boost_button_alpha, InputTypeEx.numberDecimal)

        group(R.string.footer_color) {
            AppSettingItem.SAMPLE_FOOTER =
                sample(R.layout.setting_sample_footer) { activity, viewRoot ->
                    showSampleFooter(activity, viewRoot)
                }

            colorOpaque(PrefI.ipFooterButtonBgColor, R.string.button_background_color) {
                changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
            }
            colorOpaque(PrefI.ipFooterButtonFgColor, R.string.button_foreground_color) {
                changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
            }
            colorOpaque(PrefI.ipFooterTabBgColor, R.string.quick_toot_bar_background_color) {
                changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
            }
            colorOpaque(PrefI.ipFooterTabDividerColor, R.string.tab_divider_color) {
                changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
            }
            colorAlpha(PrefI.ipFooterTabIndicatorColor, R.string.tab_indicator_color) {
                changed = { showSample(AppSettingItem.SAMPLE_FOOTER) }
            }
        }

        colorOpaque(PrefI.ipSwitchOnColor, R.string.switch_button_color) {
            changed = { setSwitchColor() }
        }

        colorOpaque(PrefI.ipWindowInsetsColor, R.string.windows_insets_color) {
            changed = { enableEdgeToEdgeEx(forceDark = false) }
        }

        colorOpaque(PrefI.ipSearchBgColor, R.string.search_bar_background_color)
        colorAlpha(PrefI.ipPopupBgColor, R.string.popup_background_color)
        colorAlpha(PrefI.ipAnnouncementsBgColor, R.string.announcement_background_color)
        colorAlpha(PrefI.ipVerifiedLinkBgColor, R.string.verified_link_background_color)
        colorAlpha(PrefI.ipVerifiedLinkFgColor, R.string.verified_link_foreground_color)
    }

    section(R.string.performance) {
        sw(PrefB.bpShareViewPool, R.string.share_view_pool)
        sw(PrefB.bpDontUseStreaming, R.string.dont_use_streaming_api)
        sw(PrefB.bpDontRefreshOnResume, R.string.dont_refresh_on_activity_resume)
        text(PrefS.spMediaReadTimeout, R.string.timeout_for_embed_media_viewer, InputTypeEx.number)
        text(PrefS.spApiReadTimeout, R.string.timeout_for_server_api, InputTypeEx.number)
        action(R.string.delete_custom_emoji_cache) {
            action = {
                App1.custom_emoji_cache.delete()
            }
        }
    }

    section(R.string.developer_options) {
        sw(PrefB.bpCheckBetaVersion, R.string.check_beta_release)

        sw(PrefB.bpEnableDeprecatedSomething, R.string.enable_deprecated_something)

        action(R.string.drawable_list) {
            action = { startActivity(Intent(this, ActDrawableList::class.java)) }
        }

        action(R.string.exit_reasons) {
            action = {
                if (Build.VERSION.SDK_INT >= 30) {
                    startActivity(Intent(this, ActExitReasons::class.java))
                } else {
                    showToast(false, "this feature requires Android 11")
                }
            }
        }

        action(R.string.test_progress_dialog) {
            action = {
                launchAndShowError {
                    runInProgress(cancellable = true) {
                        it.setMessage("message")
                        it.setTitle("title")
                        delay(2000L)
                        it.setMessage("message ".repeat(30))
                        it.setTitle("title ".repeat(30))
                        delay(2000L)
                    }
                }
            }
        }

        action(R.string.glide_test) {
            action = {
                startActivity(Intent(this, ActGlideTest::class.java))
            }
        }

        action(R.string.alert_test) {
            action = { showAlertNotification("this is a test.") }
        }
    }

    section(R.string.bug_report) {
        spinnerSimple(
            PrefI.ipLogSaveLevel,
            R.string.log_save_level, // name
            R.string.log_all, //0
            R.string.log_all, //1
            R.string.log_verbose, //2
            R.string.log_debug, //3
            R.string.log_info, //4
            R.string.log_warn, //5
            R.string.log_error, //6
            R.string.log_assert, //7
        ) {
            desc = R.string.log_save_level_desc
        }

        action(R.string.send_log_email) {
            action = { exportLog() }
            desc = R.string.bug_report_desc
        }
    }

    section(R.string.app_data_export_import) {
        group(R.string.app_data_export) {
            action(R.string.save_to_local_folder) {
                action = { saveAppData() }
            }
            action(R.string.send_to_other_app) {
                action = { sendAppData() }
            }
        }
        action(R.string.app_data_import) {
            action = { importAppData1() }
            desc = R.string.app_data_import_desc
        }
    }
}

private fun showSampleCcdHeader(activity: ActAppSetting, viewRoot: View) {
    val llColumnHeader: View = viewRoot.findViewById(R.id.llColumnHeader)
    val ivColumnHeader: ImageView = viewRoot.findViewById(R.id.ivColumnHeader)
    val tvColumnName: TextView = viewRoot.findViewById(R.id.tvColumnName)

    val colorColumnHeaderBg = PrefI.ipCcdHeaderBg.value
    val colorColumnHeaderFg = PrefI.ipCcdHeaderFg.value

    val headerBg = when {
        colorColumnHeaderBg != 0 -> colorColumnHeaderBg
        else -> activity.attrColor(R.attr.color_column_header)
    }

    val headerFg = when {
        colorColumnHeaderFg != 0 -> colorColumnHeaderFg
        else -> activity.attrColor(R.attr.colorColumnHeaderName)
    }

    llColumnHeader.background = getAdaptiveRippleDrawable(headerBg, headerFg)

    tvColumnName.setTextColor(headerFg)
    ivColumnHeader.setImageResource(R.drawable.ic_bike)
    ivColumnHeader.imageTintList = ColorStateList.valueOf(headerFg)
}

private fun showSampleColumnBody(activity: ActAppSetting, viewRoot: View) {
    val flColumnBackground: View = viewRoot.findViewById(R.id.flColumnBackground)
    val tvSampleAcct: TextView = viewRoot.findViewById(R.id.tvSampleAcct)
    val tvSampleContent: TextView = viewRoot.findViewById(R.id.tvSampleContent)

    val colorColumnBg = PrefI.ipCcdContentBg.value
    val colorColumnAcct = PrefI.ipCcdContentAcct.value
    val colorColumnText = PrefI.ipCcdContentText.value

    flColumnBackground.setBackgroundColor(colorColumnBg) // may 0

    tvSampleAcct.setTextColor(
        colorColumnAcct.notZero()
            ?: activity.attrColor(R.attr.colorTimeSmall)
    )

    tvSampleContent.setTextColor(
        colorColumnText.notZero()
            ?: activity.attrColor(R.attr.colorTextContent)
    )
}

private fun showSampleFooter(activity: ActAppSetting, viewRoot: View) {
    val ivFooterToot: AppCompatImageView = viewRoot.findViewById(R.id.ivFooterToot)
    val ivFooterMenu: AppCompatImageView = viewRoot.findViewById(R.id.ivFooterMenu)
    val llFooterBG: View = viewRoot.findViewById(R.id.llFooterBG)
    val vFooterDivider1: View = viewRoot.findViewById(R.id.vFooterDivider1)
    val vFooterDivider2: View = viewRoot.findViewById(R.id.vFooterDivider2)
    val vIndicator: View = viewRoot.findViewById(R.id.vIndicator)

    val footerButtonBgColor = PrefI.ipFooterButtonBgColor.value
    val footerButtonFgColor = PrefI.ipFooterButtonFgColor.value
    val footerTabBgColor = PrefI.ipFooterTabBgColor.value
    val footerTabDividerColor = PrefI.ipFooterTabDividerColor.value
    val footerTabIndicatorColor = PrefI.ipFooterTabIndicatorColor.value

    val colorColumnStripBackground = footerTabBgColor.notZero()
        ?: activity.attrColor(R.attr.colorColumnStripBackground)

    llFooterBG.setBackgroundColor(colorColumnStripBackground)

    val colorButtonBg = footerButtonBgColor.notZero()
        ?: colorColumnStripBackground

    val colorButtonFg = footerButtonFgColor.notZero()
        ?: activity.attrColor(R.attr.colorRippleEffect)

    ivFooterMenu.backgroundDrawable =
        getAdaptiveRippleDrawableRound(activity, colorButtonBg, colorButtonFg)
    ivFooterToot.backgroundDrawable =
        getAdaptiveRippleDrawableRound(activity, colorButtonBg, colorButtonFg)

    val csl = ColorStateList.valueOf(
        footerButtonFgColor.notZero()
            ?: activity.attrColor(R.attr.colorTextContent)
    )
    ivFooterToot.imageTintList = csl
    ivFooterMenu.imageTintList = csl

    val c = footerTabDividerColor.notZero()
        ?: colorColumnStripBackground
    vFooterDivider1.setBackgroundColor(c)
    vFooterDivider2.setBackgroundColor(c)

    vIndicator.setBackgroundColor(
        footerTabIndicatorColor.notZero()
            ?: activity.attrColor(R.attr.colorTextHelp)
    )
}
