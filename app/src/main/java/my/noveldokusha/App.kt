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
import coil3.annotation.ExperimentalCoilApi
import coil3.network.CacheStrategy
import coil3.network.NetworkRequest
import coil3.network.NetworkResponse
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.Options
import coil3.request.crossfade
import coil3.request.allowHardware
import coil3.request.allowRgb565
import okio.Path.Companion.toPath
import dagger.hilt.EntryPoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import my.noveldokusha.core.AppCacheConfig
import my.noveldokusha.core.LocaleManager
import my.noveldokusha.core.appPreferences.AppLanguage
import my.noveldokusha.core.appPreferences.AppLanguageProvider
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.di.HiltAppEntryPoint
import my.noveldokusha.data.DownloadManager
import my.noveldokusha.network.NetworkClient
import my.noveldokusha.network.ScraperNetworkClient
import my.noveldokusha.debug.MemoryDiagnostics
import my.noveldokusha.logging.FileTree
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import java.util.Locale


@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory, WorkConfiguration.Provider {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Inject
    lateinit var networkClient: NetworkClient

    @Inject
    lateinit var downloadManager: DownloadManager

    // Injeksi provider Lua untuk pembersihan memori reaktif saat onTrimMemory
    @Inject
    lateinit var luaSourceProvider: my.noveldokusha.scraper.LuaSourceProvider

    override fun attachBaseContext(newBase: Context?) {
        val base = newBase ?: return super.attachBaseContext(null)
        super.attachBaseContext(LocaleManager.createAppLocaleContext(base))
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Timber.d("App: onTrimMemory level=$level")
        runCatching {
            // Membebaskan alokasi memori Coil saat background atau memori kritis
            if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
                SingletonImageLoader.get(this).memoryCache?.clear()
            }
        }
        // Pangkas LRU cache sumber dan respons Lua
        if (::luaSourceProvider.isInitialized) {
            runCatching { luaSourceProvider.trimMemory() }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Timber.w("App: onLowMemory received")
        runCatching {
            SingletonImageLoader.get(this).memoryCache?.clear()
        }
        if (::luaSourceProvider.isInitialized) {
            runCatching { luaSourceProvider.trimMemory() }
        }
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

        // File tree for log export — all build types
        val logFile = File(filesDir, "logs/app.log")
        Timber.plant(FileTree(logFile))

        // Заголовок сессии: к какому билду относится лог.
        // Пишем напрямую в файл — Timber.i вырезается R8 из релизного dex.
        runCatching {
            val buildType = if (BuildConfig.DEBUG) "debug" else "release"
            logFile.appendText(
                "=== ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())} " +
                    "app start ${BuildConfig.VERSION_NAME}-$buildType (${BuildConfig.GIT_COMMIT_HASH}) " +
                    "API${Build.VERSION.SDK_INT} ${Build.MANUFACTURER} ${Build.MODEL} ===\n"
            )
        }

        // Ловим незахваченные исключения: пишем краш в app.log перед смертью процесса
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                Timber.e(throwable, "FATAL uncaught on ${thread.name}")
            } catch (_: Exception) {
                // логирование не должно помешать дефолтному обработчику
            }
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    @OptIn(ExperimentalCoilApi::class)
    override fun newImageLoader(context: Context): ImageLoader {
        val diskCache = coil3.disk.DiskCache.Builder()
            .directory("${context.cacheDir.absolutePath}/${AppCacheConfig.IMAGE_DISK_CACHE_DIR}".toPath())
            .maxSizeBytes(AppCacheConfig.IMAGE_DISK_CACHE_BYTES)
            .build()

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val isLowRamDevice = activityManager.isLowRamDevice

        // Alokasi memori ketat: Gunakan hanya 12% heap untuk low-RAM device guna mencegah OOM
        val memPercent = if (isLowRamDevice) 0.12 else AppCacheConfig.IMAGE_MEMORY_CACHE_PERCENT

        val memoryCache = coil3.memory.MemoryCache.Builder()
            .maxSizePercent(context, memPercent)
            .build()

        val animatorDurationScale = Settings.Global.getFloat(
            contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        )

        val sharedBuilder = ImageLoader.Builder(context)
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(8))
            .decoderCoroutineContext(Dispatchers.IO.limitedParallelism(3))
            .memoryCache(memoryCache)
            .diskCache(diskCache)
            .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
            .crossfade((AppCacheConfig.IMAGE_CROSSFADE_MS * animatorDurationScale).toInt())
            .allowHardware(AppCacheConfig.IMAGE_HARDWARE_BITMAPS)
            .allowRgb565(AppCacheConfig.IMAGE_RGB565) // RGB_565: 2 байта/пиксель вместо 4 — вдвое больше обложек в кеше

        return when (val networkClient = networkClient) {
            is ScraperNetworkClient -> sharedBuilder
                .components {
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { networkClient.client },
                            cacheStrategy = { RevalidatingCacheStrategy },
                        )
                    )
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

// Стратегия дискового кэша Coil: не отдаём из кэша ошибки и не кэшируем их.
// Дефолтная стратегия кэширует 404 по RFC и всегда читает кэш без проверки
// свежести — «отравленная» запись живёт до LRU-эвикции, ретрай бесполезен.
// Пригодность кода задаёт AppCacheConfig.isCacheableStatus (там же и правило).
// Проверено на устройстве: 404 от 30.09 в image_cache для aniliberty.png.
@OptIn(ExperimentalCoilApi::class)
private object RevalidatingCacheStrategy : CacheStrategy {

    // Тело, которое не декодируется как картинка и потому отравляет кэш:
    // 1) text/* — заглушка CDN/блокировка (text/html, text/plain и прочие);
    // 2) image/avif — CDN отдаёт AVIF только по Accept с image/avif, а на устройстве
    //    HeifDecoderImpl его не декодирует ("videoFrame is a nullptr"), ретрай читает
    //    ту же immutable-запись и вечно падает.
    private fun NetworkResponse.isUnusableBody(): Boolean {
        val contentType = headers["content-type"]?.substringBefore(';')?.trim() ?: return false
        return contentType.startsWith("text/", ignoreCase = true) ||
            contentType.equals("image/avif", ignoreCase = true)
    }

    override suspend fun read(
        cacheResponse: NetworkResponse,
        networkRequest: NetworkRequest,
        options: Options,
    ): CacheStrategy.ReadResult {
        // Пригодный для кэша ответ отдаём как есть, ошибку и непригодное тело —
        // всегда в сеть (лечит уже отравленные записи при ретрае).
        return if (
            AppCacheConfig.isCacheableStatus(cacheResponse.code) && !cacheResponse.isUnusableBody()
        ) {
            CacheStrategy.ReadResult(cacheResponse)
        } else {
            CacheStrategy.ReadResult(networkRequest)
        }
    }

    override suspend fun write(
        cacheResponse: NetworkResponse?,
        networkRequest: NetworkRequest,
        networkResponse: NetworkResponse,
        options: Options,
    ): CacheStrategy.WriteResult {
        // Кэшируем только пригодные коды и пригодное тело: «404: Not Found»,
        // текстовые заглушки и AVIF не декодируются и только отравляют кэш.
        if (
            AppCacheConfig.isCacheableStatus(networkResponse.code) &&
            !networkResponse.isUnusableBody()
        ) {
            return CacheStrategy.WriteResult(networkResponse)
        }
        // DISABLED при открытом снапшоте (cacheResponse != null) утекают FD:
        // NetworkFetcher не закрывает снапшот при response == null. Запись без тела
        // проходит через closeAndOpenEditor (снапшот закрыт, тело в кэш не попадёт);
        // старая запись и так непригодна — её бы всё равно перечитали из сети.
        return if (cacheResponse != null) {
            CacheStrategy.WriteResult(networkResponse.copy(body = null))
        } else {
            CacheStrategy.WriteResult.DISABLED
        }
    }
}
