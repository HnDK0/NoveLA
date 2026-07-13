package my.noveldokusha.features.chapterslist

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import my.noveldokusha.coreui.BaseActivity
import my.noveldokusha.coreui.composableActions.SetSystemBarTransparent
import my.noveldokusha.coreui.composableActions.onDoAskForImage
import my.noveldokusha.coreui.theme.Theme
import my.noveldokusha.core.utils.Extra_String
import my.noveldokusha.navigation.NavigationRoutes
import my.noveldokusha.feature.local_database.BookMetadata
import javax.inject.Inject

@AndroidEntryPoint
class ChaptersActivity : BaseActivity() {
    class IntentData : Intent, ChapterStateBundle {
        override var rawBookUrl by Extra_String()
        override var bookTitle by Extra_String()

        constructor(intent: Intent) : super(intent)
        constructor(ctx: Context, bookMetadata: BookMetadata) : super(
            ctx,
            ChaptersActivity::class.java
        ) {
            this.rawBookUrl = bookMetadata.url
            this.bookTitle = bookMetadata.title
        }
    }

    @Inject
    internal lateinit var navigationRoutes: NavigationRoutes

    private val viewModel by viewModels<ChaptersViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val backPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }
        addOnBackPressedCallback(backPressedCallback)

        setContent {
            Theme(themeProvider = themeProvider) {
                SetSystemBarTransparent()
                ChaptersScreen(
                    state = viewModel.state,
                    onLibraryToggle = viewModel::toggleBookmark,
                    onSearchBookInDatabase = ::searchBookInDatabase,
                    onResumeReading = ::onOpenLastActiveChapter,
                    onPressBack = ::finish,
                    onSelectedDeleteDownloads = viewModel::deleteDownloadsSelected,
                    onSelectedDownload = viewModel::downloadSelected,
                    onSelectedSetRead = viewModel::setAsReadSelected,
                    onSelectedSetUnread = viewModel::setAsUnreadSelected,
                    onSelectedSetReadUpToChapterRead = viewModel::setAsReadUpToSelected,
                    onSelectedSetReadUpToChapterUnread = viewModel::setAsReadUpToUnSelected,
                    onSelectedInvertSelection = viewModel::invertSelection,
                    onSelectAllChapters = viewModel::selectAll,
                    onCloseSelectionBar = viewModel::unselectAll,
                    onChapterClick = { openBookAtChapter(chapterUrl = it.chapter.url) },
                    onChapterLongClick = viewModel::onChapterLongClick,
                    onSelectionModeChapterClick = viewModel::onSelectionModeChapterClick,
                    onSelectionModeChapterLongClick = viewModel::onSelectionModeChapterLongClick,
                    onChapterDownload = viewModel::onChapterDownload,
                    onPullRefresh = viewModel::onPullRefresh,
                    onCoverLongClick = { searchBookInDatabase(input = viewModel.bookTitle) },
                    onChangeCover = onDoAskForImage { viewModel.saveImageAsCover(it) },
                    onOpenInBrowser = { navigationRoutes.webView(this, url = it).let(::startActivity) },
                    onGlobalSearchClick = { navigationRoutes.globalSearch(this, text = it).let(::startActivity) },
                    onDownloadAllChapters = viewModel::downloadAllChapters,
                    onMigrateBook = {
                        navigationRoutes.novelMigration(
                            this,
                            bookUrl = viewModel.state.book.value.url,
                            bookTitle = viewModel.state.book.value.title
                        ).let(::startActivity)
                    },
                    onDeleteTranslations = viewModel::deleteTranslationsForBook,
                    onFixBook = viewModel::fixBook,
                    categories = viewModel::getCategories,
                    onUpdateCategory = viewModel::updateBookCategory,
                    translatedTitle = viewModel.translatedTitle.value,
                    translatedDescription = viewModel.translatedDescription.value,
                    isTranslatingInfo = viewModel.isTranslatingInfo.value,
                    onTranslateBookInfo = viewModel::translateBookInfo,
                    onClearBookInfoTranslation = viewModel::clearBookInfoTranslation,
                    scraper = viewModel.scraper,
                )
            }
        }
    }

    private fun onOpenLastActiveChapter() {
        lifecycleScope.launch {
            val lastReadChapter = viewModel.getLastReadChapter()
                ?: viewModel.state.chapters.minByOrNull { it.chapter.position }?.chapter?.url
                ?: return@launch

            openBookAtChapter(chapterUrl = lastReadChapter)
        }
    }

    private fun searchBookInDatabase(
        input: String = viewModel.state.book.value.title
    ) = navigationRoutes.databaseSearch(
        this,
        input = input
    ).let(::startActivity)

    private fun openBookAtChapter(chapterUrl: String) = navigationRoutes.reader(
        this, bookUrl = viewModel.state.book.value.url, chapterUrl = chapterUrl
    ).let(::startActivity)
}