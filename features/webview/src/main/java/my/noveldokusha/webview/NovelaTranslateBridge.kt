package my.noveldokusha.webview

import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import my.noveldokusha.text_translator.domain.TranslationManager
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Мост JS→Kotlin→JS для перевода страницы WebView.
 *
 * JS-обвязка страницы вызывает [translateAsync] через интерфейс
 * «NovelaTranslate», регистрируемый `addJavascriptInterface` в WebViewActivity:
 * тексты батча приходят сериализованным JSON-массивом, перевод
 * выполняется существующим [TranslationManager.translateBatch] активным
 * провайдером из TRANSLATION_PROVIDER (источник «auto» резолвится внутри
 * composite; systemPromptOverride всегда null — провайдер не форсируется),
 * результат отдаётся обратно в JS вызовом
 * `window.__novelaPageTranslator.applyResult(batchId, jsonArg)`.
 *
 * Побочный эффект очистки U+2028/U+2029: org.json не экранирует эти символы,
 * поэтому сериализованный JSON (включая КЛЮЧИ — исходные тексты) перед
 * интерполяцией в JS-литерал прогоняется через `replace("\u2028", " ")` /
 * `replace("\u2029", " ")`. Узел, чей текст содержит U+2028/U+2029, не найдёт
 * себя в результатах по ключу (ключ изменён) и останется непереведённым —
 * символ крайне редок, приемлемо.
 *
 * Конкурентность: вызовы [TranslationManager.translateBatch] сериализуются
 * семафором [Semaphore] (2 разрешения) внутри моста — таймаут-продвижение
 * JS-конвейера не создаёт шторма параллельных вызовов провайдера
 * (429/квота): конкуренция ограничена, stall-breaker сохраняется.
 *
 * @param onError колбэк уведомления пользователя об ошибке перевода батча (например, toast).
 */
class NovelaTranslateBridge(
    private val webView: WebView,
    private val translationManager: TranslationManager,
    private val lifecycleScope: CoroutineScope,
    private val onError: (String) -> Unit = {},
) {
    /**
     * Лимит параллельных вызовов [TranslationManager.translateBatch].
     */
    private val translationSemaphore = Semaphore(2)

    // Гейт моста: перевод разрешён только после клика пользователя (setActive(true)
    // в onTranslateClicked). Ограничение проектирования: гейт закрывает лишь фазу «до клика» —
    // пока active=true, произвольные скрипты страницы могут дёргать translateAsync
    // (единственный ограничитель во время перевода — семафор).
    @Volatile
    private var active = false

    /**
     * Открывает/закрывает гейт моста из WebViewActivity
     * (клик Translate/Original и onPageFinished).
     */
    fun setActive(active: Boolean) {
        this.active = active
    }

    /**
     * Переводит батч текстов, присланный JS-обвязкой страницы, и возвращает
     * результат в JS. Вызывается WebView из фонового потока JS-моста.
     *
     * @param batchId идентификатор батча JS-конвейера, возвращается в applyResult.
     * @param targetLang целевой язык из белого списка GOOGLE_TRANSLATE_LANGUAGES.
     * @param jsonTexts JSON-массив исходных текстов (org.json, встроен в Android SDK).
     */
    @JavascriptInterface
    fun translateAsync(batchId: Int, targetLang: String, jsonTexts: String) {
        // Гейт: до клика пользователя страница не может запустить перевод
        // (произвольные скрипты не жгут квоту/ключи провайдера).
        if (!active) return
        val jsonArray = JSONArray(jsonTexts)
        val texts = List(jsonArray.length()) { i -> jsonArray.getString(i) }
        lifecycleScope.launch {
            runCatching {
                translationSemaphore.withPermit {
                    translationManager.translateBatch(texts, "auto", targetLang, null)
                }
            }
                .onSuccess { translations ->
                    val jsonObject = JSONObject()
                    translations.forEach { (original, translated) ->
                        jsonObject.put(original, translated)
                    }
                    // org.json не экранирует U+2028/U+2029 — теоретический разрыв
                    // JS-литерала на редком контенте; замена затрагивает и ключи (см. KDoc).
                    val jsonArg = jsonObject.toString()
                        .replace("\u2028", " ")
                        .replace("\u2029", " ")
                    // Только Kotlin-шаблонная интерполяция: String.format сломал бы
                    // jsonArg на %/кавычках. evaluateJavascript — только через post
                    // (главный поток). Гонка с onDestroy закрыта try/catch: API
                    // WebView.isDestroyed() отсутствует в compile-stubs SDK 27-37
                    // (проверено javap по android.jar) — вызов методов на
                    // уничтоженном WebView бросает IllegalStateException, краха нет.
                    webView.post {
                        try {
                            webView.evaluateJavascript(
                                "window.__novelaPageTranslator.applyResult($batchId, $jsonArg)",
                                null
                            )
                        } catch (e: Exception) {
                            Timber.w(e, "WebView уничтожен до доставки результата перевода")
                        }
                    }
                }
                .onFailure { e ->
                    // Отмена корутины не глотается — toast не показываем.
                    if (e is CancellationException) throw e
                    // Отсечка по флагу гейта: отказ батча устаревшей генерации
                    // (перевод уже остановлен — нажат Original / новый документ) — без toast.
                    if (active) onError("Translation failed")
                    // При исключении ничего не отправляем — JS оставляет оригиналы батча.
                    Timber.w(e, "Ошибка перевода батча")
                }
        }
    }
}
