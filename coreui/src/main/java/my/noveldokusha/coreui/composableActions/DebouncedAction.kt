package my.noveldokusha.coreui.composableActions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun debouncedAction(waitMillis: Long = 250, action: () -> Unit): () -> Unit {
    var ready by remember { mutableStateOf(true) }
    val currentAction by rememberUpdatedState(action)
    val scope = rememberCoroutineScope()

    return {
        if (ready) {
            currentAction()
            ready = false
            scope.launch {
                delay(waitMillis)
                ready = true
            }
        }
    }
}