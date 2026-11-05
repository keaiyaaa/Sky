package com.example.msdksample

import android.content.Context
import android.content.SharedPreferences
import android.widget.SeekBar
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.settings.UserSettingsPreferences
import com.example.msdksample.ui.settings.FlightSettingsActivity
import com.example.msdksample.ui.settings.VideoSettingsActivity
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal

@RunWith(AndroidJUnit4::class)
class SettingsFieldsUiInstrumentedTest {
    @Test fun legacyReviewDecimalHeightEnumsAndRecordSyncSurvivePageReopen() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val legacy = context.getSharedPreferences("tianshu_prefs", Context.MODE_PRIVATE)
        val current = context.getSharedPreferences("tianshu_user_settings_v2", Context.MODE_PRIVATE)
        val oldLegacy = legacy.all.toMap(); val oldCurrent = current.all.toMap()
        try {
            legacy.edit().clear().putInt("control_sensitivity", 80).putInt("return_altitude", 120)
                .putString("failsafe_action", "rth").putString("obstacle_action", "brake").commit()
            current.edit().clear().commit()
            instrumentation.runOnMainSync { DroneManager.getInstance().disableSimulator() }
            val preferences = UserSettingsPreferences(context)
            ActivityScenario.launch(FlightSettingsActivity::class.java).use { activity ->
                onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("100.25"))
                closeSoftKeyboard()
                clickSaveByScrolling()
                onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("重新选择或确认"))))
                assertTrue(preferences.load().sensitivityNeedsReview)
                assertEquals(BigDecimal("120"), preferences.load().settings.return_altitude_m)
                onView(withId(R.id.rb_failsafe_hover)).perform(scrollTo(), click())
                onView(withId(R.id.rb_obstacle_bypass)).perform(scrollTo(), click())
                activity.onActivity { it.findViewById<SeekBar>(R.id.sb_sensitivity).progress = 9 }
                onView(withId(R.id.cb_sensitivity_confirm)).perform(scrollTo(), click())
                clickSaveByScrolling()
                onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("本地已保存 · 未同步"))))
                val saved = preferences.load()
                assertFalse(saved.sensitivityNeedsReview)
                assertEquals(BigDecimal("100.25"), saved.settings.return_altitude_m)
                assertEquals(10, saved.settings.control_sensitivity)
                assertEquals("1", saved.settings.failsafe_action)
                assertEquals("0", saved.settings.obstacle_action)
                assertEquals(80, legacy.getInt("control_sensitivity", -1))
            }
            ActivityScenario.launch(FlightSettingsActivity::class.java).use {
                onView(withId(R.id.et_return_altitude)).check(matches(withText("100.25")))
                onView(withId(R.id.et_return_altitude)).perform(scrollTo(), clearText())
                closeSoftKeyboard()
                clickSaveByScrolling()
                assertNull(preferences.load().settings.return_altitude_m)
            }
            ActivityScenario.launch(VideoSettingsActivity::class.java).use {
                onView(withId(R.id.rb_video_smooth)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                onView(withId(R.id.rb_video_hd)).check(matches(withText("1080p")))
                onView(withId(R.id.switch_auto_sync)).perform(scrollTo(), click())
                clickSaveByScrolling()
                onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("账户保存请单独操作"))))
                assertEquals("0", preferences.load().settings.auto_sync_records)
                assertEquals("1080p", current.getString("video_quality", null))
                assertEquals("high", current.getString("image_quality", null))
            }
        } finally { restore(legacy, oldLegacy); restore(current, oldCurrent) }
    }

    private fun restore(preferences: SharedPreferences, values: Map<String, *>) {
        val editor = preferences.edit().clear()
        values.forEach { (key, value) -> when (value) {
            is String -> editor.putString(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        } }
        check(editor.commit())
    }

    private fun clickSaveByScrolling() {
        onView(withId(R.id.btn_save)).perform(scrollTo(), click())
    }
}
