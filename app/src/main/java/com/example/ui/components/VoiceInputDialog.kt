package com.example.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicNone
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.ShoppingItem
import com.example.util.ParsedVoiceItem
import com.example.util.PreferenceManager
import com.example.util.VoiceParser
import java.util.Locale

@Composable
fun VoiceInputDialog(
    currencySymbol: String,
    language: String = PreferenceManager.LANG_SYSTEM,
    targetItemForPrice: ShoppingItem? = null,
    onDismiss: () -> Unit,
    onItemConfirmed: (name: String, price: Double, quantity: Int) -> Unit,
    onMultipleItemsConfirmed: ((List<Triple<String, Double, Int>>) -> Unit)? = null
) {
    val context = LocalContext.current

    var spokenText by remember { mutableStateOf("") }
    var itemName by remember(targetItemForPrice) { mutableStateOf(targetItemForPrice?.name ?: "") }
    var itemPriceStr by remember(targetItemForPrice) {
        mutableStateOf(
            if (targetItemForPrice != null && targetItemForPrice.price > 0.0) {
                String.format(Locale.US, "%.2f", targetItemForPrice.price)
            } else ""
        )
    }
    var itemQuantity by remember(targetItemForPrice) { mutableIntStateOf(targetItemForPrice?.quantity ?: 1) }
    var detectedMultipleItems by remember { mutableStateOf<List<ParsedVoiceItem>>(emptyList()) }
    var isListening by remember { mutableStateOf(false) }

    val localeTag = remember(language) {
        when (language) {
            PreferenceManager.LANG_IT -> "it-IT"
            PreferenceManager.LANG_EN -> "en-US"
            else -> {
                val sysLang = Locale.getDefault().language.lowercase(Locale.ROOT)
                if (sysLang.startsWith("it")) "it-IT" else "en-US"
            }
        }
    }
    val isItalian = localeTag.startsWith("it")

    val speechPrompt = if (targetItemForPrice != null) {
        if (isItalian) "Dì il prezzo (es. '1.50' o 'due euro e cinquanta')"
        else "Say the price (e.g. '1.50' or 'two dollars')"
    } else {
        if (isItalian) "Dì articolo e prezzo o elenco (es. 'Latte, Pane, Uova')"
        else "Say item & price or list (e.g. 'Milk, Bread, Eggs')"
    }

    val parseAndApplyText: (String) -> Unit = { text ->
        spokenText = text
        if (targetItemForPrice != null) {
            val priceOnly = VoiceParser.parsePriceOnly(text)
            if (priceOnly != null && priceOnly > 0.0) {
                itemPriceStr = String.format(Locale.US, "%.2f", priceOnly)
            } else {
                val parsed = VoiceParser.parse(text)
                if (parsed != null && parsed.price > 0.0) {
                    itemPriceStr = String.format(Locale.US, "%.2f", parsed.price)
                }
            }
        } else {
            val multi = VoiceParser.parseList(text)
            if (multi.size > 1) {
                detectedMultipleItems = multi
                itemName = multi.first().name
                itemPriceStr = if (multi.first().price > 0.0) String.format(Locale.US, "%.2f", multi.first().price) else ""
                itemQuantity = multi.first().quantity
            } else {
                detectedMultipleItems = emptyList()
                val parsed = VoiceParser.parse(text)
                if (parsed != null) {
                    itemName = parsed.name
                    if (parsed.price > 0.0) {
                        itemPriceStr = String.format(Locale.US, "%.2f", parsed.price)
                    }
                    itemQuantity = parsed.quantity
                }
            }
        }
    }

    // Check in-app SpeechRecognizer service availability
    val speechRecognizer = remember(context) {
        try {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(speechRecognizer) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // Check if an external intent-based speech Activity exists
    val canLaunchActivity = remember(context) {
        try {
            val pm = context.packageManager
            val testIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            testIntent.resolveActivity(pm) != null || pm.queryIntentActivities(testIntent, 0).isNotEmpty()
        } catch (e: Exception) {
            false
        }
    }

    val isSpeechAvailable = (speechRecognizer != null) || canLaunchActivity

    fun buildSpeechIntent(): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
            putExtra(RecognizerIntent.EXTRA_PROMPT, speechPrompt)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }
    }

    // Speech Intent Launcher
    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isListening = false
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val spokenResults = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val recognized = spokenResults?.firstOrNull()
            if (!recognized.isNullOrBlank()) {
                parseAndApplyText(recognized)
            }
        }
    }

    // Declare permission launcher early so it's captured in lambda
    var triggerVoiceStart: (() -> Unit)? by remember { mutableStateOf(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            triggerVoiceStart?.invoke()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.mic_permission_required),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val startVoiceRecognition: () -> Unit = {
        if (!isSpeechAvailable) {
            Toast.makeText(
                context,
                context.getString(R.string.voice_not_available),
                Toast.LENGTH_SHORT
            ).show()
        } else {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED

            if (hasPermission) {
                if (speechRecognizer != null) {
                    try {
                        speechRecognizer.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {
                                isListening = true
                            }
                            override fun onBeginningOfSpeech() {
                                isListening = true
                            }
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {
                                isListening = false
                            }
                            override fun onError(error: Int) {
                                isListening = false
                            }
                            override fun onResults(results: Bundle?) {
                                isListening = false
                                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val recognized = matches?.firstOrNull()
                                if (!recognized.isNullOrBlank()) {
                                    parseAndApplyText(recognized)
                                }
                            }
                            override fun onPartialResults(partialResults: Bundle?) {
                                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()
                                if (!text.isNullOrBlank()) {
                                    spokenText = text
                                }
                            }
                            override fun onEvent(eventType: Int, params: Bundle?) {}
                        })
                        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, localeTag)
                            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        }
                        speechRecognizer.startListening(intent)
                        isListening = true
                    } catch (e: Exception) {
                        isListening = false
                        if (canLaunchActivity) {
                            try {
                                speechLauncher.launch(buildSpeechIntent())
                                isListening = true
                            } catch (ex: Exception) {
                                isListening = false
                            }
                        }
                    }
                } else if (canLaunchActivity) {
                    try {
                        speechLauncher.launch(buildSpeechIntent())
                        isListening = true
                    } catch (e: Exception) {
                        isListening = false
                        Toast.makeText(
                            context,
                            context.getString(R.string.voice_not_available),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            } else {
                try {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } catch (e: Exception) {
                    // Fallback
                }
            }
        }
    }

    LaunchedEffect(startVoiceRecognition) {
        triggerVoiceStart = startVoiceRecognition
    }

    val toggleVoiceRecognition: () -> Unit = {
        if (isListening) {
            try {
                speechRecognizer?.stopListening()
            } catch (e: Exception) {
                // Ignore
            }
            isListening = false
        } else {
            startVoiceRecognition()
        }
    }

    // Safely auto-start voice listening ONLY if speech is available AND permission is ALREADY granted.
    // Never auto-request runtime permissions from LaunchedEffect inside a Dialog.
    LaunchedEffect(isSpeechAvailable) {
        if (isSpeechAvailable) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasPermission) {
                startVoiceRecognition()
            }
        }
    }

    // Animation for listening pulsing effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Language badge
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = if (isItalian) "🗣️ Italiano (it-IT)" else "🗣️ English (en-US)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(72.dp)
                ) {
                    if (isListening) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .scale(pulseScale)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .clickable { toggleVoiceRecognition() }
                            .testTag("voice_record_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isListening) Icons.Default.Mic else Icons.Default.MicNone,
                                contentDescription = "Record voice",
                                tint = if (isListening) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isListening) stringResource(R.string.listening) else stringResource(R.string.tap_mic_to_speak),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                if (!isSpeechAvailable) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                    ) {
                        Text(
                            text = if (isItalian) "Servizio vocale non presente su questo dispositivo/emulatore. Usa i test rapidi o digita sotto."
                            else "Voice service not detected on this device/emulator. Tap a quick test or type below.",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                } else {
                    Text(
                        text = speechPrompt,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Quick test suggestion chips for easy testing / emulator use
                Text(
                    text = stringResource(R.string.quick_tests) + ":",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val presets = if (targetItemForPrice != null) {
                        listOf("0.99", "1.50", "1.99", "2.50", "3.00", "4.50", "5.00")
                    } else if (isItalian) {
                        listOf(
                            "Pane, Latte, Uova",
                            "Latte 1.50",
                            "3 Mele a 4.99",
                            "Pane 1.80",
                            "Caffè 1.20",
                            "Pasta 1.29"
                        )
                    } else {
                        listOf(
                            "Bread, Milk, Eggs",
                            "Milk 2.49",
                            "3 Apples for $4.99",
                            "Bread 1.99",
                            "Eggs 4.20",
                            "Coffee 6.50"
                        )
                    }
                    presets.forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable { parseAndApplyText(preset) }
                                .testTag("voice_preset_${preset.replace(" ", "_").replace("$", "").replace(",", "")}")
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }

                if (spokenText.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.RecordVoiceOver,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "\"$spokenText\"",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // If multiple items detected, show summary card
                if (detectedMultipleItems.size > 1 && targetItemForPrice == null) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "${stringResource(R.string.voice_items_detected)} (${detectedMultipleItems.size})",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = detectedMultipleItems.joinToString(", ") {
                                    "${it.name}${if (it.price > 0.0) " ($currencySymbol${String.format(Locale.US, "%.2f", it.price)})" else ""}"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                // Extracted Editable Fields (only if single item or targetItem)
                if (detectedMultipleItems.size <= 1 || targetItemForPrice != null) {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text(stringResource(R.string.item_name)) },
                        singleLine = true,
                        enabled = targetItemForPrice == null,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("voice_item_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = itemPriceStr,
                            onValueChange = { itemPriceStr = it },
                            label = { Text("${stringResource(R.string.price)} ($currencySymbol)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("voice_item_price_input")
                        )

                        // Quantity adjustment (hide if setting price on existing item)
                        if (targetItemForPrice == null) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.height(56.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                ) {
                                    IconButton(
                                        onClick = { if (itemQuantity > 1) itemQuantity-- },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(16.dp))
                                    }
                                    Text(
                                        text = "×$itemQuantity",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    )
                                    IconButton(
                                        onClick = { itemQuantity++ },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (detectedMultipleItems.size > 1 && targetItemForPrice == null && onMultipleItemsConfirmed != null) {
                Button(
                    onClick = {
                        onMultipleItemsConfirmed(detectedMultipleItems.map { Triple(it.name, it.price, it.quantity) })
                        onDismiss()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_voice_multiple_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("${stringResource(R.string.add_item)} (${detectedMultipleItems.size})")
                }
            } else {
                Button(
                    onClick = {
                        val price = itemPriceStr.toDoubleOrNull() ?: 0.0
                        val name = itemName.ifBlank { "Item" }
                        onItemConfirmed(name, price, itemQuantity)
                        onDismiss()
                    },
                    enabled = itemName.isNotBlank() || itemPriceStr.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_voice_item_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (targetItemForPrice != null) stringResource(R.string.save) else stringResource(R.string.add_item))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
