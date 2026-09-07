package my.noveldokusha

import android.app.Application
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.work.Configuration as WorkConfiguration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.allowRgb565
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import okio.Path.Companion.toPath
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
class App : Application(), SingletonImageLoader.Factory, WorkConfiguration.Provider {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var networkClient: NetworkClient

    // Eager singleton: форсирует создание DownloadManager при старте приложения,
    // чтобы restoreTasksFromDatabase() запустился сразу, а не при первом открытии книги.
    @Inject
    lateinit var downloadManager: DownloadManager

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(LocaleManager.createAppLocaleContext(base))
    }

    override fun onCreate() {
        super.onCreate()

        val appPreferences = EntryPoints.get(this, HiltAppEntryPoint::class.java).appPreferences()
        resolveAppLanguage(appPreferences)

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

    override fun newImageLoader(context: Context): ImageLoader {
        val diskCache = coil3.disk.DiskCache.Builder()
            .directory("${context.cacheDir.absolutePath}/image_cache".toPath())
            .maxSizeBytes(100 * 1024 * 1024) // 100 MB
            .build()

        val memoryCache = coil3.memory.MemoryCache.Builder()
            .maxSizePercent(context, 0.25) // ponytail: 25% — стандарт для сетевых грид-приложений (Mihon=20%, Coil Sample=25%)
            .build()

        val animatorDurationScale = Settings.System.getFloat(
            contentResolver, Settings.System.ANIMATOR_DURATION_SCALE, 1f
        )

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRamDevice = activityManager.isLowRamDevice

        val sharedBuilder = ImageLoader.Builder(context)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .crossfade((300 * animatorDurationScale).toInt())
            .allowHardware(true)
            .allowRgb565(true) // RGB_565: 2 байта/пиксель вместо 4 — вдвое больше обложек в кеше

        return when (val networkClient = networkClient) {
            is ScraperNetworkClient -> sharedBuilder
                .components {
                    add(OkHttpNetworkFetcherFactory(callFactory = { networkClient.client }))
                }
                .build()

            else -> sharedBuilder.build()
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