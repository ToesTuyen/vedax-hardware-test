package vn.vedax.hardwaretest.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/** Small shared view factory; device and update logic never live here. */
class UiKit(private val context: Context) {
    fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun gradient(start: Int, end: Int, radius: Int): GradientDrawable =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(start, end)).apply {
            cornerRadius = radius.toFloat()
            if (radius > 0) setStroke(dp(1), 0x90ffffff.toInt())
        }

    fun rounded(color: Int, radius: Int): GradientDrawable = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    fun label(value: String, size: Int, bold: Boolean = false, color: Int = 0xff1b2836.toInt()): TextView =
        TextView(context).apply {
            text = value
            textSize = size.toFloat()
            setTextColor(color)
            if (bold) typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setPadding(0, dp(5), 0, dp(5))
        }

    fun row(parent: LinearLayout): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(8)
            bottomMargin = dp(4)
        })
    }

    fun card(parent: LinearLayout, title: String, description: String): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(18))
            background = gradient(0xf5ffffff.toInt(), 0xe9eef6ff.toInt(), dp(26))
            elevation = dp(4).toFloat()
            parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })
            addView(label(title, 21, true, 0xff183c68.toInt()))
            addView(label(description, 13, color = 0xff6b819d.toInt()).apply {
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
            })
        }

    fun status(value: String, highlight: Boolean = false): TextView = label(value, 14).apply {
        background = rounded(if (highlight) 0xffe4eefb.toInt() else 0xffeef4fb.toInt(), dp(14))
        setPadding(dp(12), dp(10), dp(12), dp(10))
    }

    fun button(parent: LinearLayout, text: String, action: () -> Unit): Button = Button(context).apply {
        this.text = text
        isAllCaps = false
        textSize = 13f
        setTextColor(Color.WHITE)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = gradient(0xff5277b2.toInt(), 0xff254b88.toInt(), dp(22))
        elevation = dp(2).toFloat()
        setPadding(dp(11), dp(5), dp(11), dp(5))
        setOnClickListener { action() }
        val params = if (parent.orientation == LinearLayout.HORIZONTAL) {
            LinearLayout.LayoutParams(0, dp(48), 1f).apply { rightMargin = dp(5) }
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
                topMargin = dp(10)
                bottomMargin = dp(7)
            }
        }
        parent.addView(this, params)
    }

    fun footnote(value: String): TextView = label(value, 12, color = 0xff7289a6.toInt())
}
