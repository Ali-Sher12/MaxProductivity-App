package com.maxproductivity.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.maxproductivity.app.ui.AppRoot
import com.maxproductivity.app.ui.main.MainViewModel
import com.maxproductivity.app.ui.theme.MaxProductivityTheme

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: MainViewModel
    private var backgroundedAt: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )

        val factory = MainViewModelFactory(MaxProductivityApp.get(this))
        viewModel = ViewModelProvider(this, factory)[MainViewModel::class.java]

        setContent {
            MaxProductivityTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        viewModel = viewModel,
                        onOpenPermission = { type ->
                            viewModel.openPermissionSettings(this, type)
                        },
                        onBind = { viewModel.bindContext(this) },
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::viewModel.isInitialized && backgroundedAt > 0L) {
            if (System.currentTimeMillis() - backgroundedAt >= LOCK_GRACE_PERIOD_MS) {
                viewModel.lock()
            }
            backgroundedAt = 0L
        }
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            backgroundedAt = System.currentTimeMillis()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::viewModel.isInitialized) {
            viewModel.refreshPermissions()
        }
    }

    private companion object {
        const val LOCK_GRACE_PERIOD_MS = 30_000L
    }
}

private class MainViewModelFactory(
    private val app: MaxProductivityApp,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            return MainViewModel(app) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
