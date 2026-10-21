package app.cashflow.core.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Developer switches in debug builds (Profil › Utvecklare): make reads slow, so the loading
 * skeletons can be seen, or fail, so "Försök igen" can be tried. They are stored ([onChange]),
 * so a restart shows the skeletons from the very first frame.
 */
class DebugFlags(
    slow: Boolean = false,
    fail: Boolean = false,
    private val onChange: (slow: Boolean, fail: Boolean) -> Unit = { _, _ -> },
) {
    private val _slowLoading = MutableStateFlow(slow)
    private val _failReads = MutableStateFlow(fail)
    val slowLoading: StateFlow<Boolean> = _slowLoading.asStateFlow()
    val failReads: StateFlow<Boolean> = _failReads.asStateFlow()

    fun setSlowLoading(on: Boolean) {
        _slowLoading.value = on
        onChange(on, _failReads.value)
    }

    fun setFailReads(on: Boolean) {
        _failReads.value = on
        onChange(_slowLoading.value, on)
    }
}
