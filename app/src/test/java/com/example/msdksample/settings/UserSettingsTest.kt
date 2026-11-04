package com.example.msdksample.settings

import com.example.msdksample.drone.state.*
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class UserSettingsTest {
    @Test fun defaultsAndColumnNamesMatchDatabaseV2Exactly() {
        val defaults = UserSettings()
        assertTrue(defaults.validationErrors().isEmpty())
        assertEquals(BigDecimal("100.00"), defaults.return_altitude_m)
        assertEquals(5, defaults.control_sensitivity)
        assertEquals("0", defaults.obstacle_action)
        assertEquals("1080p", defaults.video_quality)
        assertEquals("high", defaults.image_quality)
        assertEquals(setOf("auto_sync_records", "low_battery_return", "return_altitude_m", "failsafe_action",
            "obstacle_action", "control_sensitivity", "video_quality", "image_quality", "beginner_mode"),
            defaults.values().map { it.field.column }.toSet())
    }

    @Test fun nullHeightAndTwoDecimalPlacesArePreservedWithoutRounding() {
        assertTrue(UserSettings(return_altitude_m = null).validationErrors().isEmpty())
        val decimal = UserSettings(return_altitude_m = BigDecimal("100.25"))
        assertEquals(BigDecimal("100.25"), decimal.normalized().return_altitude_m)
        assertFalse(UserSettings(return_altitude_m = BigDecimal("100.251")).validationErrors().isEmpty())
        assertNotNull(SettingField.RETURN_ALTITUDE.validate("1e2"))
        assertNotNull(SettingField.RETURN_ALTITUDE.validate("19.99"))
        assertNotNull(SettingField.RETURN_ALTITUDE.validate("400.01"))
    }

    @Test fun charFlagsAndEnumsNeverAcceptLegacyWordsOrUnknownQualityCodes() {
        assertFalse(UserSettings(failsafe_action = "rth").validationErrors().isEmpty())
        assertFalse(UserSettings(obstacle_action = "brake").validationErrors().isEmpty())
        assertFalse(UserSettings(auto_sync_records = "true").validationErrors().isEmpty())
        listOf("smooth", "hd", "ultra", "4k").forEach { assertNotNull(SettingField.VIDEO.validate(it)) }
        assertNotNull(SettingField.IMAGE.validate("hd"))
        assertTrue(UserSettings(control_sensitivity = 1).validationErrors().isEmpty())
        assertTrue(UserSettings(control_sensitivity = 10).validationErrors().isEmpty())
        assertFalse(UserSettings(control_sensitivity = 0).validationErrors().isEmpty())
        assertFalse(UserSettings(control_sensitivity = 11).validationErrors().isEmpty())
    }

    @Test fun beginnerLimitIsALocalDraftConstraint() {
        assertFalse(UserSettings(beginner_mode = "1").validationErrors().isEmpty())
        assertTrue(UserSettings(beginner_mode = "1", return_altitude_m = BigDecimal("30.00")).validationErrors().isEmpty())
    }

    @Test fun legacyPercentagesAlwaysNeedReviewEvenWhenTheyLookLikeLevels() {
        listOf(0, 5, 50, 100).forEach { legacy ->
            val result = UserSettingsMigration.fromLegacy(mapOf("control_sensitivity" to legacy))
            assertTrue(result.sensitivityNeedsReview)
            assertEquals(5, result.settings.control_sensitivity)
        }
        assertFalse(UserSettingsMigration.fromLegacy(emptyMap<String, Any>()).sensitivityNeedsReview)
    }

    @Test fun explicitLegacyEnumsMapAndPhotoSyncIsNotReusedAsRecordSync() {
        val old = mapOf("return_altitude" to 120, "failsafe_action" to "hover", "obstacle_action" to "brake",
            "video_quality" to "4k", "image_quality" to "ultra", "auto_sync" to false)
        val result = UserSettingsMigration.fromLegacy(old)
        assertEquals(BigDecimal("120"), result.settings.return_altitude_m)
        assertEquals("1", result.settings.failsafe_action)
        assertEquals("1", result.settings.obstacle_action)
        assertEquals("1", result.settings.auto_sync_records)
        assertEquals("1080p", result.settings.video_quality)
        assertEquals("high", result.settings.image_quality)
        assertEquals(false, old["auto_sync"])
        assertTrue(result.notes.any { it.contains("未转换") })
    }

    @Test fun unknownLegacyEnumsRequireCorrectionInsteadOfSelectingAnAction() {
        val settings = UserSettingsMigration.fromLegacy(mapOf("failsafe_action" to "unknown")).settings
        assertFalse(settings.validationErrors().isEmpty())
    }

    @Test fun dispatchDeniesRealUnknownCapabilitiesSdkSimulationAndOldGeneration() {
        val base = DeviceStateSnapshot(generation = 3, mode = DeviceMode.LOCAL_DEMO,
            linkState = LinkState.CONNECTED, readiness = Readiness.READY)
        assertNull(SettingsDispatchPolicy.rejection(base, 3))
        DeviceMode.values().filter { it != DeviceMode.LOCAL_DEMO }.forEach {
            assertNotNull(SettingsDispatchPolicy.rejection(base.copy(mode = it), 3))
        }
        assertNotNull(SettingsDispatchPolicy.rejection(base, 2))
        assertNotNull(SettingsDispatchPolicy.rejection(base.copy(linkState = LinkState.ERROR), 3))
    }
}
