package com.luxboy.mysecretary

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.luxboy.mysecretary.data.preferences.AppPreferences
import com.luxboy.mysecretary.data.voice.WakeWordService
import com.luxboy.mysecretary.ui.navigation.AppNavGraph
import com.luxboy.mysecretary.ui.theme.MySecretaryTheme
import com.luxboy.mysecretary.ui.update.UpdateOverlay
import com.luxboy.mysecretary.ui.update.UpdateViewModel
import androidx.activity.viewModels
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var preferences: AppPreferences

    private val updateViewModel: UpdateViewModel by viewModels()

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* ignored */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()
        autoStartWakeWordServiceIfEnabled()
        updateViewModel.checkOnLaunch()

        setContent {
            MySecretaryTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavGraph()
                    UpdateOverlay(viewModel = updateViewModel)
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun autoStartWakeWordServiceIfEnabled() {
        lifecycleScope.launch {
            val enabled = preferences.wakeWordEnabled.first()
            val micGranted = ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED
            if (enabled && micGranted) {
                WakeWordService.start(this@MainActivity)
            }
        }
    }
}
