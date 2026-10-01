package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ConnectionStatus
import com.example.R

@Composable
fun DeviceStatusCard(
    connectionStatus: ConnectionStatus,
    pairedTimestamp: String?,
    onStartPairing: () -> Unit,
    onUnpair: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("device_status_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = when (connectionStatus) {
                                    ConnectionStatus.PAIRED -> MaterialTheme.colorScheme.primaryContainer
                                    ConnectionStatus.QUARANTINED -> MaterialTheme.colorScheme.errorContainer
                                    ConnectionStatus.UNPAIRED -> MaterialTheme.colorScheme.secondaryContainer
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (connectionStatus) {
                                ConnectionStatus.PAIRED -> Icons.Default.CheckCircle
                                ConnectionStatus.QUARANTINED -> Icons.Default.Warning
                                ConnectionStatus.UNPAIRED -> Icons.Default.Usb
                            },
                            contentDescription = "Device icon",
                            tint = when (connectionStatus) {
                                ConnectionStatus.PAIRED -> MaterialTheme.colorScheme.primary
                                ConnectionStatus.QUARANTINED -> MaterialTheme.colorScheme.error
                                ConnectionStatus.UNPAIRED -> MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "Target Hardware Device",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "USB-C Peripheral (Host Mode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = when (connectionStatus) {
                        ConnectionStatus.PAIRED -> MaterialTheme.colorScheme.primaryContainer
                        ConnectionStatus.QUARANTINED -> MaterialTheme.colorScheme.errorContainer
                        ConnectionStatus.UNPAIRED -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.testTag("status_badge")
                ) {
                    Text(
                        text = when (connectionStatus) {
                            ConnectionStatus.PAIRED -> stringResource(R.string.status_paired)
                            ConnectionStatus.QUARANTINED -> stringResource(R.string.status_quarantined)
                            ConnectionStatus.UNPAIRED -> stringResource(R.string.status_ready_to_pair)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = when (connectionStatus) {
                            ConnectionStatus.PAIRED -> MaterialTheme.colorScheme.onPrimaryContainer
                            ConnectionStatus.QUARANTINED -> MaterialTheme.colorScheme.onErrorContainer
                            ConnectionStatus.UNPAIRED -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            AnimatedVisibility(
                visible = connectionStatus == ConnectionStatus.PAIRED && pairedTimestamp != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Encrypted session active (AES-256)",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Paired at: ${pairedTimestamp ?: "Just now"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            when (connectionStatus) {
                ConnectionStatus.UNPAIRED -> {
                    Button(
                        onClick = onStartPairing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pair_device_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.btn_pair_device),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                ConnectionStatus.PAIRED -> {
                    OutlinedButton(
                        onClick = onUnpair,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("unpair_device_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "Disconnect / Unpair",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                ConnectionStatus.QUARANTINED -> {
                    Text(
                        text = "⚠️ Device is isolated due to security policy violation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
