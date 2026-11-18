package net.zodac.ludoplus

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/** The versionCode build-logic's `AppVersion` packs from the repo root's VERSION file. */
class VersionCodeTest {

    private val version = File("../../VERSION").readText().trim()

    @Test
    fun `the versionCode packs VERSION as three digits each for minor and patch`() {
        // A -PdevStamp build takes the minute it was built instead (its name is <version>-dev<minute>).
        assumeTrue(BuildConfig.VERSION_NAME == "$version-dev")
        val (major, minor, patch) = version.split(".").map(String::toInt)

        assertEquals(major * 1_000_000 + minor * 1_000 + patch, BuildConfig.VERSION_CODE)
    }
}
