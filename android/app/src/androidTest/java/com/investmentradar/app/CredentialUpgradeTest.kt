package com.investmentradar.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.security.MessageDigest

/** Two separate instrumentation invocations around a real `adb install -r`.
 * Deliberately no @Before reset: the verify phase must read the old installation's data.
 * No API requests: these synthetic credentials are exercised exclusively in Demo mode.
 */
@RunWith(AndroidJUnit4::class)
class CredentialUpgradeTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val fields = listOf("eodhdKey", "fmpKey", "twelveKey", "alphaKey", "finnhubKey")
    private fun fixture(field: String) = "upgrade-fixture-only-$field"
    private fun cipherHash(): String {
        val encoded = context.getSharedPreferences("secure_providers", Context.MODE_PRIVATE).getString("config", null)
        assertTrue("Encrypted provider configuration must exist", !encoded.isNullOrBlank())
        fields.forEach { assertFalse("Credentials must not appear in plaintext preferences", encoded!!.contains(fixture(it))) }
        return MessageDigest.getInstance("SHA-256").digest(encoded!!.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
    private fun assertCredentials(config: JSONObject) {
        fields.forEach { assertTrue("A saved provider credential changed or disappeared", config.optString(it) == fixture(it)) }
    }
    @Test fun providerKeysSurvivePackageUpdate() {
        val args = InstrumentationRegistry.getArguments()
        val phase = args.getString("phase")
        org.junit.Assume.assumeTrue("Run this test through test_android_upgrade.py", phase != null)
        assertEquals("Unexpected installed app version", args.getString("expectedVersion"), context.packageManager.getPackageInfo(context.packageName, 0).versionName)
        val store = SecureSettings(context)
        val evidence = context.getSharedPreferences("credential_upgrade_probe", Context.MODE_PRIVATE)
        when (phase) {
            "seed" -> {
                val config = JSONObject().put("demo", true).put("routing", JSONObject().put("quotes", "finnhub"))
                fields.forEach { config.put(it, fixture(it)) }
                store.save(config)
                assertCredentials(store.load())
                assertTrue(evidence.edit().putString("cipherHash", cipherHash()).putInt("uid", context.applicationInfo.uid).commit())
                LocalPortfolio(context).write(listOf("AAPL", "AIR.PA"), emptyList(), true)
            }
            "verify" -> {
                assertTrue("The old installation's evidence was lost", evidence.contains("cipherHash"))
                assertEquals("Package update must retain the app UID", evidence.getInt("uid", -1), context.applicationInfo.uid)
                assertEquals("Update must preserve the encrypted blob", evidence.getString("cipherHash", null), cipherHash())
                val loaded = store.load()
                assertCredentials(loaded) // Also proves that the original Android Keystore key survived.
                assertEquals("finnhub", loaded.getJSONObject("routing").getString("quotes"))
                assertEquals(listOf("AAPL", "AIR.PA"), LocalPortfolio(context).watch())
                val repository = ResearchRepository(context)
                try {
                    runBlocking {
                        val publicConfig = repository.request("/api/settings") as JSONObject
                        fields.forEach { field ->
                            assertTrue("Native settings must recognize the saved key", publicConfig.optBoolean(field.removeSuffix("Key") + "Configured"))
                            assertFalse("Native public settings must redact the key", publicConfig.toString().contains(fixture(field)))
                        }
                        // The same partial save used by Demo/Live/routing controls must preserve all keys.
                        repository.save(JSONObject().put("demo", true))
                    }
                } finally { repository.close() }
                assertCredentials(store.load())
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    val deadline = android.os.SystemClock.uptimeMillis() + 90000
                    var recognized = false
                    while (!recognized && android.os.SystemClock.uptimeMillis() < deadline) {
                        scenario.onActivity { recognized = it.model.state.value.configured.size == fields.size }
                        if (!recognized) android.os.SystemClock.sleep(50)
                    }
                    assertTrue("Presentation must show all five keys as configured after update", recognized)
                }
            }
            else -> fail("Use the upgrade runner with phase=seed or phase=verify")
        }
    }
}
