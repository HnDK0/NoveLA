package my.noveldokusha.tooling.application_workers

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import my.noveldokusha.interactor.WorkersInteractions
import my.noveldokusha.core.domain.LibraryCategory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppWorkersInteractions @Inject constructor(
    @ApplicationContext private val context: Context,
): WorkersInteractions {

    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override fun checkForLibraryUpdates(libraryCategory: LibraryCategory) {
        Timber.d("checkForLibraryUpdates: called category=$libraryCategory")
        workManager.beginUniqueWork(
            LibraryUpdatesWorker.TAG_MANUAL,
            ExistingWorkPolicy.REPLACE,
            LibraryUpdatesWorker.createManualRequest(updateCategory = libraryCategory)
        ).enqueue()
    }

    override fun cancelLibraryUpdates() {
        Timber.d("cancelLibraryUpdates: called")
        workManager.cancelUniqueWork(LibraryUpdatesWorker.TAG_MANUAL)
    }

    override fun isManualUpdateRunning(): Flow<Boolean> {
        return workManager.getWorkInfosForUniqueWorkFlow(LibraryUpdatesWorker.TAG_MANUAL)
            .map { workInfos ->
                workInfos.any {
                    it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING
                }
            }
    }

    fun scheduleAutoBackup(intervalMinutes: Long) {
        Timber.d("scheduleAutoBackup: called intervalMinutes=$intervalMinutes")
        AutoBackupWorker.setupTask(context, intervalMinutes)
    }

    fun cancelAutoBackup() {
        Timber.d("cancelAutoBackup: called")
        AutoBackupWorker.setupTask(context, 0)
    }

    fun runAutoBackupNow() {
        Timber.d("runAutoBackupNow: called")
        AutoBackupWorker.startNow(context)
    }
}