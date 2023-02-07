package jp.juggler.subwaytooter.itemviewholder

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import com.google.android.flexbox.FlexWrap
import com.google.android.flexbox.FlexboxLayout
import com.google.android.flexbox.JustifyContent
import jp.juggler.subwaytooter.ActMain
import jp.juggler.subwaytooter.R
import jp.juggler.subwaytooter.action.reactionAdd
import jp.juggler.subwaytooter.action.reactionFromAnotherAccount
import jp.juggler.subwaytooter.action.reactionRemove
import jp.juggler.subwaytooter.api.entity.TootReaction
import jp.juggler.subwaytooter.api.entity.TootStatus
import jp.juggler.subwaytooter.pref.PrefB
import jp.juggler.subwaytooter.pref.PrefI
import jp.juggler.subwaytooter.util.DecodeOptions
import jp.juggler.subwaytooter.util.NetworkEmojiInvalidator
import jp.juggler.subwaytooter.util.minWidthCompat
import jp.juggler.subwaytooter.util.startMargin
import jp.juggler.util.data.notZero
import jp.juggler.util.log.LogCategory
import jp.juggler.util.ui.attrColor
import jp.juggler.util.ui.getAdaptiveRippleDrawableRound
import org.jetbrains.anko.allCaps
import org.jetbrains.anko.dip

private val log = LogCategory("ItemViewHolderReaction")

fun ItemViewHolder.makeReactionsView(status: TootStatus) {
    val reactionSet = status.reactionSet
    if (reactionSet?.hasReaction() != true) {
        if (!TootReaction.canReaction(accessInfo) || !PrefB.bpKeepReactionSpace.value) return
    }

    val density = activity.density

    fun Float.round() = (this + 0.5f).toInt()

    val imageScale = 1.5f
    val buttonHeight = ActMain.boostButtonSize // px
    val marginBetween = (buttonHeight * 0.05f).round()
    val paddingH = (buttonHeight * 0.1f).round()
    val textHeight = (buttonHeight * 0.7f) / imageScale

    val act = this@makeReactionsView.activity // not Button(View).getActivity()

    val box = FlexboxLayout(activity).apply {
        flexWrap = FlexWrap.WRAP
        justifyContent = JustifyContent.FLEX_START
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = (0.5f + density * 3f).toInt()
        }
    }

    if (reactionSet?.isEmpty() != false) {
        val v = View(act).apply {
            layoutParams = FlexboxLayout.LayoutParams(0, buttonHeight)
            setPadding(paddingH, 0, paddingH, 0)
        }
        box.addView(v)
    }

    val options = DecodeOptions(
        act,
        accessInfo,
        decodeEmoji = true,
        enlargeEmoji = imageScale,
        enlargeCustomEmoji = imageScale,
    )

    reactionSet?.forEachIndexed { index, reaction ->

        if (reaction.count <= 0) return@forEachIndexed

        val b = AppCompatButton(act).apply {
            layoutParams = FlexboxLayout.LayoutParams(
                FlexboxLayout.LayoutParams.WRAP_CONTENT,
                buttonHeight,
            ).apply {
                if (index > 0) startMargin = marginBetween
                bottomMargin = dip(3)
            }
            gravity = Gravity.CENTER
            minWidthCompat = buttonHeight

            background = if (reactionSet.isMyReaction(reaction)) {
                // 自分がリアクションしたやつは背景を変える
                getAdaptiveRippleDrawableRound(
                    act,
                    PrefI.ipButtonReactionedColor.value.notZero()
                        ?: act.attrColor(R.attr.colorButtonAccentReaction),
                    act.attrColor(R.attr.colorRippleEffect),
                    roundNormal = true
                )
            } else {
                ContextCompat.getDrawable(
                    act,
                    R.drawable.btn_bg_transparent_round6dp
                )
            }

            setTextColor(colorTextContent)
            setPadding(paddingH, 0, paddingH, 0)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, textHeight)
            allCaps = false
            tag = reaction
            setOnClickListener {
                val taggedReaction = it.tag as? TootReaction
                if (status.reactionSet?.isMyReaction(taggedReaction) == true) {
                    act.reactionRemove(column, status, taggedReaction)
                } else {
                    act.reactionAdd(column, status, taggedReaction?.name, taggedReaction?.staticUrl)
                }
            }
            setOnLongClickListener {
                val taggedReaction = it.tag as? TootReaction
                act.reactionFromAnotherAccount(
                    accessInfo,
                    statusShowing,
                    taggedReaction
                )
                true
            }
            // カスタム絵文字の場合、アニメーション等のコールバックを処理する必要がある
            val invalidator = NetworkEmojiInvalidator(act.handler, this)
            extraInvalidatorList.add(invalidator)
            try {
                val ssb =
                    reaction.toSpannableStringBuilder(options, status)
                        .also { it.append(" ${reaction.count}") }
                text = ssb
                invalidator.register(ssb)
            } catch (ex: Throwable) {
                log.e(ex, "can't decode reaction emoji.")
                text = "${reaction.name} ${reaction.count}"
            }
        }
        box.addView(b)
    }

    llExtra.addView(box)
}
