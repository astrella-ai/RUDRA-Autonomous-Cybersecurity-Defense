package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.security.RemediationIncident
import com.example.security.RemediationLogEntry
import com.example.security.SecurityRemediationManager
import com.example.security.ThreatEvaluationResult
import com.example.ui.components.DeviceStatusCard
import com.example.ui.components.PairingProgressDialog
import com.example.ui.components.QuarantineBanner
import com.example.ui.components.SecurityActivityLogCard
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicePairingApp() {
  val context = LocalContext.current
  val snackbarHostState = remember { SnackbarHostState() }
  val scope = rememberCoroutineScope()

  val remediationManager = remember { SecurityRemediationManager(threshold = 75) }
  val activityLogs = remember { mutableStateListOf<RemediationLogEntry>() }

  var connectionStatus by remember { mutableStateOf(ConnectionStatus.UNPAIRED) }
  var pairedTimestamp by remember { mutableStateOf<String?>(null) }
  var isPairingDialogVisible by remember { mutableStateOf(false) }
  var pairingProgress by remember { mutableFloatStateOf(0f) }
  var statusMessage by remember { mutableStateOf("Initializing handshake...") }
  var pairingJob by remember { mutableStateOf<Job?>(null) }

  // Threat & Remediation State
  var currentThreatScore by remember { mutableIntStateOf(15) }
  var activeIncident by remember { mutableStateOf<RemediationIncident?>(null) }

  val successToastMessage = stringResource(R.string.pair_success_toast)

  fun refreshLogs() {
    activityLogs.clear()
    activityLogs.addAll(remediationManager.getActivityLogs())
  }

  // Automated remediation evaluation function
  val triggerThreatEvaluation: (Int, String) -> Unit = { score, reason ->
    currentThreatScore = score
    val result = remediationManager.evaluateAndRemediate(
      score = score,
      reason = reason,
      onIsolateSession = {
        // Countermeasure 1: Sever communication session
        pairingJob?.cancel()
        isPairingDialogVisible = false
        connectionStatus = ConnectionStatus.QUARANTINED
        // Countermeasure 2: Invalidate session keys
        pairedTimestamp = null
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

  Scaffold(
    modifier = Modifier.fillMaxSize(),
    topBar = {
      CenterAlignedTopAppBar(
        title = {
          Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
          )
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
          containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
      )
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
          .padding(20.dp)
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

        // Hardware Device Status Card
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

              remediationManager.recordDevicePaired()
              refreshLogs()

              // Toast on 100% completion
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

        // Threat Monitoring & Automated Remediation Trigger Card
        ThreatRemediationCard(
          currentScore = currentThreatScore,
          threshold = remediationManager.threshold,
          onEvaluateScore = { score, reason ->
            triggerThreatEvaluation(score, reason)
          }
        )

        // Scrollable Activity Log Feed
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

        // Security Protocol Details Banner
        SecurityFeatureBanner(isPaired = connectionStatus == ConnectionStatus.PAIRED)
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
        Spacer(modifier = Modifier.width(8.dp))
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
