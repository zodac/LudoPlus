package net.zodac.ludoplus.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame times while pawns move: the whole guided tutorial (`.claude/TUTORIAL.md`), played from the Rules' How to Play
 * page to its Game Over - the die's tumbles, pawns stepping square by square, a capture's slide home, and the
 * celebration - against the generated Baseline Profile. The target is 60fps (`.claude/BENCHMARKS.md`): frameDurationCpuMs
 * at P90 under 16ms. Run on a device with `./gradlew :app:baselineprofile:connectedBenchmarkReleaseAndroidTest`.
 */
@RunWith(AndroidJUnit4::class)
class PawnMoveBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun tutorialFrames() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = intArgument("benchmarkIterations") ?: 3,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            openRules()
            selectTab("How to Play")
        },
    ) {
        playTutorial()
    }
}
