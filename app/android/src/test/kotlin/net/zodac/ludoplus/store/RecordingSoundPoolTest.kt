package net.zodac.ludoplus.store

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import net.zodac.ludoplus.device.AndroidSoundPlayer
import net.zodac.ludoplus.platform.SoundEffect
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The promo video's sound: what the app plays under [RecordingSoundPool] reaches [SoundLog], so the video and its credits get it. */
@RunWith(AndroidJUnit4::class)
@Config(shadows = [RecordingSoundPool::class])
class RecordingSoundPoolTest {

    private val log: File = File.createTempFile("sounds", ".txt")

    @After
    fun tearDown() {
        SoundLog.file = null
        log.delete()
    }

    @Test
    fun `every clip the app plays is logged with its frame and volume`() {
        SoundLog.file = log.apply { writeText("") }
        SoundLog.frame = 12
        val player = AndroidSoundPlayer(ApplicationProvider.getApplicationContext())

        player.play(SoundEffect.CAPTURE, 0.5f)
        player.play(SoundEffect.CELEBRATION, 1f)

        assertEquals(listOf("12 capture 0.5", "12 celebration 1.0"), log.readLines())
    }
}
