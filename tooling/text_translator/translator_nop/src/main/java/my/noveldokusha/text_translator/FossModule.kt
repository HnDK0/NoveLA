package my.noveldokusha.text_translator

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.text_translator.domain.TranslationManager
import javax.inject.Singleton

/**
 * Hilt module that wires the translation subsystem for the FOSS build flavour.
 *
 * [TranslationManagerComposite] acts as the single [TranslationManager] entry-point
 * and routes each request to the appropriate backend based on the user's preference:
 *   • Google PA  (default)
 *   • Google Free
 *   • Gemini
 *   • OpenAI-compatible
 */
@InstallIn(SingletonComponent::class)
@Module
object FossModule {

    @Provides
    @Singleton
    fun provideTranslationManager(
        appCoroutineScope: AppCoroutineScope,
        appPreferences: AppPreferences,
        networkClient: ScraperNetworkClient,
    ): TranslationManager = TranslationManagerComposite(
        coroutineScope    = appCoroutineScope,
        geminiManager     = TranslationManagerGemini(appCoroutineScope, appPreferences),
        googleFreeManager = TranslationManagerGoogleFree(appCoroutineScope),
        googlePAManager   = TranslationManagerGooglePA(appCoroutineScope, appPreferences, networkClient),
        openAiManager     = TranslationManagerOpenAI(appCoroutineScope, appPreferences),
        appPreferences    = appPreferences,
    )
}
