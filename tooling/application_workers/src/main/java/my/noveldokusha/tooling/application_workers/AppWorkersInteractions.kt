package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
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
        Log.d("AutoBackup", "checkForLibraryUpdates: called category=$libraryCategory")
        workManager.beginUniqueWork(
            LibraryUpdatesWorker.TAG_MANUAL,
            ExistingWorkPolicy.REPLACE,
            LibraryUpdatesWorker.createManualRequest(updateCategory = libraryCategory)
        ).enqueue()
    }

    override fun cancelLibraryUpdates() {
        Log.d("AutoBackup", "cancelLibraryUpdates: called")
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
        Log.d("AutoBackup", "scheduleAutoBackup: called intervalMinutes=$intervalMinutes")
        AutoBackupWorker.setupTask(context, intervalMinutes)
    }

    fun cancelAutoBackup() {
        Log.d("AutoBackup", "cancelAutoBackup: called")
        AutoBackupWorker.setupTask(context, 0)
    }

    fun runAutoBackupNow() {
        Log.d("AutoBackup", "runAutoBackupNow: called")
        AutoBackupWorker.startNow(context)
    }
}