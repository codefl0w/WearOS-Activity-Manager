package com.fl0w.wearactivity

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.remote.interactions.RemoteActivityHelper
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val BackgroundColor = Color(0xFF000000)
private val SearchSurface = Color(0xFF111826)
private val SearchBorder = Color(0xFF2E3E58)
private val Accent = Color(0xFF2E3A93)
private val AccentGlow = Color(0xFF23233F)
private val Muted = Color(0xFFAAB4C7)
private val CardSurface = Color(0xFF101621)
private val CardBorder = Color(0xFF1B2330)
private val AppIconCache = LruCache<String, ImageBitmap>(48)

private sealed interface LauncherContentRow {
    val key: String
}

private data class LauncherAppRow(
    val app: LauncherApp,
    val visibleActivityCount: Int,
    val expanded: Boolean,
) : LauncherContentRow {
    override val key: String = "app:${app.packageName}"
}

private data class LauncherActivityRow(
    val appLabel: String,
    val activity: LauncherActivity,
) : LauncherContentRow {
    override val key: String = "activity:${activity.packageName}/${activity.className}"
}

private data class LauncherInlineMessageRow(
    override val key: String,
    val title: String,
    val detail: String,
) : LauncherContentRow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            ActivityLauncherTheme {
                LauncherApp()
            }
        }
    }
}

@Composable
private fun ActivityLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(BackgroundColor),
        ) {
            content()
        }
    }
}

@Composable
private fun LauncherApp(viewModel: LauncherViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val (query, setQuery) = rememberSaveable { mutableStateOf("") }
    val (expandedPackage, setExpandedPackage) = rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler(enabled = expandedPackage != null) {
        setExpandedPackage(null)
    }

    HomeScreen(
        uiState = uiState,
        query = query,
        expandedPackage = expandedPackage,
        onQueryChange = setQuery,
        onRefresh = viewModel::refresh,
        onToggleApp = { app ->
            setExpandedPackage(
                if (expandedPackage == app.packageName) {
                    null
                } else {
                    app.packageName
                },
            )
        },
    )
}

