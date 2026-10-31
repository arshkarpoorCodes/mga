package com.example.myboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

class MyBoardService : InputMethodService(), KeyboardView.Listener {

    private var board: KeyboardView? = null

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        board = v
        return v
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        when (info.inputType and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_PHONE -> board?.setLayer(Layer.SYM1)
            else -> board?.setLayer(Layer.LETTERS)
        }
        updateCaps()
    }

    override fun onText(text: String) {
        val ic = currentInputConnection ?: return
        // Double space -> ". "
        if (text == " ") {
            val before = ic.getTextBeforeCursor(2, 0)
            if (before != null && before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic.deleteSurroundingText(1, 0)
                ic.commitText(". ", 1)
                updateCaps()
                return
            }
        }
        ic.commitText(text, 1)
        updateCaps()
    }

    override fun onBackspace() {
        sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        updateCaps()
    }

    override fun onEnter() {
        val info = currentInputEditorInfo
        val ic = currentInputConnection ?: return
        if (info != null) {
            val action = info.imeOptions and EditorInfo.IME_MASK_ACTION
            val noEnterAction = info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
            if (!noEnterAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
                return
            }
        }
        sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }

    /** Auto-capitalize at start of sentences, like the stock keyboard. */
    private fun updateCaps() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo ?: return
        val caps = if (info.inputType != 0) ic.getCursorCapsMode(info.inputType) else 0
        board?.autoShift(caps != 0)
    }
}
