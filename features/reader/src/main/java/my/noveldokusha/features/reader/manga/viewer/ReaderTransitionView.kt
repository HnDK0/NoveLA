package my.noveldokusha.features.reader.manga.viewer

import android.content.Context
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Карточка перехода главы — порт tachiyomisy ReaderTransitionView без
 * Compose и DownloadManager: одна глава, данных о соседях нет.
 *
 * [Direction] задаёт сторону «подсматривания»: L2R — следующая глава
 * подъезжает справа (RIGHT), R2L — слева (LEFT), вертикальный пейджер —
 * снизу (BOTTOM).
 */
internal class ReaderTransitionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    enum class Direction { LEFT, RIGHT, BOTTOM }

    /** Тап по области ошибки — повторить (например, загрузку главы). */
    var onRetry: (() -> Unit)? = null

    /** Текущее направление (задаётся в [bind]). */
    val direction: Direction
        get() = storedDirection

    private var storedDirection: Direction = Direction.RIGHT

    private val titleView = TextView(context)
    private val messageView = TextView(context)
    private val progressBar = ProgressBar(context)

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER

        val padding = (16 * resources.displayMetrics.density).toInt()
        setPadding(padding, padding, padding, padding)

        progressBar.isIndeterminate = true
        progressBar.visibility = View.GONE

        titleView.textSize = 16f
        titleView.gravity = Gravity.CENTER
        titleView.setPadding(0, 0, 0, padding)

        messageView.textSize = 14f
        messageView.gravity = Gravity.CENTER
        messageView.setPadding(0, padding, 0, 0)
        messageView.isClickable = true
        messageView.setOnClickListener { onRetry?.invoke() }

        addView(progressBar)
        addView(titleView)
        addView(messageView)
    }

    fun bind(title: String?, direction: Direction) {
        storedDirection = direction
        titleView.text = title
        titleView.visibility = if (title.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    fun setLoading() {
        progressBar.visibility = View.VISIBLE
        messageView.visibility = View.VISIBLE
        messageView.text = "\u2026"
    }

    fun setError(message: String?) {
        progressBar.visibility = View.GONE
        messageView.visibility = View.VISIBLE
        messageView.text = message ?: "!"
    }
}
