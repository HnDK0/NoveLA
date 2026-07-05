package my.noveldokusha

import android.app.Application
import android.os.Build
import android.util.Log
import androidx.work.Configuration as WorkConfiguration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.BitmapFactoryDecoder
import dagger.hilt.EntryPoints
import dagger.hilt.android.HiltAndroidApp
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.core.appPreferences.AppLanguageProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.di.HiltAppEntryPoint
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.ScraperNetworkClient
import timber.log.Timber
import javax.inject.Inject
import java.util.Locale


@HiltAndroidApp
class App : Application(), ImageLoaderFactory, WorkConfiguration.Provider {

    @Inject
    lateinit var networkClient: NetworkClient

    // Eager singleton: форсирует создание DownloadManager при старте приложения,
    // чтобы restoreTasksFromDatabase() запустился сразу, а не при первом открытии книги.
    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onCreate() {
        super.onCreate()

        val appPreferences = EntryPoints.get(this, HiltAppEntryPoint::class.java).appPreferences()
        val language = resolveAppLanguage(appPreferences)
        LocaleManager.applyLocale(this, language)

        try {
            android.webkit.CookieManager.getInstance().apply {
                setAcceptCookie(true)
                flush()
            }
        } catch (e: Exception) {
            Timber.e(e, "CookieManager init failed")
        }

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    override fun newImageLoader(): ImageLoader {
        val diskCache = coil.disk.DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(300L * 1024 * 1024) // 300 MB
            .build()

        return when (val networkClient = networkClient) {
            is ScraperNetworkClient -> ImageLoader
                .Builder(this)
                .components { add(BitmapFactoryDecoder.Factory()) }
                .okHttpClient(networkClient.client)
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .build()

            else -> ImageLoader
                .Builder(this)
                .components { add(BitmapFactoryDecoder.Factory()) }
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .build()
        }
    }

    private fun resolveAppLanguage(appPreferences: AppPreferences): AppLanguage {
        if (appPreferences.IS_FOLLOW_SYSTEM_LANGUAGE.value || !appPreferences.IS_FIRST_LAUNCH_DONE.value) {
            val systemLocale = getSystemLocale()
            val detected = AppLanguageProvider.fromLocale(systemLocale)
            if (!appPreferences.IS_FIRST_LAUNCH_DONE.value) {
                appPreferences.APP_LANGUAGE_CODE.value = detected.code
                appPreferences.IS_FIRST_LAUNCH_DONE.value = true
            }
            return detected
        }
        return AppLanguageProvider.fromCode(appPreferences.APP_LANGUAGE_CODE.value)
            ?: AppLanguageProvider.supportedLanguages.first()
    }

    private fun getSystemLocale(): Locale {
        return if (Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            resources.configuration.locale
        }
    }

    // WorkManager — custom factory for @HiltWorker workers (LibraryUpdates, UpdatesChecker)
    override val workManagerConfiguration: WorkConfiguration by lazy {
        val appWorkerFactory = EntryPoints
            .get(this, HiltAppEntryPoint::class.java)
            .workerFactory()

        WorkConfiguration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .setWorkerFactory(appWorkerFactory)
            .build()
    }
}