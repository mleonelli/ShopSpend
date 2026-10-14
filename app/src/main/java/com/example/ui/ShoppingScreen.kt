package com.example.ui

import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.ShoppingItem
import com.example.ui.components.AdBanner
import com.example.ui.components.AdjustThresholdDialog
import com.example.ui.components.BudgetMeter
import com.example.ui.components.EditItemDialog
import com.example.ui.components.ManualAddItemDialog
import com.example.ui.components.NewShoppingDialog
import com.example.ui.components.SettingsDialog
import com.example.ui.components.ShoppingHistoryDialog
import com.example.ui.components.ShoppingItemCard
import com.example.ui.components.ThresholdExceededDialog
import com.example.ui.components.VoiceInputDialog
import com.example.util.PreferenceManager
import com.example.viewmodel.ShoppingViewModel
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    viewModel: ShoppingViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.items.collectAsState()
    val currentSession by viewModel.currentSession.collectAsState()
    val allSessions by viewModel.allSessions.collectAsState()
    val totalSpent by viewModel.totalSpent.collectAsState()
    val showExceededAlert by viewModel.showExceededAlert.collectAsState()
    val exceededAlertInfo by viewModel.exceededAlertInfo.collectAsState()

    // Settings & preferences
    val language by viewModel.language.collectAsState()
    val currencyPref by viewModel.currency.collectAsState()
    val isVibrationEnabled by viewModel.isVibrationEnabled.collectAsState()

    // Dialog visibility states
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showVoiceDialog by remember { mutableStateOf(false) }
    var showManualAddDialog by remember { mutableStateOf(false) }
    var showNewShoppingDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showAdjustThresholdDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<ShoppingItem?>(null) }
    var voicePriceTargetItem by remember { mutableStateOf<ShoppingItem?>(null) }

    val currency = currentSession?.currencySymbol ?: currencyPref
    val threshold = currentSession?.threshold ?: 0.0
    val isTripClosed = currentSession?.isCompleted == true
    val isShoppingList = currentSession?.isShoppingList == true

    // Dynamic locale context for live language switching
    val context = LocalContext.current
    val targetLocale = remember(language) {
        when (language) {
            PreferenceManager.LANG_IT -> Locale.ITALIAN
            PreferenceManager.LANG_EN -> Locale.ENGLISH
            else -> {
                val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
                if (sysLang.startsWith("it")) Locale.ITALIAN else Locale.ENGLISH
            }
        }
    }

    LaunchedEffect(targetLocale) {
        Locale.setDefault(targetLocale)
        val res = context.resources
        val config = Configuration(res.configuration)
        config.setLocale(targetLocale)
        @Suppress("DEPRECATION")
        res.updateConfiguration(config, res.displayMetrics)
    }

    val localizedConfiguration = remember(targetLocale, context) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        config
    }

    val localizedContext = remember(targetLocale, context) {
        val config = Configuration(context.resources.configuration)
        config.setLocale(targetLocale)
        context.createConfigurationContext(config)
    }

    CompositionLocalProvider(
        LocalConfiguration provides localizedConfiguration,
        LocalContext provides localizedContext
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = currentSession?.title ?: stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (isTripClosed) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.errorContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.trip_status_closed),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onErrorContainer
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                } else if (isShoppingList) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = stringResource(R.string.shopping_list_badge),
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            ),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (threshold > 0.0) "${stringResource(R.string.budget)}: $currency${String.format(Locale.US, "%.2f", threshold)}" else stringResource(R.string.no_limit),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { showHistoryDialog = true },
                            modifier = Modifier.testTag("history_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = stringResource(R.string.history_trips_title),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        if (currentSession != null) {
                            IconButton(
                                onClick = { viewModel.toggleCurrentSessionStatus() },
                                modifier = Modifier.testTag("toggle_trip_status_button")
                            ) {
                                Icon(
                                    imageVector = if (isTripClosed) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isTripClosed) stringResource(R.string.reopen_trip_action) else stringResource(R.string.close_trip_action),
                                    tint = if (isTripClosed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.settings),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(
                            onClick = { showNewShoppingDialog = true },
                            modifier = Modifier.testTag("new_trip_action_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddShoppingCart,
                                contentDescription = stringResource(R.string.new_shopping_trip),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            floatingActionButton = {
                if (isTripClosed) {
                    ExtendedFloatingActionButton(
                        onClick = { viewModel.toggleCurrentSessionStatus() },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = stringResource(R.string.reopen_trip_action),
                                modifier = Modifier.size(24.dp)
                            )
                        },
                        text = {
                            Text(
                                text = stringResource(R.string.reopen_trip_action),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.testTag("reopen_trip_fab")
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 6.dp, bottom = 4.dp)
                    ) {
                        // Secondary button for manual typing
                        FloatingActionButton(
                            onClick = { showManualAddDialog = true },
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = CircleShape,
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("manual_add_fab")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.manual_add),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Primary prominent voice button
                        ExtendedFloatingActionButton(
                            onClick = { showVoiceDialog = true },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = stringResource(R.string.voice_input_title),
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            text = {
                                Text(
                                    text = stringResource(R.string.say_item_price),
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                )
                            },
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.testTag("voice_input_fab")
                        )
                    }
                }
            },
            modifier = modifier
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                // Ad Banner
                AdBanner(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Closed Trip Banner
                if (isTripClosed) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .testTag("trip_closed_banner")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = stringResource(R.string.trip_closed_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                            TextButton(
                                onClick = { viewModel.toggleCurrentSessionStatus() },
                                modifier = Modifier.testTag("reopen_banner_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.reopen_trip_action),
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                // Budget / Running Total Header Card
                BudgetMeter(
                    totalSpent = totalSpent,
                    threshold = threshold,
                    currencySymbol = currency,
                    onEditThresholdClick = {
                        if (!isTripClosed) showAdjustThresholdDialog = true
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )

                // Items List or Empty State
                if (items.isEmpty()) {
                    EmptyItemsPlaceholder()
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .testTag("items_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${stringResource(R.string.shopping_items)} (${items.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Text(
                                    text = if (isTripClosed) stringResource(R.string.trip_closed_badge) else stringResource(R.string.tap_to_edit),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                        }

                        items(items, key = { it.id }) { item ->
                            ShoppingItemCard(
                                item = item,
                                currencySymbol = currency,
                                isReadOnly = isTripClosed,
                                onItemClick = {
                                    if (!isTripClosed) editingItem = item
                                },
                                onDeleteClick = { viewModel.deleteItem(item) },
                                onQuantityChange = { newQty -> viewModel.updateQuantity(item, newQty) },
                                onAddPriceClick = {
                                    voicePriceTargetItem = item
                                }
                            )
                        }
                    }
                }
            }
        }

        // --- Dialogs ---

        // Settings Dialog
        if (showSettingsDialog) {
            key(language) {
                SettingsDialog(
                    currentLanguage = language,
                    currentCurrency = currencyPref,
                    isVibrationEnabled = isVibrationEnabled,
                    onDismiss = { showSettingsDialog = false },
                    onLanguageSelected = { viewModel.setLanguage(it) },
                    onCurrencySelected = { viewModel.setCurrency(it) },
                    onVibrationToggled = { viewModel.setVibrationEnabled(it) }
                )
            }
        }

        // Voice Input Dialog for adding items
        if (showVoiceDialog) {
            VoiceInputDialog(
                currencySymbol = currency,
                language = language,
                onDismiss = { showVoiceDialog = false },
                onItemConfirmed = { name, price, qty ->
                    viewModel.addItem(name, price, qty)
                },
                onMultipleItemsConfirmed = { itemList ->
                    viewModel.addItems(itemList)
                }
            )
        }

        // Voice Input Dialog for setting price on a specific item
        voicePriceTargetItem?.let { targetItem ->
            VoiceInputDialog(
                currencySymbol = currency,
                language = language,
                targetItemForPrice = targetItem,
                onDismiss = { voicePriceTargetItem = null },
                onItemConfirmed = { _, price, _ ->
                    viewModel.setItemPrice(targetItem, price)
                    voicePriceTargetItem = null
                }
            )
        }

        // Manual Add Dialog
        if (showManualAddDialog) {
            ManualAddItemDialog(
                currencySymbol = currency,
                onDismiss = { showManualAddDialog = false },
                onConfirm = { name, price, qty ->
                    viewModel.addItem(name, price, qty)
                },
                onMultipleConfirm = { itemList ->
                    viewModel.addItems(itemList)
                }
            )
        }

        // Edit Item Dialog
        editingItem?.let { item ->
            EditItemDialog(
                item = item,
                currencySymbol = currency,
                onDismiss = { editingItem = null },
                onSave = { updated -> viewModel.updateItem(updated) },
                onDelete = { toDelete -> viewModel.deleteItem(toDelete) }
            )
        }

        // Threshold Exceeded Alert Dialog
        if (showExceededAlert && exceededAlertInfo != null) {
            val info = exceededAlertInfo!!
            ThresholdExceededDialog(
                totalSpent = info.totalSpent,
                threshold = info.threshold,
                exceededBy = info.exceededBy,
                currencySymbol = currency,
                onDismiss = { viewModel.dismissExceededAlert() },
                onAdjustThresholdClick = {
                    viewModel.dismissExceededAlert()
                    showAdjustThresholdDialog = true
                }
            )
        }

        // Adjust Threshold Dialog
        if (showAdjustThresholdDialog) {
            AdjustThresholdDialog(
                currentThreshold = threshold,
                currencySymbol = currency,
                onDismiss = { showAdjustThresholdDialog = false },
                onSaveThreshold = { newThreshold ->
                    viewModel.updateThreshold(newThreshold)
                }
            )
        }

        // New Shopping Trip Dialog
        if (showNewShoppingDialog) {
            NewShoppingDialog(
                onDismiss = { showNewShoppingDialog = false },
                onCreate = { title, newThreshold, curr, isShoppingList ->
                    viewModel.createNewSession(title, newThreshold, curr, isShoppingList)
                }
            )
        }

        // History Trips Dialog
        if (showHistoryDialog) {
            ShoppingHistoryDialog(
                sessions = allSessions,
                currentSessionId = currentSession?.id,
                onDismiss = { showHistoryDialog = false },
                onSelectSession = { sessionId ->
                    viewModel.switchToSession(sessionId)
                },
                onDeleteSession = { sessionId ->
                    viewModel.deleteSession(sessionId)
                },
                onToggleSessionStatus = { session ->
                    viewModel.toggleSessionStatus(session)
                },
                onNewShoppingClick = {
                    showNewShoppingDialog = true
                }
            )
        }
    }
}

@Composable
private fun EmptyItemsPlaceholder() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.size(80.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.ShoppingBag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = stringResource(R.string.cart_empty),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = stringResource(R.string.cart_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
