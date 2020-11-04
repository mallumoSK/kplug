package tk.mallumo.cordova.kplug

import android.content.Context
import android.util.DisplayMetrics
import android.view.View
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

abstract class BottomDialog(val context: Context) {

    private val mBottomSheetBehaviorCallback = object : BottomSheetBehavior.BottomSheetCallback() {

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_HIDDEN) {
                dismiss()
            }

        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {}
    }

    @Suppress("unused")
    val isShowing: Boolean
        get() = dialog?.isShowing == true

    private  var dialog: BottomSheetDialog? = null

    abstract fun getContentView(): View


    open fun show() {

        dialog = BottomSheetDialog(context, dialogTheme()).apply {
            setOnDismissListener {
                onDismiss()
            }
        }
        getContentView().also { contentView ->
            dialog!!.setContentView(contentView)
            contentView.layoutParams.height = context.resources.displayMetrics.heightPixels
            BottomSheetBehavior.from(contentView.parent as View).also {
                it.addBottomSheetCallback(mBottomSheetBehaviorCallback)
                it.isDraggable = false
                it.isHideable = false
//                it.state = BottomSheetBehavior.STATE_EXPANDED
                it.setPeekHeight(peekHeight())
            }
        }
        dialog!!.show()
    }
    open fun onDismiss(){

    }
    open fun dismiss() {
        dialog?.dismiss()
        dialog = null
    }

    open fun peekHeight(): Int = context.dm.heightPixels - context.dm.heightPixels / 4

    open fun dialogTheme(): Int = 0


}

