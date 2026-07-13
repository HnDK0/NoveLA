package my.noveldokusha.tooling.application_workers

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import my.noveldokusha.data.AppRepository
import java.util.concurrent.TimeUnit

internal class DatabaseMaintenanceWorker(
    private val context: Context,
    workerParameters: WorkerParameters,
) : CoroutineWorker(context, workerParameters) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface DatabaseMaintenanceEntryPoint {
        fun appRepository(): AppRepository
    }

    companion object {
        const val TAG = "DatabaseMaintenance"

        fun createPeriodicRequest(): PeriodicWorkRequest {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .build()

            return PeriodicWorkRequestBuilder<DatabaseMaintenanceWorker>(
                repeatInterval = 7,
                repeatIntervalTimeUnit = TimeUnit.DAYS,
            )
                .addTag(TAG)
                .setConstraints(constraints)
                .setInitialDelay(1, TimeUnit.DAYS)
                .build()
        }
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "DatabaseMaintenanceWorker: starting")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                context.applicationContext,
                DatabaseMaintenanceEntryPoint::class.java
            )
            val appRepository = entryPoint.appRepository()
            appRepository.settings.clearNonLibraryData()
            appRepository.vacuum()
            Log.d(TAG, "DatabaseMaintenanceWorker: completed successfully")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "DatabaseMaintenanceWorker: failed", e)
            Result.failure()
        }
    }
}
