package my.noveldokusha.databaseexplorer.databaseBookInfo

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.ViewModel
import my.noveldokusha.core.appPreferences.TranslationSettingsResolver
import my.noveldokusha.core.utils.StateExtra_String
import my.noveldokusha.feature.local_database.DAOs.BookTranslationDao
import my.noveldokusha.scraper.DatabaseInterface
import my.noveldokusha.scraper.Scraper
import my.noveldokusha.feature.local_database.BookMetadata
import timber.log.Timber
import javax.inject.Inject

interface DatabaseBookInfoStateBundle {
    val bookMetadata
        get() = BookMetadata(
            title = bookTitle,
            url = bookUrl
        )

    var databaseUrlBase: String
    var bookUrl: String
    var bookTitle: String
}


@HiltViewModel
class DatabaseBookInfoViewModel @Inject constructor(
    stateHandle: SavedStateHandle,
    scraper: Scraper,
    private val bookTranslationDao: BookTranslationDao,
    private val translationSettingsResolver: TranslationSettingsResolver,
) : ViewModel(), DatabaseBookInfoStateBundle {
    override var databaseUrlBase: String by StateExtra_String(stateHandle)
    override var bookUrl: String by StateExtra_String(stateHandle)
    override var bookTitle: String by StateExtra_String(stateHandle)

    val database = requireNotNull(scraper.getCompatibleDatabase(databaseUrlBase)) {
        "No compatible database for base URL: $databaseUrlBase"
    }

    val translatedTitle = mutableStateOf<String?>(null)
    val translatedDescription = mutableStateOf<String?>(null)

    internal val state = DatabaseBookInfoState(
        databaseNameStrId = mutableIntStateOf(database.nameStrId),
        book = mutableStateOf(
            DatabaseInterface.BookData(
                title = bookTitle,
                description = "",
                coverImageUrl = null,
                alternativeTitles = listOf(),
                authors = listOf(),
                tags = listOf(),
                genres = listOf(),
                bookType = "",
                relatedBooks = listOf(),
                similarRecommended = listOf()
            )
        )
    )

    init {
        viewModelScope.launch {
            database.getBookData(bookMetadata.url)
                .onSuccess { bookData ->
                    state.book.value = bookData

                    // Читаем перевод из БД реактивно, если он есть (пользователь ранее
                    // переводил эту книгу). translatedTitle.ifBlank{} — fallback
                    // на оригинальное название. Подписка на поток: если перевод
                    // отредактируют/очистят при открытом экране — состояние обновится
                    // без перезахода.
                    val pair = translationSettingsResolver.translationPairForBook(bookUrl)
                    val sourceLang = pair.source
                    val targetLang = pair.target
                    if (targetLang.isBlank()) return@onSuccess

                    bookTranslationDao.getTranslatedBookFlow(bookUrl, targetLang)
                        .collect { rows ->
                            // Строка с точным source-языком; если её нет — показываем оригинал.
                            val row = rows.firstOrNull { it.sourceLang == sourceLang }
                            translatedTitle.value = row?.titleTranslation
                                ?.takeIf { it.isNotBlank() }
                                ?: bookData.title.ifBlank { null }
                            translatedDescription.value = row?.descriptionTranslation
                                ?.takeIf { it.isNotBlank() }
                                ?: bookData.description.ifBlank { null }
                        }
                }
                .onError { Timber.d(it.exception) }
        }
    }
}