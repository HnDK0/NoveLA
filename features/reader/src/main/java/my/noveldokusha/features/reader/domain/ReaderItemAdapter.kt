package my.noveldokusha.features.reader.domain

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.doOnNextLayout
import androidx.core.view.updateLayoutParams
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import my.noveldokusha.core.AppFileResolver
import my.noveldokusha.core.utils.inflater
import my.noveldokusha.features.reader.features.TextSynthesis
import my.noveldokusha.reader.R
import my.noveldokusha.reader.databinding.ActivityReaderListItemBodyBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemDividerBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemErrorBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemGoogleTranslateAttributionBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemImageBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemPaddingBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemProgressBarBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemSpecialTitleBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemTitleBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemTranslateAttributionBinding
import my.noveldokusha.reader.databinding.ActivityReaderListItemTranslatingBinding
import my.noveldokusha.text_to_speech.Utterance

internal class ReaderItemAdapter(
    private val ctx: Context,
    list: List<ReaderItem>,
    private val bookUrl: String,
    private val currentSpeakerActiveItem: () -> TextSynthesis,
    private val currentTextSelectability: () -> Boolean,
    private val currentFontSize: () -> Float,
    private val currentLineHeight: () -> Float,
    private val currentParagraphSpacing: () -> Float,
    private val currentTypeface: () -> Typeface,
    private val currentTypefaceBold: () -> Typeface,
    private val currentParallelEnabled: () -> Boolean,
    private val currentParallelOrder: () -> String,
    private val onChapterStartVisible: (chapterUrl: String) -> Unit,
    private val onChapterEndVisible: (chapterUrl: String) -> Unit,
    private val onReloadReader: () -> Unit,
    private val onRetryChapter: (chapterIndex: Int) -> Unit,
    private val onOpenChapterInBrowser: (url: String) -> Unit,
    private val onClick: () -> Unit,
) : ArrayAdapter<ReaderItem>(ctx, 0, list) {
    private val appFileResolver = AppFileResolver(ctx)
    override fun getCount() = super.getCount() + 2
    override fun getItem(position: Int): ReaderItem = when (position) {
        0 -> topPadding
        count - 1 -> bottomPadding
        else -> super.getItem(position - 1)!!
    }

    fun getFirstVisibleItemIndexGivenPosition(firstVisiblePosition: Int): Int =
        when (firstVisiblePosition) {
            in 1 until (count - 1) -> firstVisiblePosition - 1
            0 -> 0
            count - 1 -> count - 1
            else -> -1
        }

    fun fromPositionToIndex(position: Int): Int = when (position) {
        in 1 until (count - 1) -> position - 1
        else -> -1
    }

    fun fromIndexToPosition(index: ItemIndex): Int = when (index) {
        in 0 until super.getCount() -> index + 1
        else -> -1
    }

    private val topPadding = ReaderItem.Padding(chapterIndex = Int.MIN_VALUE)
    private val bottomPadding = ReaderItem.Padding(chapterIndex = Int.MAX_VALUE)

    override fun hasStableIds(): Boolean = true

    override fun getItemId(position: Int): Long {
        if (position == 0) return Long.MIN_VALUE
        if (position == count - 1) return Long.MAX_VALUE
        val item = super.getItem(position - 1)!!
        return when (item) {
            is ReaderItem.Position -> (item.chapterIndex.toLong() shl 32) or (item.chapterItemPosition.toLong() and 0xFFFFFFFFL)
            is ReaderItem.BookStart -> (item.chapterIndex.toLong() shl 32) or 1L
            is ReaderItem.BookEnd -> (item.chapterIndex.toLong() shl 32) or 2L
            is ReaderItem.Divider -> (item.chapterIndex.toLong() shl 32) or 3L
            is ReaderItem.Progressbar -> (item.chapterIndex.toLong() shl 32) or 4L
            is ReaderItem.Translating -> (item.chapterIndex.toLong() shl 32) or 5L
            is ReaderItem.Error -> (item.chapterIndex.toLong() shl 32) or 6L
            is ReaderItem.GoogleTranslateAttribution -> (item.chapterIndex.toLong() shl 32) or 7L
            is ReaderItem.TranslateAttribution -> (item.chapterIndex.toLong() shl 32) or 8L
            else -> item.chapterIndex.toLong()
        }
    }

    override fun getViewTypeCount(): Int = 12
    override fun getItemViewType(position: Int) = when (getItem(position)) {
        is ReaderItem.Body -> 0
        is ReaderItem.Image -> 1
        is ReaderItem.BookEnd -> 2
        is ReaderItem.BookStart -> 3
        is ReaderItem.Divider -> 4
        is ReaderItem.Error -> 5
        is ReaderItem.Padding -> 6
        is ReaderItem.Progressbar -> 7
        is ReaderItem.Title -> 8
        is ReaderItem.Translating -> 9
        is ReaderItem.GoogleTranslateAttribution -> 10
        is ReaderItem.TranslateAttribution -> 11
    }

    private fun viewTranslateAttribution(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemGoogleTranslateAttributionBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemGoogleTranslateAttributionBinding.bind(convertView)
        }
        return bind.root
    }

    private fun viewTranslateAttributionNew(item: ReaderItem.TranslateAttribution, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemTranslateAttributionBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemTranslateAttributionBinding.bind(convertView)
        }
        bind.attributionText.text = when (item.provider) {
            "gemini" -> "Powered by Gemini"
            else -> "Powered by Google Translate"
        }
        return bind.root
    }

    private fun viewBody(item: ReaderItem.Body, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemBodyBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemBodyBinding.bind(convertView)
        }

        val parallelEnabled = currentParallelEnabled() && item.textTranslated != null

        if (parallelEnabled) {
            val orderTranslationFirst = currentParallelOrder() == "TRANSLATION_FIRST"

            val primaryText = if (orderTranslationFirst) item.textTranslated ?: item.text else item.text
            val secondaryText = if (orderTranslationFirst) item.text else item.textTranslated ?: item.text

            bind.bodyTranslated.text = primaryText
            bind.bodyTranslated.textSize = currentFontSize()
            bind.bodyTranslated.typeface = currentTypeface()
            bind.bodyTranslated.updateTextSelectability()
            bind.bodyTranslated.setLineSpacing(0f, currentLineHeight())

            bind.bodyOriginal.text = secondaryText
            bind.bodyOriginal.textSize = currentFontSize() * 0.85f
            bind.bodyOriginal.typeface = currentTypeface()
            bind.bodyOriginal.updateTextSelectability()
            bind.bodyOriginal.setLineSpacing(0f, currentLineHeight())
            bind.bodyOriginal.visibility = View.VISIBLE
        } else {
            bind.bodyTranslated.text = item.textToDisplay
            bind.bodyTranslated.textSize = currentFontSize()
            bind.bodyTranslated.typeface = currentTypeface()
            bind.bodyTranslated.updateTextSelectability()
            bind.bodyTranslated.setLineSpacing(0f, currentLineHeight())

            bind.bodyOriginal.visibility = View.GONE
        }

        bind.root.background = getItemReadingStateBackground(item)
        val paddingVertical = android.util.TypedValue.applyDimension(
            android.util.TypedValue.COMPLEX_UNIT_DIP,
            currentParagraphSpacing(),
            ctx.resources.displayMetrics
        ).toInt()
        bind.bodyTranslated.setPadding(bind.bodyTranslated.paddingLeft, paddingVertical, bind.bodyTranslated.paddingRight, paddingVertical)
        bind.bodyOriginal.setPadding(bind.bodyOriginal.paddingLeft, paddingVertical, bind.bodyOriginal.paddingRight, paddingVertical)

        when (item.location) {
            ReaderItem.Location.FIRST -> onChapterStartVisible(item.chapterUrl)
            ReaderItem.Location.LAST -> onChapterEndVisible(item.chapterUrl)
            else -> run {}
        }
        return bind.root
    }

    private fun viewImage(item: ReaderItem.Image, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemImageBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemImageBinding.bind(convertView)
        }

        bind.image.updateLayoutParams<ConstraintLayout.LayoutParams> {
            dimensionRatio = "1:${item.image.yrel}"
        }

        val imageModel = appFileResolver.resolvedBookImagePath(bookUrl = bookUrl, imagePath = item.image.path)

        bind.imageContainer.doOnNextLayout {
            Glide.with(ctx)
                .load(imageModel)
                .fitCenter()
                .error(R.drawable.ic_baseline_error_outline_24)
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(bind.image)
        }

        when (item.location) {
            ReaderItem.Location.FIRST -> onChapterStartVisible(item.chapterUrl)
            ReaderItem.Location.LAST -> onChapterEndVisible(item.chapterUrl)
            else -> run {}
        }
        return bind.root
    }

    private fun viewBookEnd(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemSpecialTitleBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemSpecialTitleBinding.bind(convertView)
        }
        bind.specialTitle.updateTextSelectability()
        bind.specialTitle.text = ctx.getString(R.string.reader_no_more_chapters)
        bind.specialTitle.typeface = currentTypefaceBold()
        return bind.root
    }

    private fun viewBookStart(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemSpecialTitleBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemSpecialTitleBinding.bind(convertView)
        }
        bind.specialTitle.updateTextSelectability()
        bind.specialTitle.text = ctx.getString(R.string.reader_first_chapter)
        bind.specialTitle.typeface = currentTypefaceBold()
        return bind.root
    }

    private fun viewProgressbar(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemProgressBarBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemProgressBarBinding.bind(convertView)
        }
        return bind.root
    }

    private fun viewTranslating(item: ReaderItem.Translating, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemTranslatingBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemTranslatingBinding.bind(convertView)
        }
        bind.text.text = context.getString(R.string.translating_from_lang_a_to_lang_b, item.sourceLang, item.targetLang)
        return bind.root
    }

    private fun viewDivider(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemDividerBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemDividerBinding.bind(convertView)
        }
        return bind.root
    }

    private fun viewError(item: ReaderItem.Error, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemErrorBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemErrorBinding.bind(convertView)
        }
        bind.error.updateTextSelectability()
        bind.error.text = item.text
        bind.reloadButton.setOnClickListener { onRetryChapter(item.chapterIndex) }
        if (item.chapterUrl.isNotBlank()) {
            bind.openInBrowserButton.visibility = View.VISIBLE
            bind.openInBrowserButton.setOnClickListener { onOpenChapterInBrowser(item.chapterUrl) }
        } else {
            bind.openInBrowserButton.visibility = View.GONE
        }
        return bind.root
    }

    private fun viewPadding(convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemPaddingBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemPaddingBinding.bind(convertView)
        }
        return bind.root
    }

    private fun viewTitle(item: ReaderItem.Title, convertView: View?, parent: ViewGroup): View {
        val bind = when (convertView) {
            null -> ActivityReaderListItemTitleBinding.inflate(parent.inflater, parent, false).also { it.root.tag = it }
            else -> ActivityReaderListItemTitleBinding.bind(convertView)
        }
        bind.titleTranslated.updateTextSelectability()
        bind.root.background = getItemReadingStateBackground(item)
        bind.titleTranslated.text = item.textToDisplay
        bind.titleTranslated.typeface = currentTypefaceBold()
        bind.titleOriginal.visibility = View.GONE
        return bind.root
    }

    private val currentReadingAloudDrawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.translucent_current_reading_text_background)
    }

    private val currentReadingAloudLoadingDrawable by lazy {
        AppCompatResources.getDrawable(context, R.drawable.translucent_current_reading_loading_text_background)
    }

    private fun TextView.updateTextSelectability() {
        val selectableText = currentTextSelectability()
        setTextIsSelectable(selectableText)
        if (selectableText) {
            // Добавляем пункт "Поиск в браузере" в меню выделения текста
            if (customSelectionActionModeCallback == null) {
                customSelectionActionModeCallback = object : android.view.ActionMode.Callback {
                    override fun onCreateActionMode(mode: android.view.ActionMode, menu: android.view.Menu): Boolean {
                        menu.add(0, MENU_ID_SEARCH_WEB, 0, ctx.getString(R.string.search_web))
                            .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER)
                        return true
                    }
                    override fun onPrepareActionMode(mode: android.view.ActionMode, menu: android.view.Menu) = false
                    override fun onActionItemClicked(mode: android.view.ActionMode, item: android.view.MenuItem): Boolean {
                        if (item.itemId == MENU_ID_SEARCH_WEB) {
                            val start = selectionStart.coerceAtLeast(0)
                            val end = selectionEnd.coerceAtLeast(0)
                            val selected = text.substring(minOf(start, end), maxOf(start, end))
                            if (selected.isNotBlank()) {
                                val intent = Intent(Intent.ACTION_WEB_SEARCH).apply {
                                    putExtra(android.app.SearchManager.QUERY, selected)
                                }
                                ctx.startActivity(intent)
                            }
                            mode.finish()
                            return true
                        }
                        return false
                    }
                    override fun onDestroyActionMode(mode: android.view.ActionMode) {}
                }
            }
            setTextSelectionAwareClick { onClick() }
        } else {
            customSelectionActionModeCallback = null
            setOnClickListener { onClick() }
            setOnTouchListener(null)
        }
    }

    private fun getItemReadingStateBackground(item: ReaderItem): Drawable? {
        val textSynthesis = currentSpeakerActiveItem()
        val isReadingItem = item is ReaderItem.Position &&
                textSynthesis.itemPos.chapterIndex == item.chapterIndex &&
                textSynthesis.itemPos.chapterItemPosition == item.chapterItemPosition

        if (!isReadingItem) return null

        return when (textSynthesis.playState) {
            Utterance.PlayState.PLAYING -> currentReadingAloudDrawable
            Utterance.PlayState.LOADING -> currentReadingAloudLoadingDrawable
            Utterance.PlayState.FINISHED -> null
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
        when (val item = getItem(position)) {
            is ReaderItem.GoogleTranslateAttribution -> viewTranslateAttribution(convertView, parent)
            is ReaderItem.TranslateAttribution -> viewTranslateAttributionNew(item, convertView, parent)
            is ReaderItem.Body -> viewBody(item, convertView, parent)
            is ReaderItem.Image -> viewImage(item, convertView, parent)
            is ReaderItem.BookEnd -> viewBookEnd(convertView, parent)
            is ReaderItem.BookStart -> viewBookStart(convertView, parent)
            is ReaderItem.Divider -> viewDivider(convertView, parent)
            is ReaderItem.Error -> viewError(item, convertView, parent)
            is ReaderItem.Padding -> viewPadding(convertView, parent)
            is ReaderItem.Progressbar -> viewProgressbar(convertView, parent)
            is ReaderItem.Translating -> viewTranslating(item, convertView, parent)
            is ReaderItem.Title -> viewTitle(item, convertView, parent)
        }

    private fun View.setTextSelectionAwareClick(action: () -> Unit) {
        setOnClickListener { action() }
        setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP && !this.isFocused) {
                performClick()
            }
            false
        }
    }

    companion object {
        private const val MENU_ID_SEARCH_WEB = 9999
    }
}