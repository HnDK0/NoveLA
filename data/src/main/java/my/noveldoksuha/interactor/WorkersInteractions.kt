package my.noveldokusha.interactor

import kotlinx.coroutines.flow.Flow
import my.noveldokusha.core.domain.LibraryCategory

interface WorkersInteractions {
    fun checkForLibraryUpdates(libraryCategory: LibraryCategory)
    fun cancelLibraryUpdates()
    fun isManualUpdateRunning(): Flow<Boolean>
}
