package com.odinu.forwardsms

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.odinu.forwardsms.ui.AddFilterScreen
import com.odinu.forwardsms.ui.EditFilterScreen
import com.odinu.forwardsms.ui.FilterListScreen
import com.odinu.forwardsms.ui.FilterViewModel
import com.odinu.forwardsms.ui.HistoryScreen
import com.odinu.forwardsms.ui.LogScreen
import com.odinu.forwardsms.ui.theme.ForwardSMSTheme

class MainActivity : ComponentActivity() {

    private lateinit var permissionManager: com.odinu.forwardsms.utils.PermissionManager

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "권한이 허용되었습니다", Toast.LENGTH_SHORT).show()
            checkNotificationListenerPermission()
            // 백그라운드 서비스 시작
            com.odinu.forwardsms.service.MessageProcessingService.start(this)
        } else {
            showPermissionRetryDialog()
        }
    }

    private fun showPermissionRetryDialog() {
        val deniedPermissions = permissionManager.getDeniedPermissions()
        val permissionNames = deniedPermissions.map { permissionManager.getPermissionDisplayName(it) }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("권한 필요")
            .setMessage("앱이 정상적으로 동작하려면 다음 권한들이 필요합니다:\n\n${permissionNames.joinToString("\n") { "• $it" }}\n\n설정에서 권한을 허용하시겠습니까?")
            .setPositiveButton("설정으로 이동") { _, _ ->
                try {
                    startActivity(permissionManager.createAppSettingsIntent())
                } catch (e: Exception) {
                    Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("나중에") { _, _ ->
                Toast.makeText(this, "일부 기능이 제한될 수 있습니다", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        permissionManager = com.odinu.forwardsms.utils.PermissionManager(this)
        checkAndRequestPermissions()

        setContent {
            ForwardSMSTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    ForwardSMSApp()
                }
            }
        }
    }

    private fun checkAndRequestPermissions() {
        val deniedPermissions = permissionManager.getDeniedPermissions()

        if (deniedPermissions.isNotEmpty()) {
            if (permissionManager.shouldShowRationale(this)) {
                showPermissionRationaleDialog(deniedPermissions)
            } else {
                permissionLauncher.launch(deniedPermissions.toTypedArray())
            }
        } else {
            checkNotificationListenerPermission()
            // 모든 권한이 있으면 백그라운드 서비스 시작
            com.odinu.forwardsms.service.MessageProcessingService.start(this)
        }
    }

    private fun showPermissionRationaleDialog(deniedPermissions: List<String>) {
        val permissionDescriptions = deniedPermissions.map { permission ->
            "${permissionManager.getPermissionDisplayName(permission)}: ${permissionManager.getPermissionDescription(permission)}"
        }

        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("권한이 필요한 이유")
            .setMessage("ForwardSMS가 정상적으로 동작하려면 다음 권한들이 필요합니다:\n\n${permissionDescriptions.joinToString("\n\n")}")
            .setPositiveButton("권한 허용") { _, _ ->
                permissionLauncher.launch(deniedPermissions.toTypedArray())
            }
            .setNegativeButton("취소") { _, _ ->
                Toast.makeText(this, "권한 없이는 일부 기능이 제한됩니다", Toast.LENGTH_LONG).show()
            }
            .setCancelable(false)
            .show()
    }

    private fun checkNotificationListenerPermission() {
        val enabledListeners = Settings.Secure.getString(
            contentResolver,
            "enabled_notification_listeners"
        )

        val packageName = packageName
        if (enabledListeners.isNullOrEmpty() || !enabledListeners.contains(packageName)) {
            Toast.makeText(
                this,
                "RCS 메시지 감지를 위해 알림 액세스 권한을 허용해주세요",
                Toast.LENGTH_LONG
            ).show()

            try {
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "설정 화면을 열 수 없습니다", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun ForwardSMSApp() {
    val navController = rememberNavController()
    val viewModel: FilterViewModel = viewModel()

    NavHost(
        navController = navController,
        startDestination = "filter_list"
    ) {
        composable("filter_list") {
            FilterListScreen(
                viewModel = viewModel,
                onAddFilter = {
                    navController.navigate("add_filter")
                },
                onEditFilter = { filterId ->
                    navController.navigate("edit_filter/$filterId")
                },
                onViewHistory = {
                    navController.navigate("history")
                },
                onViewLogs = {
                    navController.navigate("logs")
                },
                onViewSystemOptimization = {
                    navController.navigate("system_optimization")
                }
            )
        }

        composable("add_filter") {
            AddFilterScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("edit_filter/{filterId}") { backStackEntry ->
            val filterId = backStackEntry.arguments?.getString("filterId")?.toIntOrNull() ?: 0
            EditFilterScreen(
                filterId = filterId,
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("history") {
            HistoryScreen(
                viewModel = viewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("logs") {
            LogScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable("system_optimization") {
            com.odinu.forwardsms.ui.SystemOptimizationScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}