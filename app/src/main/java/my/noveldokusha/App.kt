package my.noveldokusha

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.EntryPoints
import dagger.hilt.android.HiltAndroidApp
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.di.HiltAppEntryPoint
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.tooling.application_workers.setup.PeriodicWorkersInitializer
import timber.log.Timber
import javax.inject.Inject


@HiltAndroidApp
class App : Application(), ImageLoaderFactory, Configuration.Provider {

    @Inject
    lateinit var networkClient: NetworkClient

    @Inject
    lateinit var periodicWorkersInitializer: PeriodicWorkersInitializer

    override fun onCreate() {
        super.onCreate()

        val appPreferences = EntryPoints.get(this, HiltAppEntryPoint::class.java).appPreferences()
        var language = appPreferences.APP_LANGUAGE.value

        // If using default value (English) and this is first launch,
        // determine system language and set it
        if (language == AppLanguage.DEFAULT) {
            language = LocaleManager.getSystemLocale(this)
            appPreferences.APP_LANGUAGE.value = language
        }

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
        periodicWorkersInitializer.init()
    }

    override fun newImageLoader(): ImageLoader {
        val diskCache = coil.disk.DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(300L * 1024 * 1024) // 300 MB
            .build()

        return when (val networkClient = networkClient) {
            is ScraperNetworkClient -> ImageLoader
                .Builder(this)
                .okHttpClient(networkClient.client)
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .build()

            else -> ImageLoader
                .Builder(this)
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .build()
        }
    }

    // WorkManager — custom factory for @HiltWorker workers (LibraryUpdates, UpdatesChecker)
    override val workManagerConfiguration: Configuration by lazy {
        val appWorkerFactory = EntryPoints
            .get(this, HiltAppEntryPoint::class.java)
            .workerFactory()

        Configuration.Builder()
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) Log.DEBUG else Log.INFO)
            .setWorkerFactory(appWorkerFactory)
            .build()
    }
}