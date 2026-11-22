package com.capji.netpulse.ui.viewmodel

import com.capji.netpulse.R
import com.capji.netpulse.domain.model.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capji.netpulse.AppGraph
import com.capji.netpulse.data.speedtest.RealSpeedTestEngine
import com.capji.netpulse.data.speedtest.SimulatedSpeedTestEngine
import com.capji.netpulse.domain.model.SpeedPhase
import com.capji.netpulse.domain.model.SpeedRecord
import com.capji.netpulse.domain.model.SpeedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SpeedTestViewModel : ViewModel() {
    private val _state = MutableStateFlow(SpeedState())
    val state: StateFlow<SpeedState> = _state.asStateFlow()

    private val _simulated = MutableStateFlow(AppGraph.settings.defaultSimulate.value)
    val simulated: StateFlow<Boolean> = _simulated.asStateFlow()

    val history: StateFlow<List<SpeedRecord>> = AppGraph.speedHistory

    private var job: Job? = null

    fun setSimulated(value: Boolean) {
        _simulated.value = value
    }

    fun start() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val engine = if (_simulated.value) SimulatedSpeedTestEngine() else RealSpeedTestEngine(AppGraph.http)
            try {
                engine.run().collect { _state.value = it }
                val s = _state.value
                if (s.phase == SpeedPhase.DONE) {
                    AppGraph.speedHistory.update {
                        listOf(
                            SpeedRecord(System.currentTimeMillis(), s.pingMs ?: 0f, s.jitterMs ?: 0f, s.downMbps ?: 0f, s.upMbps ?: 0f),
                        ) + it
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = SpeedState(phase = SpeedPhase.ERROR, error = e.message?.takeIf { it.isNotBlank() }?.let { UiText(raw = it) } ?: UiText(R.string.speed_error_default))
            }
        }
    }

    fun stop() {
        job?.cancel()
        _state.value = SpeedState()
    }
}
