package io.seatlayer.benchmark

import android.content.ComponentName
import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Physical-device cold, warm, and credential-free WebView-prewarm evidence. */
@RunWith(AndroidJUnit4::class)
public class PickerStartupBenchmark {
    @get:Rule
    public val benchmarkRule: MacrobenchmarkRule = MacrobenchmarkRule()

    @Test
    public fun coldPickerStartup(): Unit = measure(StartupMode.COLD)

    @Test
    public fun warmPickerStartup(): Unit = measure(StartupMode.WARM)

    @Test
    public fun prewarmedPickerStartup(): Unit = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.None(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait(prewarmIntent())
            require(
                device.wait(
                    Until.hasObject(By.desc(PREWARM_READY_MARKER)),
                    READY_TIMEOUT_MILLIS,
                ),
            ) { "SeatLayer WebView engine prewarm did not complete." }
        },
        measureBlock = { launchPickerAndAwaitReady() },
    )

    private fun measure(startupMode: StartupMode): Unit =
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = startupMode,
            iterations = ITERATIONS,
            setupBlock = { pressHome() },
            measureBlock = { launchPickerAndAwaitReady() },
        )

    private fun MacrobenchmarkScope.launchPickerAndAwaitReady() {
        startActivityAndWait(pickerIntent())
        require(
            device.wait(
                Until.hasObject(By.desc(PICKER_READY_MARKER)),
                READY_TIMEOUT_MILLIS,
            ),
        ) { "SeatLayer picker did not report ready." }
    }

    private fun pickerIntent(): Intent = Intent().apply {
        component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.MainActivity")
        putExtra("seatlayerIntegration", "ready-compose")
    }

    private fun prewarmIntent(): Intent = Intent().apply {
        component = ComponentName(TARGET_PACKAGE, "$TARGET_PACKAGE.PrewarmActivity")
    }

    private companion object {
        const val TARGET_PACKAGE = "io.seatlayer.sample"
        const val PICKER_READY_MARKER = "seatlayer-picker-ready"
        const val PREWARM_READY_MARKER = "seatlayer-prewarm-ready"
        const val READY_TIMEOUT_MILLIS = 30_000L
        const val ITERATIONS = 5
    }
}
