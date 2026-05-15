package com.luxboy.mysecretary.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luxboy.mysecretary.data.update.UpdateChecker
import com.luxboy.mysecretary.data.update.UpdateInfo
import com.luxboy.mysecretary.data.update.UpdateInstaller
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class NeedPermission(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo) : UpdateState
    data object Failed : UpdateState
}

@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val checker: UpdateChecker,
    private val installer: UpdateInstaller,
) : ViewModel() {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    fun checkOnLaunch() {
        if (_state.value != UpdateState.Idle) return
        viewModelScope.launch {
            _state.value = UpdateState.Checking
            val info = checker.checkForUpdate()
            _state.value = if (info != null) UpdateState.Available(info) else UpdateState.Idle
        }
    }

    fun startUpdate() {
        val current = _state.value as? UpdateState.Available ?: return
        if (!installer.canInstallPackages()) {
            _state.value = UpdateState.NeedPermission(current.info)
            return
        }
        viewModelScope.launch {
            _state.value = UpdateState.Downloading(current.info)
            val launched = installer.downloadAndLaunchInstall(current.info)
            _state.value = if (launched) UpdateState.Idle else UpdateState.Failed
        }
    }

    fun retryAfterPermission() {
        val current = _state.value as? UpdateState.NeedPermission ?: return
        _state.value = UpdateState.Available(current.info)
        startUpdate()
    }

    fun openInstallSettings() {
        installer.openInstallPermissionSettings()
    }

    fun dismiss() {
        _state.value = UpdateState.Idle
    }
}
