package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.LogSeverity
import com.example.security.RemediationIncident
import com.example.security.RemediationLogEntry
import com.example.security.SecurityRemediationManager
import com.example.security.ThreatEvaluationResult
import com.example.ui.components.DeviceStatusCard
import com.example.ui.components.PairingProgressDialog
import com.example.ui.components.QuarantineBanner
import com.example.ui.components.SecurityActivityLogCard
import com.example.ui.components.SettingsScreen
import com.example.ui.components.ThreatRemediationCard
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent {
      MyApplicationTheme {
        DevicePairingApp()
      }
    }
  }
}

enum class ConnectionStatus {
  UNPAIRED,
  PAIRED,
  QUARANTINED
}

enum class AppNavTab {
  DEVICE,
  THREATS,
  LOGS,
  SETTINGS
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingApp() {
  val context = LocalContext.current
  val haptic = LocalHapticFeedback.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val remediationManager = remember { SecurityRemediationManager(threshold = 75) }
  val activityLogs = remember { mutableStateListOf<RemediationLogEntry>() }

  var currentTab by remember { mutableStateOf(AppNavTab.DEVICE) }
  var connectionStatus by remember { mutableStateOf(ConnectionStatus.UNPAIRED) }
  var pairedTimestamp by remember { mutableStateOf<String?>(null) }
  var isPairingDialogVisible by remember { mutableStateOf(false) }
  var pairingProgress by remember { mutableFloatStateOf(0f) }
  var statusMessage by remember { mutableStateOf("Initializing handshake...") }
  var pairingJob by remember { mutableStateOf<Job?>(null) }

  // Settings state
  var autoRemediateEnabled by remember { mutableStateOf(true) }
  var hapticFeedbackEnabled by remember { mutableStateOf(true) }

  // Threat & Remediation State
  var currentThreatScore by remember { mutableIntStateOf(15) }
  var activeIncident by remember { mutableStateOf<RemediationIncident?>(null) }

  val successToastMessage = stringResource(R.string.pair_success_toast)

  // Handle system back navigation smoothly
  BackHandler(enabled = currentTab != AppNavTab.DEVICE) {
    currentTab = AppNavTab.DEVICE
  }

  fun refreshLogs() {
    activityLogs.clear()
    activityLogs.addAll(remediationManager.getActivityLogs())
  }

  // Automated remediation evaluation function
  val triggerThreatEvaluation: (Int, String) -> Unit = { score, reason ->
    currentThreatScore = score

    if (hapticFeedbackEnabled && score >= 75) {
      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    val result = remediationManager.evaluateAndRemediate(
      score = score,
      reason = reason,
      onIsolateSession = {
        if (autoRemediateEnabled) {
          pairingJob?.cancel()
          isPairingDialogVisible = false
          connectionStatus = ConnectionStatus.QUARANTINED
          pairedTimestamp = null
        }
      }
    )

    refreshLogs()

    when (result) {
      is ThreatEvaluationResult.CriticalTriggered -> {
        activeIncident = result.incident
        Toast.makeText(
          context,
          "Critical threat detected! Automated remediation protocol activated.",
          Toast.LENGTH_LONG
        ).show()
        scope.launch {
          snackbarHostState.showSnackbar(
            message = "Remediation Alert: Hardware session isolated and quarantined.",
            actionLabel = "Review"
          )
        }
      }
      is ThreatEvaluationResult.Elevated -> {
        scope.launch {
          snackbarHostState.showSnackbar("Notice: Threat score elevated (${result.score}/100). Monitoring.")
        }
      }
      is ThreatEvaluationResult.Normal -> {
        scope.launch {
          snackbarHostState.showSnackbar("Security scan normal. Threat score: ${result.score}/100.")
        }
      }
    }
  }

  val criticalAlertCount = activityLogs.count { it.severity == LogSeverity.CRITICAL }

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            text = when (currentTab) {
              AppNavTab.DEVICE -> stringResource(R.string.app_name)
              AppNavTab.THREATS -> stringResource(R.string.title_threat_monitor)
              AppNavTab.LOGS -> stringResource(R.string.title_activity_log)
              AppNavTab.SETTINGS -> stringResource(R.string.title_settings)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      )
    },
    bottomBar = {
      NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer
      ) {
        NavigationBarItem(
          selected = currentTab == AppNavTab.DEVICE,
          onClick = { currentTab = AppNavTab.DEVICE },
          icon = { Icon(Icons.Default.Devices, contentDescription = "Device") },
          label = { Text(stringResource(R.string.tab_device)) },
          modifier = Modifier.testTag("tab_device_btn")
        )
        NavigationBarItem(
          selected = currentTab == AppNavTab.THREATS,
          onClick = { currentTab = AppNavTab.THREATS },
          icon = { Icon(Icons.Default.Shield, contentDescription = "Threats") },
          label = { Text(stringResource(R.string.tab_threats)) },
          modifier = Modifier.testTag("tab_threats_btn")
        )
        NavigationBarItem(
          selected = currentTab == AppNavTab.LOGS,
          onClick = { currentTab = AppNavTab.LOGS },
          icon = {
            if (criticalAlertCount > 0) {
              BadgedBox(badge = { Badge { Text("$criticalAlertCount") } }) {
                Icon(Icons.Default.History, contentDescription = "Logs")
              }
            } else {
              Icon(Icons.Default.History, contentDescription = "Logs")
            }
          },
          label = { Text(stringResource(R.string.tab_logs)) },
          modifier = Modifier.testTag("tab_logs_btn")
        )
        NavigationBarItem(
          selected = currentTab == AppNavTab.SETTINGS,
          onClick = { currentTab = AppNavTab.SETTINGS },
          icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
          label = { Text(stringResource(R.string.tab_settings)) },
          modifier = Modifier.testTag("tab_settings_btn")
        )
      }
    },
    snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
  ) { innerPadding ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Column(
        modifier = Modifier
          .fillMaxSize()
          .widthIn(max = 600.dp)
          .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
      ) {
        // Quarantine Alert Banner if active
        AnimatedVisibility(
          visible = connectionStatus == ConnectionStatus.QUARANTINED && activeIncident != null,
          enter = fadeIn() + slideInVertically(),
          exit = fadeOut() + slideOutVertically()
        ) {
          activeIncident?.let { incident ->
            QuarantineBanner(
              incident = incident,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              onResetQuarantine = {
                connectionStatus = ConnectionStatus.UNPAIRED
                activeIncident = null
                currentThreatScore = 15
                remediationManager.recordQuarantineReset()
                refreshLogs()
                scope.launch {
                  snackbarHostState.showSnackbar("Quarantine resolved. Device ready for re-verification.")
                }
              }
            )
          }
        }

        when (currentTab) {
          AppNavTab.DEVICE -> {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              DeviceStatusCard(
                connectionStatus = connectionStatus,
                pairedTimestamp = pairedTimestamp,
                onStartPairing = {
                  if (connectionStatus == ConnectionStatus.QUARANTINED) {
                    Toast.makeText(context, "Cannot pair while device is quarantined!", Toast.LENGTH_SHORT).show()
                    return@DeviceStatusCard
                  }

                  isPairingDialogVisible = true
                  pairingProgress = 0f
                  statusMessage = "Negotiating USB connection..."

                  pairingJob?.cancel()
                  pairingJob = scope.launch {
                    val steps = listOf(
                      Pair(0.20f, "Negotiating USB connection..."),
                      Pair(0.45f, "Exchanging authentication keys..."),
                      Pair(0.75f, "Verifying cryptographic channel..."),
                      Pair(0.95f, "Finalizing secure protocol handshake..."),
                      Pair(1.00f, "Complete!")
                    )

                    for (step in steps) {
                      while (pairingProgress < step.first) {
                        delay(45)
                        pairingProgress += 0.02f
                      }
                      statusMessage = step.second
                      delay(200)
                    }

                    delay(200)
                    isPairingDialogVisible = false
                    connectionStatus = ConnectionStatus.PAIRED
                    val format = SimpleDateFormat("h:mm a, MMM d", Locale.getDefault())
                    pairedTimestamp = format.format(Date())

                    if (hapticFeedbackEnabled) {
                      haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }

                    remediationManager.recordDevicePaired()
                    refreshLogs()

                    Toast.makeText(context, successToastMessage, Toast.LENGTH_SHORT).show()
                    snackbarHostState.showSnackbar(
                      message = successToastMessage,
                      actionLabel = "Dismiss"
                    )
                  }
                },
                onUnpair = {
                  connectionStatus = ConnectionStatus.UNPAIRED
                  pairedTimestamp = null
                  remediationManager.recordDeviceUnpaired()
                  refreshLogs()
                  scope.launch {
                    snackbarHostState.showSnackbar("Device disconnected and unpaired.")
                  }
                }
              )

              SecurityFeatureBanner(isPaired = connectionStatus == ConnectionStatus.PAIRED)
            }
          }

          AppNavTab.THREATS -> {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              ThreatRemediationCard(
                currentScore = currentThreatScore,
                threshold = remediationManager.threshold,
                onEvaluateScore = { score, reason ->
                  triggerThreatEvaluation(score, reason)
                }
              )
            }
          }

          AppNavTab.LOGS -> {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
              SecurityActivityLogCard(
                logs = activityLogs,
                onClearLogs = {
                  remediationManager.clearActivityLogs()
                  refreshLogs()
                  scope.launch {
                    snackbarHostState.showSnackbar("Audit activity logs cleared.")
                  }
                }
              )
            }
          }

          AppNavTab.SETTINGS -> {
            SettingsScreen(
              autoRemediateEnabled = autoRemediateEnabled,
              onAutoRemediateChanged = { autoRemediateEnabled = it },
              hapticFeedbackEnabled = hapticFeedbackEnabled,
              onHapticFeedbackChanged = { hapticFeedbackEnabled = it }
            )
          }
        }
      }
    }
  }

  // Pairing Modal Dialog
  if (isPairingDialogVisible) {
    PairingProgressDialog(
      progress = pairingProgress,
      statusText = statusMessage,
      onCancel = {
        pairingJob?.cancel()
        isPairingDialogVisible = false
        pairingProgress = 0f
      }
    )
  }
}

@Composable
fun SecurityFeatureBanner(isPaired: Boolean) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("security_info_card"),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
          imageVector = Icons.Default.Security,
          contentDescription = "Security",
          tint = MaterialTheme.colorScheme.secondary,
          modifier = Modifier.size(20.dp)
        )
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text(
          text = "Zero-Trust Enforcement Policies",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
      }

      Text(
        text = if (isPaired)
          "• Authenticated Session active\n• Real-time threat evaluation enabled\n• Auto-isolation threshold: 75/100"
        else
          "• Connect device using USB Host mode\n• Complete pairing to establish cryptographic channel\n• Real-time SOAR/EDR remediation triggers if threshold exceeded",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
