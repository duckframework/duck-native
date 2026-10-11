package com.duckframework.bridge

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout

/**
 * A LinearLayout that never grows wider than `maxContentWidth`.
 *
 * Keeps forms readable on tablets and in landscape while still filling a phone.
 */
class MaxWidthLinearLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val maxWidthPx: Int

    init {
        val styled = context.obtainStyledAttributes(attrs, R.styleable.MaxWidthLinearLayout)
        maxWidthPx = styled.getDimensionPixelSize(
            R.styleable.MaxWidthLinearLayout_maxContentWidth,
            Int.MAX_VALUE,
        )
        styled.recycle()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // Clamp the width the parent offers before measuring children
        val offered = MeasureSpec.getSize(widthMeasureSpec)
        val spec = if (offered > maxWidthPx) {
            MeasureSpec.makeMeasureSpec(maxWidthPx, MeasureSpec.getMode(widthMeasureSpec))
        } else {
            widthMeasureSpec
        }
        super.onMeasure(spec, heightMeasureSpec)
    }
}
