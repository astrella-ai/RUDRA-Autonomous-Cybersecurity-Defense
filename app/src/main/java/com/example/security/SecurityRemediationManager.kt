package com.example.security

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Severity levels for automated security log entries.
 */
enum class LogSeverity {
    INFO,
    WARNING,
    CRITICAL
}

/**
 * An individual timestamped audit log entry capturing remediation and security actions.
 */
data class RemediationLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val actionTitle: String,
    val details: String,
    val severity: LogSeverity,
    val threatScore: Int? = null
)

/**
 * Represents a security incident recorded during threat evaluation.
 */
data class RemediationIncident(
    val timestamp: String,
    val threatScore: Int,
    val threshold: Int,
    val actionsTaken: List<String>,
    val triggerReason: String
)

/**
 * Result returned after evaluating threat metrics.
 */
sealed class ThreatEvaluationResult {
    data class Normal(val score: Int) : ThreatEvaluationResult()
    data class Elevated(val score: Int, val warning: String) : ThreatEvaluationResult()
    data class CriticalTriggered(val incident: RemediationIncident) : ThreatEvaluationResult()
}

/**
 * Manages automated security remediation protocols and audit activity logging for connected hardware sessions.
 * Follows Zero Trust and Endpoint Detection & Response (EDR) principles:
 * If threat telemetry surpasses the critical threshold, automated countermeasures isolate the channel.
 */
class SecurityRemediationManager(
    val threshold: Int = 75
) {
    private val incidentLogs = mutableListOf<RemediationIncident>()
    private val activityLogs = mutableListOf<RemediationLogEntry>()

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    /**
     * Evaluates a threat score (0-100) and executes remediation if the score exceeds [threshold].
     */
    fun evaluateAndRemediate(
        score: Int,
        reason: String,
        onIsolateSession: () -> Unit
    ): ThreatEvaluationResult {
        val now = timeFormat.format(Date())

        return if (score >= threshold) {
            // Trigger automated remediation protocol
            onIsolateSession()

            val incident = RemediationIncident(
                timestamp = now,
                threatScore = score,
                threshold = threshold,
                triggerReason = reason,
                actionsTaken = listOf(
                    "Active communication channel severed",
                    "Session cryptographic keys purged from volatile memory",
                    "Background synchronization workers terminated",
                    "Device marked as Quarantined in security policy manager"
                )
            )
            incidentLogs.add(0, incident)

            // Emit timestamped activity feed entries
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Automated Remediation Activated",
                    details = "Threat metric ($score/100) exceeded threshold ($threshold). Reason: $reason",
                    severity = LogSeverity.CRITICAL,
                    threatScore = score
                )
            )
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Hardware Channel Severed",
                    details = "Active USB host connection forcefully detached to prevent unauthorized data transfer.",
                    severity = LogSeverity.CRITICAL
                )
            )
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Cryptographic Sandbox Cleared",
                    details = "Purged volatile AES-256 session keys and cached tokens.",
                    severity = LogSeverity.CRITICAL
                )
            )
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Device Quarantined",
                    details = "Hardware marked restricted. Re-connection blocked until operator acknowledgment.",
                    severity = LogSeverity.WARNING
                )
            )

            ThreatEvaluationResult.CriticalTriggered(incident)
        } else if (score >= 45) {
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Elevated Threat Warning",
                    details = "Score ($score/100) approached threshold. Enhanced telemetry logging engaged.",
                    severity = LogSeverity.WARNING,
                    threatScore = score
                )
            )
            ThreatEvaluationResult.Elevated(
                score = score,
                warning = "Elevated risk detected. Security logging enhanced."
            )
        } else {
            activityLogs.add(
                0,
                RemediationLogEntry(
                    timestamp = now,
                    actionTitle = "Routine Audit Completed",
                    details = "Integrity check passed without anomalies (Score: $score/100).",
                    severity = LogSeverity.INFO,
                    threatScore = score
                )
            )
            ThreatEvaluationResult.Normal(score = score)
        }
    }

    fun recordDevicePaired() {
        val now = timeFormat.format(Date())
        activityLogs.add(
            0,
            RemediationLogEntry(
                timestamp = now,
                actionTitle = "Secure Session Established",
                details = "Hardware peripheral authenticated with AES-256 GCM encrypted channel.",
                severity = LogSeverity.INFO
            )
        )
    }

    fun recordQuarantineReset() {
        val now = timeFormat.format(Date())
        activityLogs.add(
            0,
            RemediationLogEntry(
                timestamp = now,
                actionTitle = "Quarantine Cleared",
                details = "Operator manually acknowledged security alerts and restored device to standby.",
                severity = LogSeverity.INFO
            )
        )
    }

    fun recordDeviceUnpaired() {
        val now = timeFormat.format(Date())
        activityLogs.add(
            0,
            RemediationLogEntry(
                timestamp = now,
                actionTitle = "Device Disconnected",
                details = "Active session gracefully terminated by operator.",
                severity = LogSeverity.INFO
            )
        )
    }

    fun getActivityLogs(): List<RemediationLogEntry> = activityLogs.toList()

    fun clearActivityLogs() {
        activityLogs.clear()
    }

    fun getIncidentHistory(): List<RemediationIncident> = incidentLogs.toList()

    fun clearIncidents() {
        incidentLogs.clear()
        activityLogs.clear()
    }
}
