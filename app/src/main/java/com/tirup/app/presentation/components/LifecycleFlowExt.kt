package com.tirup.app.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.StateFlow

/**
 * Collects a StateFlow only while the hosting Lifecycle is at least STARTED.
 * Stops background Compose recompositions and pauses upstream WhileSubscribed flows
 * (such as BleBroadcaster countdown tickers) when the activity is stopped or the screen is off.
 */
@Composable
fun <T> StateFlow<T>.collectAsStateWhenStarted(): State<T> {
    val lifecycleOwner = LocalLifecycleOwner.current
    return produceState(initialValue = value, key1 = this, key2 = lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            this@collectAsStateWhenStarted.collect { value = it }
        }
    }
}
