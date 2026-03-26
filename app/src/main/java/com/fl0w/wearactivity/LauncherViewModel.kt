package com.fl0w.wearactivity

import android.app.Application
import android.content.pm.ActivityInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class LauncherUiState(
    val isLoading: Boolean = true,
    val apps: List<LauncherApp> = emptyList(),
    val lastUpdated: String = "",
    val errorMessage: String? = null,
)

data class LauncherApp(
    val packageName: String,
    val label: String,
    val activityCount: Int,
    val activities: List<LauncherActivity>,
)

data class LauncherActivity(
    val packageName: String,
    val className: String,
    val label: String,
    val shortName: String,
    val permission: String?,
)

class LauncherViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(LauncherUiState())
    val uiState: StateFlow<LauncherUiState> = _uiState.asStateFlow()

    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        .withZone(ZoneId.systemDefault())

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                loadApps(getApplication<Application>().packageManager)
            }.onSuccess { apps ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = apps,
                        lastUpdated = timeFormatter.format(Instant.now()),
                        errorMessage = null,
                    )
                }
            }.onFailure { throwable ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        apps = emptyList(),
                        errorMessage = throwable.message ?: "Unable to scan installed activities.",
                    )
                }
            }
        }
    }

    private fun loadApps(packageManager: PackageManager): List<LauncherApp> {
        return getInstalledPackages(packageManager)
            .asSequence()
            .mapNotNull { packageInfo -> packageInfo.toLauncherApp(packageManager) }
            .sortedBy { it.label.lowercase(Locale.getDefault()) }
            .toList()
    }

    @Suppress("DEPRECATION")
    private fun getInstalledPackages(packageManager: PackageManager): List<PackageInfo> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(
                PackageManager.PackageInfoFlags.of(PackageManager.GET_ACTIVITIES.toLong()),
            )
        } else {
            packageManager.getInstalledPackages(PackageManager.GET_ACTIVITIES)
        }
    }

    private fun PackageInfo.toLauncherApp(packageManager: PackageManager): LauncherApp? {
        val visibleActivities = activities
            ?.asSequence()
            ?.filter { it.enabled && it.exported }
            ?.map { it.toLauncherActivity(packageName, packageManager) }
            ?.sortedBy { it.label.lowercase(Locale.getDefault()) }
            ?.toList()
            .orEmpty()

        if (visibleActivities.isEmpty()) {
            return null
        }

        val appLabel = applicationInfo.loadLabel(packageManager)
            .toString()
            .takeIf { it.isNotBlank() }
            ?: packageName.substringAfterLast('.')

        return LauncherApp(
            packageName = packageName,
            label = appLabel,
            activityCount = visibleActivities.size,
            activities = visibleActivities,
        )
    }

    private fun ActivityInfo.toLauncherActivity(
        packageName: String,
        packageManager: PackageManager,
    ): LauncherActivity {
        val resolvedClassName = if (name.startsWith(".")) {
            packageName + name
        } else {
            name
        }

        val labelText = loadLabel(packageManager)
            .toString()
            .takeIf { it.isNotBlank() && it != packageName }
            ?: prettifyClassName(resolvedClassName)

        return LauncherActivity(
            packageName = packageName,
            className = resolvedClassName,
            label = labelText,
            shortName = prettifyClassName(resolvedClassName),
            permission = permission,
        )
    }

    private fun prettifyClassName(className: String): String {
        return className.substringAfterLast('.')
            .replace('$', ' ')
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
    }
}
