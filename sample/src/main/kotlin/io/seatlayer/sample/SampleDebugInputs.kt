package io.seatlayer.sample

import android.content.Context
import java.io.File
import java.util.Properties

/**
 * The inputs a device walk needs that must never be committed, and never logged.
 *
 * The walk drives this app against a real backend, so it needs an API base, and
 * for a key-scoped or private event a publishable key and a buyer bearer as
 * well. None of that may travel as an intent extra: `am start --es` puts the
 * value on a command line that every process on the device can read out of
 * `ps`, and ActivityManager writes the whole intent into logcat, where it
 * outlives the run.
 *
 * They are read instead from a properties file the operator places in this
 * app's own external files directory, which only this app and `adb` can reach:
 *
 * ```
 * /sdcard/Android/data/io.seatlayer.sample/files/seatlayer-debug.properties
 * ```
 *
 * The operator keeps it beside the repository as `sample/seatlayer-debug.properties`,
 * which `.gitignore` excludes, and pushes it before the walk. It is never
 * packaged into the APK, never named by a build file, and no value in it is
 * ever printed. A system property (`adb shell setprop debug.seatlayer.*`) is
 * deliberately not supported: a bearer token is far longer than the property
 * value limit, so it would work for the harmless values and silently truncate
 * the one that matters.
 *
 * Keys, all optional:
 *
 * | Key | What it supplies |
 * | --- | --- |
 * | `event` | the event to open, when it is not passed as the intent extra |
 * | `apiBase` | the API base URL the picker talks to |
 * | `publicKey` | a key-scoped event's publishable key |
 * | `buyerToken` | a private event's buyer access token |
 * | `buyerTokenExpiresAt` | that token's expiry, in epoch milliseconds |
 *
 * With no file present every value is absent and this app behaves exactly as it
 * does without any of it: the demo event on the SDK's own defaults.
 */
internal class SampleDebugInputs private constructor(private val values: Map<String, String>) {

    val event: String? get() = value("event")
    val apiBase: String? get() = value("apiBase")
    val publicKey: String? get() = value("publicKey")
    val buyerToken: String? get() = value("buyerToken")
    val buyerTokenExpiresAt: Double? get() = value("buyerTokenExpiresAt")?.toDoubleOrNull()

    /** True when the file named at least one value, so the walk can be told it was found. */
    val isPresent: Boolean get() = values.isNotEmpty()

    private fun value(key: String): String? = values[key]?.trim()?.takeIf(String::isNotEmpty)

    internal companion object {
        const val FILE_NAME: String = "seatlayer-debug.properties"

        /**
         * Reads the file if it is there. Every failure — absent, unreadable,
         * malformed — is the same answer as absent, and nothing is reported: a
         * message naming the file would tell a shared device it exists.
         */
        fun read(context: Context): SampleDebugInputs {
            val file = File(context.getExternalFilesDir(null), FILE_NAME)
            val loaded = runCatching {
                Properties().apply { file.inputStream().use(::load) }
            }.getOrNull() ?: return SampleDebugInputs(emptyMap())
            val values = loaded.stringPropertyNames()
                .mapNotNull { name -> loaded.getProperty(name)?.let { name to it } }
                .toMap()
            return SampleDebugInputs(values)
        }
    }
}
