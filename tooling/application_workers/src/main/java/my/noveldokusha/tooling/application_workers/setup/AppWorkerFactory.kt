package my.noveldokusha.tooling.application_workers.setup

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import my.noveldokusha.core.appPreferences.AppPreferences
import my.noveldokusha.coreui.states.NotificationsCenter
import my.noveldokusha.data.AppRemoteRepository
import my.noveldokusha.interactor.LibraryUpdatesInteractions
import my.noveldokusha.scraper.LuaSourceProvider
import my.noveldokusha.tooling.application_workers.AutoBackupWorker
import my.noveldokusha.tooling.application_workers.DatabaseMaintenanceWorker
import my.noveldokusha.tooling.application_workers.LibraryUpdatesWorker
import my.noveldokusha.tooling.application_workers.UpdatesCheckerWorker
import my.noveldokusha.tooling.application_workers.notifications.LibraryUpdateNotification
import javax.inject.Inject

class AppWorkerFactory @Inject internal constructor(
    private val appPreferences: AppPreferences,
    private val appRemoteRepository: AppRemoteRepository,
    private val notificationsCenter: NotificationsCenter,
    private val libraryUpdateNotification: LibraryUpdateNotification,
    private val libraryUpdatesInteractions: LibraryUpdatesInteractions,
    private val luaSourceProvider: LuaSourceProvider,
) : WorkerFactory() {
    @SuppressLint("LogNotTimber")
    override fun createWorker(
        appContext: Context,
        workerClassName: String,
        workerParameters: WorkerParameters
    ): ListenableWorker? {
        Log.d("AppWorkerFactory", "AppWorkerFactory: createWorker called for '$workerClassName'")
        return when (workerClassName) {
            UpdatesCheckerWorker::class.java.name -> {
                Log.d("AppWorkerFactory", "AppWorkerFactory: creating UpdatesCheckerWorker")
                UpdatesCheckerWorker(
                    context = appContext,
                    workerParameters = workerParameters,
                    appRemoteRepository = appRemoteRepository,
                    notificationsCenter = notificationsCenter
                )
            }
            LibraryUpdatesWorker::class.java.name -> {
                Log.d("AppWorkerFactory", "AppWorkerFactory: creating LibraryUpdatesWorker")
                LibraryUpdatesWorker(
                    context = appContext,
                    workerParameters = workerParameters,
                    appPreferences = appPreferences,
                    libraryUpdateNotification = libraryUpdateNotification,
                    libraryUpdatesInteractions = libraryUpdatesInteractions,
                    luaSourceProvider = luaSourceProvider,
                )
            }
            AutoBackupWorker::class.java.name -> {
                Log.d("AppWorkerFactory", "AppWorkerFactory: creating AutoBackupWorker")
                AutoBackupWorker(
                    context = appContext,
                    workerParameters = workerParameters,
                )
            }
            DatabaseMaintenanceWorker::class.java.name -> {
                Log.d("AppWorkerFactory", "AppWorkerFactory: creating DatabaseMaintenanceWorker")
                DatabaseMaintenanceWorker(
                    context = appContext,
                    workerParameters = workerParameters,
                )
            }
            else -> {
                Log.w("AppWorkerFactory", "AppWorkerFactory: unknown worker '$workerClassName', returning null")
                null
            }
        }
    }
}