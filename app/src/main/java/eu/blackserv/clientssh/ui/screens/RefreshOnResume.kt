package eu.blackserv.clientssh.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Invokes [onResume] whenever the owning screen returns to the foreground.
 *
 * The callback is kept current across recompositions, while the observer is
 * registered exactly once per lifecycle owner and removed when the composable
 * leaves composition.
 */
@Composable
internal fun RefreshOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnResume by rememberUpdatedState(onResume)

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (shouldRefreshHealthMonitor(event)) currentOnResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

internal fun shouldRefreshHealthMonitor(event: Lifecycle.Event): Boolean =
    event == Lifecycle.Event.ON_RESUME
