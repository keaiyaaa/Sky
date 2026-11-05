package com.example.msdksample

import android.content.Context
import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.remote.*
import com.example.msdksample.settings.*
import com.example.msdksample.ui.settings.FlightSettingsActivity
import com.example.msdksample.ui.settings.AccountSettingsController
import android.widget.EditText
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.security.MessageDigest
import java.util.UUID
import java.util.Collections

@RunWith(AndroidJUnit4::class)
class F18SettingsInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val owner = "f18-${UUID.randomUUID()}"
    private val other = "f18-${UUID.randomUUID()}"
    private val store by lazy { AccountCloudStore.getInstance(context) }
    @Before fun before() { BackendSessions.registry.clear() }
    @After fun after() {
        BackendSessions.registry.clear()
        for (id in listOf(owner, other)) {
            val hash = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
            context.deleteSharedPreferences("tianshu_settings_user_$hash")
            for (table in listOf("pending", "snapshots")) store.writableDatabase.delete(table, "user_id=?", arrayOf(id))
        }
    }
    private fun login(id: String = owner) { BackendSessions.registry.install(id, "测试账户", "fixture-only", System.currentTimeMillis() + 600_000) }
    private fun legacyDraft(action: () -> Unit) {
        val legacy = context.getSharedPreferences("tianshu_prefs", Context.MODE_PRIVATE)
        val current = context.getSharedPreferences("tianshu_user_settings_v2", Context.MODE_PRIVATE)
        val oldLegacy = legacy.all.toMap(); val oldCurrent = current.all.toMap()
        try {
            legacy.edit().clear().putInt("control_sensitivity", 80).putInt("return_altitude", 120).commit()
            current.edit().clear().commit()
            val preferences = UserSettingsPreferences(context)
            val loaded = preferences.load()
            preferences.save(loaded.settings.copy(return_altitude_m = BigDecimal("100.25"), auto_sync_records = "0"))
            action()
        } finally { restore(legacy, oldLegacy); restore(current, oldCurrent) }
    }

    @Test fun accountDraftsStartAtDefaultsAndExplicitImportRetainsSensitivityReview() = legacyDraft {
        login()
        val first = ScopedUserSettingsStore(context)
        assertEquals(UserSettings(), first.load().settings)
        assertFalse(first.load().sensitivityNeedsReview)
        first.importLocalDraft()
        assertEquals(BigDecimal("100.25"), first.load().settings.return_altitude_m)
        assertTrue(first.load().sensitivityNeedsReview)
        first.save(first.load().settings.copy(low_battery_return = "0"))
        assertTrue(first.load().sensitivityNeedsReview)
        assertTrue(store.pending(owner).isEmpty())
        login(other)
        assertEquals(UserSettings(), ScopedUserSettingsStore(context).load().settings)
        try { first.save(UserSettings()); fail("Old page cannot save after account change") } catch (_: IllegalStateException) { }
        login()
        val reopened = ScopedUserSettingsStore(context).load()
        assertEquals("0", reopened.settings.low_battery_return)
        assertTrue(reopened.sensitivityNeedsReview)
        assertEquals(BigDecimal("100.25"), UserSettingsPreferences(context).load().settings.return_altitude_m)
    }

    @Test fun nullableAccountSettingAndPendingSaveSurviveReopenWithoutCrossingOwners() {
        login()
        ScopedUserSettingsStore(context).save(UserSettings(return_altitude_m = null))
        assertNull(ScopedUserSettingsStore(context).load().settings.return_altitude_m)
        val name = "f18-${UUID.randomUUID()}.db"
        var database = AccountCloudStore(context, name)
        try {
            val body = CloudSettingsCodec.request(UserSettings(return_altitude_m = null), 2, "stable-f18-key-001")
            val pending = database.pending(owner, "settings", "PUT", "settings") { body }
            database.close(); database = AccountCloudStore(context, name)
            assertEquals(pending, database.pending(owner, "settings", "PUT", "settings") { error("Must reuse exact body") })
            assertTrue(database.pending(other).isEmpty())
            database.confirm(pending.copy(userId = other)); assertEquals(listOf(pending), database.pending(owner))
            database.confirm(pending); assertTrue(database.pending(owner).isEmpty())
            login(other)
            assertEquals(BigDecimal("100.00"), ScopedUserSettingsStore(context).load().settings.return_altitude_m)
        } finally { database.close(); context.deleteDatabase(name) }
    }

    @Test fun unavailableCloudPreservesAccountDraftAndDoesNotClaimDeviceApplication() {
        login()
        ActivityScenario.launch(FlightSettingsActivity::class.java).use {
            onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("100.25"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.btn_account_settings_save)).perform(scrollTo(), click())
            onView(withId(R.id.tv_account_settings_status)).perform(scrollTo())
                .check(matches(withText(containsString("尚未配置"))))
            assertEquals(BigDecimal("100.25"), ScopedUserSettingsStore(context).load().settings.return_altitude_m)
            assertTrue(store.pending(owner).isEmpty())
            capture("f18-account-settings")
        }
    }

    @Test fun oldGlobalDraftRequiresExplicitImportAndDoesNotClearReviewOrUpload() = legacyDraft {
        login()
        ActivityScenario.launch(FlightSettingsActivity::class.java).use {
            onView(withId(R.id.et_return_altitude)).check(matches(withText("100.00")))
            onView(withId(R.id.btn_account_settings_import)).perform(scrollTo(), click())
            onView(withText("继续")).perform(click())
            onView(withId(R.id.tv_account_settings_status)).perform(scrollTo())
                .check(matches(withText(containsString("尚未保存到账户"))))
            capture("f18-settings-imported")
            onView(withId(R.id.et_return_altitude)).perform(scrollTo()).check(matches(withText("100.25")))
            onView(withId(R.id.cb_sensitivity_confirm)).check(matches(isNotChecked()))
            assertTrue(ScopedUserSettingsStore(context).load().sensitivityNeedsReview)
            assertTrue(store.pending(owner).isEmpty())
            assertNull(store.snapshot(owner, "settings"))
        }
    }

    @Test fun oldPageCannotWriteAfterAccountSwitch() {
        login()
        ActivityScenario.launch(FlightSettingsActivity::class.java).use {
            onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("80.25"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            instrumentation.runOnMainSync { login(other) }
            onView(withId(R.id.btn_save)).perform(scrollTo(), click())
            onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("账号已变更"))))
            assertEquals(UserSettings(), ScopedUserSettingsStore(context).load().settings)
            login()
            assertEquals(UserSettings(), ScopedUserSettingsStore(context).load().settings)
        }
    }

    @Test fun uncertainSaveRetriesExactBodyAndKeepsEditsMadeAfterTheFirstAttempt() {
        login()
        val bodies = Collections.synchronizedList(mutableListOf<String>())
        val defaults = CloudUserSettings(null, "user-settings", owner, AccountSettingsValues.fromLocal(UserSettings()), 0, null, false, null)
        val client = fixtureApi { request ->
            if (request.method == "GET") 200 to CloudSettingsCodec.json.toJson(defaults) else {
                val buffer = Buffer(); request.body!!.writeTo(buffer); val body = buffer.readUtf8(); bodies += body
                if (bodies.size == 1) 503 to "{\"code\":\"SERVICE_UNAVAILABLE\"}" else {
                    val saved = CloudSettingsCodec.json.fromJson(body, SettingsUpdate::class.java)
                    200 to CloudSettingsCodec.json.toJson(defaults.copy(id = "41", settings = saved.settings,
                        version = 1, updatedAt = "2026-11-05T02:00:00Z", cloudSaved = true))
                }
            }
        }
        ActivityScenario.launch(FlightSettingsActivity::class.java).use { scenario ->
            attachFixture(scenario, client)
            onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("100.25"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.btn_account_settings_save)).perform(scrollTo(), click())
            awaitStatus("暂不可用")
            val original = store.pending(owner).single()
            assertTrue(original.body.contains("100.25"))
            onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("80.25"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.btn_account_settings_retry)).perform(scrollTo(), click())
            awaitStatus("账户已保存")
            assertEquals(listOf(original.body, original.body), bodies.toList())
            assertTrue(store.pending(owner).isEmpty())
            assertEquals(1L, CloudSettingsCodec.decode(store.snapshot(owner, "settings")!!, owner).version)
            onView(withId(R.id.et_return_altitude)).perform(scrollTo()).check(matches(withText("80.25")))
        }
    }

    @Test fun versionConflictKeepsPendingAndLocalDraftUntilExplicitDiscardAndRead() {
        login()
        val latest = CloudUserSettings("41", "user-settings", owner, AccountSettingsValues.fromLocal(UserSettings()),
            2, "2026-11-05T02:00:00Z", true, null)
        val client = fixtureApi { request -> if (request.method == "GET") 200 to CloudSettingsCodec.json.toJson(latest)
            else 409 to "{\"code\":\"VERSION_CONFLICT\"}" }
        ActivityScenario.launch(FlightSettingsActivity::class.java).use { scenario ->
            attachFixture(scenario, client)
            onView(withId(R.id.et_return_altitude)).perform(scrollTo(), replaceText("80.25"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withId(R.id.btn_account_settings_save)).perform(scrollTo(), click())
            awaitStatus("已被修改")
            assertEquals(1, store.pending(owner).size)
            assertEquals(BigDecimal("80.25"), ScopedUserSettingsStore(context).load().settings.return_altitude_m)
            onView(withId(R.id.btn_account_settings_discard)).perform(scrollTo(), click())
            onView(withText("继续")).perform(click())
            assertTrue(store.pending(owner).isEmpty())
            assertNull(store.snapshot(owner, "settings"))
            onView(withId(R.id.btn_account_settings_read)).perform(scrollTo(), click())
            onView(withText("继续")).perform(click())
            awaitStatus("已读取到本机草稿")
            assertEquals(BigDecimal("100.00"), ScopedUserSettingsStore(context).load().settings.return_altitude_m)
            assertEquals(2L, CloudSettingsCodec.decode(store.snapshot(owner, "settings")!!, owner).version)
        }
    }
    @Test fun returningToSettingsRefreshesPendingActionsAndLatestAccountSnapshot() {
        login()
        ActivityScenario.launch(FlightSettingsActivity::class.java).use { scenario ->
            onView(withId(R.id.btn_account_settings_retry)).check(matches(org.hamcrest.Matchers.not(isEnabled())))
            scenario.moveToState(Lifecycle.State.CREATED)
            store.pending(owner, "settings", "PUT", "settings") {
                CloudSettingsCodec.request(UserSettings(), 1, "other-page-pending-key")
            }
            val latest = CloudUserSettings("41", "user-settings", owner, AccountSettingsValues.fromLocal(UserSettings()),
                2, "2026-11-05T02:00:00Z", true, null)
            store.saveSnapshot(owner, "settings", CloudSettingsCodec.json.toJson(latest))
            scenario.moveToState(Lifecycle.State.RESUMED)
            onView(withId(R.id.btn_account_settings_retry)).check(matches(isEnabled()))
            onView(withId(R.id.btn_account_settings_discard)).check(matches(isEnabled()))
            onView(withId(R.id.tv_account_settings_status)).check(matches(withText(containsString("版本2"))))
            onView(withId(R.id.tv_account_settings_status)).check(matches(withText(containsString("有待确认保存"))))
        }
    }
    private fun fixtureApi(handler: (Request) -> Pair<Int, String>): BackendAccountApi {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val (code, body) = handler(chain.request())
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        return BackendAccountApi("https://school.example/api/v1", client = client)
    }
    private fun attachFixture(scenario: ActivityScenario<FlightSettingsActivity>, api: BackendAccountApi) {
        scenario.onActivity { activity ->
            val preferences = ScopedUserSettingsStore(activity)
            val height = activity.findViewById<EditText>(R.id.et_return_altitude)
            AccountSettingsController(activity, preferences, draftStamp = { height.text.toString() }, saveDraft = {
                preferences.load().settings.copy(return_altitude_m = height.text.toString().toBigDecimalOrNull()).also { preferences.save(it) }
            }, reloadDraft = { height.setText(preferences.load().settings.return_altitude_m?.toPlainString().orEmpty()) }, apiFactory = { api })
        }
    }
    private fun awaitStatus(text: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try { onView(withId(R.id.tv_account_settings_status)).check(matches(withText(containsString(text)))); return }
            catch (error: AssertionError) { if (System.currentTimeMillis() >= deadline) throw error; Thread.sleep(50) }
        }
    }
    private fun capture(name: String) {
        val descriptor = instrumentation.uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
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
}
