package my.noveldokusha.tooling.application_workers.setup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.tooling.application_workers.AutoBackupWorker
import my.noveldokusha.tooling.application_workers.DatabaseMaintenanceWorker
import my.noveldokusha.tooling.application_workers.LibraryUpdatesWorker
import my.noveldokusha.tooling.application_workers.UpdatesCheckerWorker
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PeriodicWorkersInitializer @Inject constructor(
    private val appPreferences: AppPreferences,
    @ApplicationContext private val context: Context,
    private val appCoroutineScope: AppCoroutineScope
) {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    fun init() {
        // --- UpdatesCheckerWorker ---
        // Регистрация при старте: KEEP не сбрасывает таймер при перезапуске
        workManager.enqueueUniquePeriodicWork(
            UpdatesCheckerWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            UpdatesCheckerWorker.createPeriodicRequest(),
        )

        // Реакция на вкл/выкл проверки обновлений приложения
        appCoroutineScope.launch {
            appPreferences.GLOBAL_APP_UPDATER_CHECKER_ENABLED
                .flow()
                .collectLatest { enabled ->
                    if (!enabled) {
                        workManager.cancelAllWorkByTag(UpdatesCheckerWorker.TAG)
                    } else {
                        workManager.enqueueUniquePeriodicWork(
                            UpdatesCheckerWorker.TAG,
                            ExistingPeriodicWorkPolicy.KEEP,
                            UpdatesCheckerWorker.createPeriodicRequest(),
                        )
                    }
                }
        }

        // --- LibraryUpdatesWorker ---
        // Регистрация периодической работы; manual запускается только по запросу пользователя
        val libEnabled = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED.value
        val libInterval = appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS.value

        if (libEnabled) {
            workManager.enqueueUniquePeriodicWork(
                LibraryUpdatesWorker.TAG,
                ExistingPeriodicWorkPolicy.KEEP,
                LibraryUpdatesWorker.createPeriodicRequest(LibraryCategory.DEFAULT, libInterval),
            )
        }

        // Реакция на изменения настроек библиотеки (без начального эмита)
        appCoroutineScope.launch {
            combine(
                appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED.flow(),
                appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS.flow()
            ) { enabled, intervalHours ->
                if (!enabled) {
                    workManager.cancelAllWorkByTag(LibraryUpdatesWorker.TAG)
                    workManager.cancelAllWorkByTag(LibraryUpdatesWorker.TAG_MANUAL)
                } else {
                    workManager.enqueueUniquePeriodicWork(
                        LibraryUpdatesWorker.TAG,
                        ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                        LibraryUpdatesWorker.createPeriodicRequest(LibraryCategory.DEFAULT, intervalHours),
                    )
                }
            }.drop(1).collect()
        }

        // --- DatabaseMaintenanceWorker ---
        workManager.enqueueUniquePeriodicWork(
            DatabaseMaintenanceWorker.TAG,
            ExistingPeriodicWorkPolicy.KEEP,
            DatabaseMaintenanceWorker.createPeriodicRequest(),
        )

        // --- AutoBackupWorker ---
        // При старте flow эмитит текущие значения, что эквивалентно проверке при инициализации
        appCoroutineScope.launch {
            combine(
                appPreferences.BACKUP_AUTO_ENABLED.flow(),
                appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.flow()
            ) { enabled, intervalMinutes ->
                if (enabled) {
                    AutoBackupWorker.setupTask(context, intervalMinutes)
                    val lastTimestamp = appPreferences.BACKUP_AUTO_LAST_TIMESTAMP.value
                    val now = System.currentTimeMillis()
                    val intervalMs = intervalMinutes * 60 * 1000L
                    if (lastTimestamp <= 0 || (now - lastTimestamp) >= intervalMs) {
                        Timber.d("onAutoBackupChanged: backup is stale or missing, triggering one-shot")
                        AutoBackupWorker.startNow(context)
                    }
                } else {
                    AutoBackupWorker.cancelTask(context)
                }
            }.collect()
        }
    }
}