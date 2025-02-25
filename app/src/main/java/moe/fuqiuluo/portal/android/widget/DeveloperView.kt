@file:Suppress("OVERRIDE_DEPRECATION")
package moe.fuqiuluo.portal.android.widget

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.CollapsibleActionView
import androidx.appcompat.widget.LinearLayoutCompat

class DeveloperView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayoutCompat(context, attrs, defStyleAttr), CollapsibleActionView {

    override fun requestFocus(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        if (!isFocusable) return false
        return super.requestFocus(direction, previouslyFocusedRect)
    }

    override fun onActionViewExpanded() {
        visibility = VISIBLE
        invalidate()
        requestLayout()
        Log.d("DeveloperView", "onActionViewExpanded")
    }

    override fun onActionViewCollapsed() {
        visibility = GONE
        invalidate()
        requestLayout()
        Log.d("DeveloperView", "onActionViewCollapsed")
    }
}