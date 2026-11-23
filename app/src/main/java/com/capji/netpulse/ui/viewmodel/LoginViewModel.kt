package com.capji.netpulse.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capji.netpulse.AppGraph
import com.capji.netpulse.domain.model.ConnectionState
import com.capji.netpulse.domain.model.RouterProfile
import com.capji.netpulse.domain.model.RouterType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LoginViewModel : ViewModel() {
    var type by mutableStateOf(RouterType.AUTO)
    var host by mutableStateOf("")
    var user by mutableStateOf("admin")
    var pass by mutableStateOf("")
    var remember by mutableStateOf(true)

    val profiles: StateFlow<List<RouterProfile>> = AppGraph.profilesState

    val connection: StateFlow<ConnectionState> = AppGraph.repo.connection

    init {
        detectGateway()
    }

    fun detectGateway() {
        AppGraph.detectGateway()?.let { host = it }
    }

    fun use(p: RouterProfile) {
        type = p.type
        host = p.host
        user = p.username
        pass = ""
    }

    fun delete(p: RouterProfile) {
        AppGraph.profilesState.value = AppGraph.profiles.delete(p.id)
    }

    fun connect() {
        val h = host.trim()
        val profile = RouterProfile(
            id = "${type.name}-$h-${user.trim()}",
            name = "${type.shortName} - $h",
            type = type,
            host = h,
            username = user.trim(),
        )
        if (remember) AppGraph.profilesState.value = AppGraph.profiles.save(profile)
        AppGraph.sessionPass = pass
        if (AppGraph.settings.bgTimers.value) AppGraph.secrets.put(profile.id, pass)
        viewModelScope.launch { AppGraph.repo.connect(profile, pass) }
    }

    fun demo() {
        type = RouterType.DEMO
        host = "192.168.88.1"
        user = "admin"
        pass = "demo"
        connect()
    }
}
