// Полный заменённый FossModule.kt

package my.noveldokusha.text_translator

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.text_translator.domain.TranslationManager
import my.noveldokusha.text_translator.TranslationManagerComposite
import my.noveldokusha.text_translator.TranslationManagerGemini
import my.noveldokusha.text_translator.TranslationManagerGoogleFree
import my.noveldokusha.text_translator.TranslationManagerGooglePA
import my.noveldokusha.text_translator.TranslationManagerOpenAI
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
    ): TranslationManager {
        val geminiManager    = TranslationManagerGemini(appCoroutineScope, appPreferences)
        val googleFreeManager= TranslationManagerGoogleFree(appCoroutineScope, appPreferences, networkClient)
        val googlePAManager  = TranslationManagerGooglePA(appCoroutineScope, appPreferences, networkClient)
        val openAiManager    = TranslationManagerOpenAI(appCoroutineScope, appPreferences)

        return TranslationManagerComposite(
            coroutineScope    = appCoroutineScope,
            geminiManager     = geminiManager,
            googleFreeManager = googleFreeManager,
            googlePAManager   = googlePAManager,
            openAiManager     = openAiManager,
            appPreferences    = appPreferences
        )
    }
}