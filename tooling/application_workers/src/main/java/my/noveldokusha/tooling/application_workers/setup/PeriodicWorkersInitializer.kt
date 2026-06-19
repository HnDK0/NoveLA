package my.noveldokusha.tooling.application_workers.setup

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import my.noveldokusha.core.AppCoroutineScope
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.core.domain.LibraryCategory
import my.noveldokusha.tooling.application_workers.AutoBackupWorker
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

    private fun startUpdatesChecker(enabled: Boolean) {
        Timber.d("startUpdatesChecker: called enabled=$enabled")
        if (!enabled) {
            if (!workManager.getWorkInfosByTag(UpdatesCheckerWorker.TAG).isCancelled) {
                workManager.cancelAllWorkByTag(UpdatesCheckerWorker.TAG)
            }
            return
        }

        workManager.enqueueUniquePeriodicWork(
            UpdatesCheckerWorker.TAG,
            ExistingPeriodicWorkPolicy.UPDATE,
            UpdatesCheckerWorker.createPeriodicRequest(),
        )
    }

    private fun startLibraryUpdates(enabled: Boolean, intervalHours: Int) {
        Timber.d("startLibraryUpdates: called enabled=$enabled intervalHours=$intervalHours")
        if (!enabled) {
            if (!workManager.getWorkInfosByTag(LibraryUpdatesWorker.TAG).isCancelled) {
                workManager.cancelAllWorkByTag(LibraryUpdatesWorker.TAG)
            }
            return
        }

        workManager.enqueueUniquePeriodicWork(
            LibraryUpdatesWorker.TAG,
            ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
            LibraryUpdatesWorker.createPeriodicRequest(
                updateCategory = LibraryCategory.DEFAULT,
                repeatIntervalHours = intervalHours
            ),
        )
    }

    private fun onAutoBackupChanged(enabled: Boolean, intervalMinutes: Long) {
        Timber.d("onAutoBackupChanged: enabled=$enabled, intervalMinutes=$intervalMinutes")
        if (enabled) {
            AutoBackupWorker.setupTask(context, intervalMinutes)
            // Если бекапа не было или он устарел — запускаем сразу
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
    }

    fun init() {
        appCoroutineScope.launch {
            appPreferences.GLOBAL_APP_UPDATER_CHECKER_ENABLED
                .flow()
                .collectLatest { enabled ->
                    startUpdatesChecker(enabled)
                }
        }

        appCoroutineScope.launch {
            combine(
                appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_ENABLED.flow(),
                appPreferences.GLOBAL_APP_AUTOMATIC_LIBRARY_UPDATES_INTERVAL_HOURS.flow()
            ) { enabled, intervalHours ->
                startLibraryUpdates(enabled, intervalHours)
            }.collect()
        }

        // Реагируем на изменения настроек автобэкапа.
        // При старте flow эмитит текущие значения, что эквивалентно проверке при инициализации.
        appCoroutineScope.launch {
            combine(
                appPreferences.BACKUP_AUTO_ENABLED.flow(),
                appPreferences.BACKUP_AUTO_INTERVAL_MINUTES.flow()
            ) { enabled, intervalMinutes ->
                onAutoBackupChanged(enabled, intervalMinutes)
            }.collect()
        }
    }
}