@Composable
private fun HomeScreen(
    uiState: LauncherUiState,
    query: String,
    expandedPackage: String?,
    onQueryChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleApp: (LauncherApp) -> Unit,
) {
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    val filteredApps = remember(uiState.apps, query) {
        filterApps(uiState.apps, query)
    }
    val filteredActivityCount = remember(filteredApps, query) {
        if (query.isBlank()) {
            filteredApps.sumOf { it.activityCount }
        } else {
            filteredApps.sumOf { app -> filterActivities(app.activities, query).size }
        }
    }
    val versionName = remember(context) { context.appVersionName() }
    val contentRows = remember(filteredApps, query, expandedPackage) {
        buildContentRows(
            filteredApps = filteredApps,
            query = query,
            expandedPackage = expandedPackage,
        )
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) },
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = buildAnnotatedString {
                            append("Wear Launcher by ")
                            pushStyle(SpanStyle(color = Color(0xFFFF8FF5)))
                            append("fl0w")
                            pop()
                        },
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "v$versionName",
                        color = Muted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    SearchField(
                        query = query,
                        onQueryChange = onQueryChange,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = summaryText(
                            shownAppCount = filteredApps.size,
                            shownActivityCount = filteredActivityCount,
                            scanTime = uiState.lastUpdated,
                        ),
                        color = Muted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item {
                        LoadingChip()
                    }
                }

                uiState.errorMessage != null -> {
                    item {
                        MessageChip(
                            title = "Scan failed",
                            detail = uiState.errorMessage,
                        )
                    }
                }

                filteredApps.isEmpty() -> {
                    item {
                        MessageChip(
                            title = "Nothing matches",
                            detail = if (query.isBlank()) {
                                "No exported activities were found on this watch."
                            } else {
                                "Try a shorter search term."
                            },
                        )
                    }
                }

                else -> {
                    items(contentRows, key = { it.key }) { row ->
                        when (row) {
                            is LauncherAppRow -> {
                                AppChip(
                                    app = row.app,
                                    visibleActivityCount = row.visibleActivityCount,
                                    expanded = row.expanded,
                                    onClick = { onToggleApp(row.app) },
                                )
                            }

                            is LauncherActivityRow -> {
                                ActivityCard(
                                    appLabel = row.appLabel,
                                    activity = row.activity,
                                    onClick = {
                                        launchActivity(
                                            context = context,
                                            packageName = row.activity.packageName,
                                            className = row.activity.className,
                                            onFailure = { message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    },
                                    onLongClick = {
                                        createPinnedShortcut(
                                            context = context,
                                            appLabel = row.appLabel,
                                            activity = row.activity,
                                            onMessage = { message ->
                                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                            },
                                        )
                                    },
                                )
                            }

                            is LauncherInlineMessageRow -> {
                                MessageChip(
                                    title = row.title,
                                    detail = row.detail,
                                )
                            }
                        }
                    }
                }
            }

            item {
                ActionDivider()
            }

            item {
                ActionChip(
                    label = "Rescan packages",
                    secondary = uiState.lastUpdated.takeIf { it.isNotBlank() }?.let { "Last scan $it" }
                        ?: "Refresh installed activity list",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Accent,
                        )
                    },
                    onClick = onRefresh,
                )
            }

            item {
                ActionChip(
                    label = "Contact Me",
                    secondary = "Open a contact link on your phone",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            tint = Accent,
                        )
                    },
                    onClick = {
                        Toast.makeText(context, "Continue on your phone.", Toast.LENGTH_SHORT).show()
                        openPhoneContactLink(
                            context = context,
                            uri = context.getString(R.string.contact_me_url).toUri(),
                            onFailure = {
                                Toast.makeText(context, "Unable to open the phone link.", Toast.LENGTH_SHORT).show()
                            },
                        )
                    },
                )
            }

            item {
                ActionChip(
                    label = "Donate",
                    secondary = "Support the project on your phone",
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Accent,
                        )
                    },
                    onClick = {
                        Toast.makeText(context, "Continue on your phone.", Toast.LENGTH_SHORT).show()
                        openPhoneContactLink(
                            context = context,
                            uri = context.getString(R.string.donate_url).toUri(),
                            onFailure = {
                                Toast.makeText(context, "Unable to open the phone link.", Toast.LENGTH_SHORT).show()
                            },
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun AppChip(
    app: LauncherApp,
    visibleActivityCount: Int,
    expanded: Boolean,
    onClick: () -> Unit,
) {
    val icon = rememberAppIcon(packageName = app.packageName)
    val secondaryText = if (visibleActivityCount == app.activityCount) {
        "${app.activityCount} activities"
    } else {
        "$visibleActivityCount matches"
    }

    Chip(
        modifier = Modifier.fillMaxWidth(),
        colors = if (expanded) {
            ChipDefaults.primaryChipColors()
        } else {
            ChipDefaults.secondaryChipColors()
        },
        label = {
            Text(
                text = app.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = secondaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            if (icon == null) {
                Icon(
                    imageVector = Icons.Default.Apps,
                    contentDescription = null,
                )
            } else {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }
        },
        onClick = onClick,
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ActivityCard(
    activity: LauncherActivity,
    appLabel: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val detail = buildString {
        append(appLabel)
        append(" - ")
        append(activity.shortName)
        if (!activity.permission.isNullOrBlank()) {
            append(" - needs permission")
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(CardSurface)
            .border(width = 1.dp, color = CardBorder, shape = RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Accent,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .size(20.dp),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 30.dp),
        ) {
            Text(
                text = activity.label,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = detail,
                color = Muted,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    secondary: String,
    icon: @Composable BoxScope.() -> Unit,
    onClick: () -> Unit,
) {
    Chip(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = AccentGlow,
                shape = RoundedCornerShape(28.dp),
            ),
        colors = ChipDefaults.primaryChipColors(),
        label = { Text(text = label) },
        secondaryLabel = {
            Text(
                text = secondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = icon,
        onClick = onClick,
    )
}

@Composable
private fun ActionDivider() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Muted),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Actions",
            color = Muted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun MessageChip(
    title: String,
    detail: String,
) {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        label = { Text(text = title) },
        secondaryLabel = {
            Text(
                text = detail,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        },
        icon = {
            Icon(
                imageVector = Icons.Default.Apps,
                contentDescription = null,
            )
        },
        onClick = {},
    )
}

@Composable
private fun LoadingChip() {
    Chip(
        modifier = Modifier.fillMaxWidth(),
        enabled = false,
        label = { Text(text = "Scanning installed packages") },
        secondaryLabel = { Text(text = "This can take a moment.") },
        icon = {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                indicatorColor = Accent,
                trackColor = SearchBorder,
                strokeWidth = 2.dp,
            )
        },
        onClick = {},
    )
}

@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(SearchSurface)
            .border(width = 1.dp, color = SearchBorder, shape = RoundedCornerShape(18.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = Accent,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .size(16.dp),
            )
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                cursorBrush = SolidColor(Accent),
                textStyle = TextStyle(
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = if (query.isNotBlank()) 24.dp else 0.dp),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isBlank()) {
                            Text(
                                text = "Search apps or activities",
                                color = Muted,
                                fontSize = 13.sp,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Clear search",
                    tint = Muted,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(16.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}

private fun filterApps(
    apps: List<LauncherApp>,
    query: String,
): List<LauncherApp> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) {
        return apps
    }

    return apps.filter { app ->
        app.label.lowercase().contains(normalized) ||
            app.packageName.lowercase().contains(normalized) ||
            app.activities.any { activity ->
                activity.label.lowercase().contains(normalized) ||
                    activity.shortName.lowercase().contains(normalized)
            }
    }
}

private fun filterActivities(
    activities: List<LauncherActivity>,
    query: String,
): List<LauncherActivity> {
    val normalized = query.trim().lowercase()
    if (normalized.isBlank()) {
        return activities
    }

    return activities.filter { activity ->
        activity.label.lowercase().contains(normalized) ||
            activity.shortName.lowercase().contains(normalized) ||
            activity.className.lowercase().contains(normalized)
    }
}

private fun buildContentRows(
    filteredApps: List<LauncherApp>,
    query: String,
    expandedPackage: String?,
): List<LauncherContentRow> {
    val rows = ArrayList<LauncherContentRow>(filteredApps.size * 2)

    filteredApps.forEach { app ->
        val visibleActivities = if (query.isBlank()) {
            app.activities
        } else {
            filterActivities(app.activities, query)
        }
        val expanded = expandedPackage == app.packageName

        rows += LauncherAppRow(
            app = app,
            visibleActivityCount = visibleActivities.size,
            expanded = expanded,
        )

        if (expanded) {
            if (visibleActivities.isEmpty()) {
                rows += LauncherInlineMessageRow(
                    key = "message:${app.packageName}",
                    title = "No activities match",
                    detail = "Try a different search for this app.",
                )
            } else {
                visibleActivities.forEach { activity ->
                    rows += LauncherActivityRow(
                        appLabel = app.label,
                        activity = activity,
                    )
                }
            }
        }
    }

    return rows
}

private fun summaryText(
    shownAppCount: Int,
    shownActivityCount: Int,
    scanTime: String,
): String {
    val timestamp = scanTime.takeIf { it.isNotBlank() }?.let { " - updated $it" }.orEmpty()
    return "$shownAppCount apps - $shownActivityCount activities$timestamp"
}

private fun launchActivity(
    context: Context,
    packageName: String,
    className: String,
    onFailure: (String) -> Unit,
) {
    try {
        context.startActivity(activityLaunchIntent(packageName, className))
    } catch (_: ActivityNotFoundException) {
        onFailure("Activity is no longer available.")
    } catch (_: SecurityException) {
        onFailure("This activity requires a permission the watch app does not have.")
    } catch (_: Exception) {
        onFailure("Unable to open that activity.")
    }
}

private fun createPinnedShortcut(
    context: Context,
    appLabel: String,
    activity: LauncherActivity,
    onMessage: (String) -> Unit,
) {
    if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) {
        onMessage("Pinned shortcuts are not supported by your launcher.")
        return
    }

    val shortcut = ShortcutInfoCompat.Builder(
        context,
        "activity:${activity.packageName}/${activity.className}",
    )
        .setShortLabel(activity.label)
        .setLongLabel("$appLabel - ${activity.label}")
        .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
        .setIntent(activityLaunchIntent(activity.packageName, activity.className))
        .build()

    val requested = ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    onMessage(
        if (requested) {
            "Shortcut request sent to your home launcher."
        } else {
            "Unable to create a shortcut right now."
        },
    )
}

private fun openPhoneContactLink(
    context: Context,
    uri: android.net.Uri,
    onFailure: () -> Unit,
) {
    val remoteIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
    }

    runCatching {
        RemoteActivityHelper(context, ContextCompat.getMainExecutor(context))
            .startRemoteActivity(remoteIntent)
    }.onFailure {
        onFailure()
    }
}

@Composable
private fun rememberAppIcon(packageName: String): ImageBitmap? {
    val context = LocalContext.current.applicationContext
    val icon by produceState<ImageBitmap?>(initialValue = AppIconCache.get(packageName), packageName) {
        if (value != null) {
            return@produceState
        }

        value = withContext(Dispatchers.IO) {
            AppIconCache.get(packageName) ?: runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(width = 56, height = 56)
                    .asImageBitmap()
            }.getOrNull()?.also { bitmap ->
                AppIconCache.put(packageName, bitmap)
            }
        }
    }

    return icon
}

private fun activityLaunchIntent(
    packageName: String,
    className: String,
): Intent {
    return Intent(Intent.ACTION_VIEW).apply {
        component = ComponentName(packageName, className)
    }
}

private fun Context.appVersionName(): String {
    return runCatching {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        packageInfo.versionName ?: "1.0"
    }.getOrDefault("1.0")
}
