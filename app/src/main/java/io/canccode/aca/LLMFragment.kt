val splitter = view.findViewById<View>(R.id.splitter)
var initialY = 0f
var initialEditorWeight = 2f
var initialChatWeight = 1f

splitter.setOnTouchListener { _, event ->
    val parentLayout = splitter.parent as LinearLayout
    when (event.action) {
        android.view.MotionEvent.ACTION_DOWN -> {
            initialY = event.rawY
            initialEditorWeight = (parentLayout.getChildAt(0) as FrameLayout).layoutParams as LinearLayout.LayoutParams
            true
        }
        android.view.MotionEvent.ACTION_MOVE -> {
            val dy = event.rawY - initialY
            val totalWeight = 3f // editor + chat initial weights
            val editorLp = parentLayout.getChildAt(0).layoutParams as LinearLayout.LayoutParams
            val chatLp = parentLayout.getChildAt(2).layoutParams as LinearLayout.LayoutParams

            val heightPx = parentLayout.height.toFloat()
            val deltaWeight = dy / heightPx * totalWeight

            editorLp.weight = (editorLp.weight + deltaWeight).coerceIn(0.2f, totalWeight - 0.2f)
            chatLp.weight = totalWeight - editorLp.weight

            parentLayout.getChildAt(0).layoutParams = editorLp
            parentLayout.getChildAt(2).layoutParams = chatLp
            true
        }
        else -> false
    }
}