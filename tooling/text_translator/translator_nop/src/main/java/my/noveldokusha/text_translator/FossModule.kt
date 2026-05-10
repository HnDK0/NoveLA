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

@InstallIn(SingletonComponent::class)
@Module
object FossModule {

    @Provides
    @Singleton
    fun provideTranslationManager(
        appCoroutineScope: AppCoroutineScope,
        appPreferences: AppPreferences,
        networkClient: ScraperNetworkClient
    ): TranslationManager = TranslationManagerComposite(
        coroutineScope    = appCoroutineScope,
        geminiManager     = TranslationManagerGemini(appCoroutineScope, appPreferences),
        googleFreeManager = TranslationManagerGoogleFree(appCoroutineScope),
        googlePAManager   = TranslationManagerGooglePA(appCoroutineScope, appPreferences, networkClient),
        openAiManager     = TranslationManagerOpenAI(appCoroutineScope, appPreferences),
        appPreferences    = appPreferences
    )
}
