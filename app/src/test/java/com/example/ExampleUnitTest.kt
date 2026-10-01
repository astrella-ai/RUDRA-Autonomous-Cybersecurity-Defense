package com.example

import com.example.security.LogSeverity
import com.example.security.SecurityRemediationManager
import com.example.security.ThreatEvaluationResult
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun `threat score below threshold logs routine audit and does not trigger isolation`() {
    val manager = SecurityRemediationManager(threshold = 75)
    var isolated = false

    val result = manager.evaluateAndRemediate(
      score = 30,
      reason = "Normal traffic"
    ) {
      isolated = true
    }

    assertTrue(result is ThreatEvaluationResult.Normal)
    assertFalse(isolated)
    assertEquals(0, manager.getIncidentHistory().size)

    val logs = manager.getActivityLogs()
    assertEquals(1, logs.size)
    assertEquals(LogSeverity.INFO, logs[0].severity)
    assertEquals("Routine Audit Completed", logs[0].actionTitle)
  }

  @Test
  fun `threat score exceeding threshold triggers automated remediation and generates audit logs`() {
    val manager = SecurityRemediationManager(threshold = 75)
    var isolated = false

    val result = manager.evaluateAndRemediate(
      score = 85,
      reason = "Malicious anomaly detected"
    ) {
      isolated = true
    }

    assertTrue(result is ThreatEvaluationResult.CriticalTriggered)
    assertTrue(isolated)
    assertEquals(1, manager.getIncidentHistory().size)
    assertEquals(85, manager.getIncidentHistory()[0].threatScore)

    val logs = manager.getActivityLogs()
    assertTrue(logs.size >= 4)
    val criticalLogs = logs.filter { it.severity == LogSeverity.CRITICAL }
    assertTrue(criticalLogs.isNotEmpty())
    assertTrue(logs.any { it.actionTitle == "Hardware Channel Severed" })
    assertTrue(logs.any { it.actionTitle == "Cryptographic Sandbox Cleared" })
    assertTrue(logs.any { it.actionTitle == "Device Quarantined" })
  }

  @Test
  fun `clearing activity logs resets feed`() {
    val manager = SecurityRemediationManager(threshold = 75)
    manager.recordDevicePaired()
    manager.recordDeviceUnpaired()
    assertEquals(2, manager.getActivityLogs().size)

    manager.clearActivityLogs()
    assertEquals(0, manager.getActivityLogs().size)
  }
}
