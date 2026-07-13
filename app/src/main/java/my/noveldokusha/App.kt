package my.noveldokusha

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.work.Configuration as WorkConfiguration
import coil.ImageLoader
import coil.ImageLoaderFactory
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.core.appPreferences.AppLanguageProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.di.HiltAppEntryPoint
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.debug.MemoryDiagnostics
import timber.log.Timber
import javax.inject.Inject
import java.util.Locale


@HiltAndroidApp
class App : Application(), ImageLoaderFactory, WorkConfiguration.Provider {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var networkClient: NetworkClient

    // Eager singleton: форсирует создание DownloadManager при старте приложения,
    // чтобы restoreTasksFromDatabase() запустился сразу, а не при первом открытии книги.
    @Inject
    lateinit var downloadManager: DownloadManager

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        val prefs = base.getSharedPreferences(base.packageName + "_preferences", Context.MODE_PRIVATE)
        val code = prefs.getString("APP_LANGUAGE_CODE", "en") ?: "en"
        val language = AppLanguageProvider.fromCode(code)
            ?: AppLanguageProvider.supportedLanguages.first()
        super.attachBaseContext(LocaleManager.createLocaleContext(base, language))
    }

    override fun onCreate() {
        super.onCreate()

        val appPreferences = EntryPoints.get(this, HiltAppEntryPoint::class.java).appPreferences()
        resolveAppLanguage(appPreferences)

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
            MemoryDiagnostics.logMemoryStats()
            applicationScope.launch {
                delay(30_000)
                while (true) {
                    MemoryDiagnostics.logMemoryStats()
                    delay(60_000)
                }
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        val diskCache = coil.disk.DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(100 * 1024 * 1024) // 100 MB
            .build()

        val memoryCache = coil.memory.MemoryCache.Builder(this)
            .maxSizeBytes(64 * 1024 * 1024) // 64 MB
            .build()

        return when (val networkClient = networkClient) {
            is ScraperNetworkClient -> ImageLoader
                .Builder(this)
                .dispatcher(Dispatchers.IO.limitedParallelism(4))
                .memoryCache(memoryCache)
                .okHttpClient(networkClient.client)
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .addLastModifiedToFileCacheKey(true)
                .build()

            else -> ImageLoader
                .Builder(this)
                .dispatcher(Dispatchers.IO.limitedParallelism(4))
                .memoryCache(memoryCache)
                .diskCache(diskCache)
                .diskCachePolicy(coil.request.CachePolicy.ENABLED)
                .respectCacheHeaders(false)
                .addLastModifiedToFileCacheKey(true)
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