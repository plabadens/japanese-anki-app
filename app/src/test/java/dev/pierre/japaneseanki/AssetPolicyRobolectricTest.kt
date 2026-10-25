package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.security.DigestInputStream
import java.security.MessageDigest
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [37], qualifiers = "w390dp-h844dp-xxhdpi")
class AssetPolicyAndroidTest {
    @Test fun bundledNhkIndexResolvesHeadwordWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        var byteCount = 0L
        val digest = MessageDigest.getInstance("SHA-256")
        context.assets.open(Nhk2016PronunciationSource.ASSET_NAME).use { stream ->
            DigestInputStream(stream, digest).use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    byteCount += count
                }
            }
        }
        assertEquals(36_971_461L, byteCount)
        assertEquals(
            "9d9714b79c15ae6586d053665bf6cd0b881fcd56ed42ed3b4d758ea0499f1137",
            digest.digest().joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') },
        )

        val source = Nhk2016PronunciationSource(
            readIndexText = {
                context.assets.open(Nhk2016PronunciationSource.ASSET_NAME)
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
            },
            mediaRoot = "http://must-not-be-used",
        )
        assertEquals("20170726141547.ogg", source.findRecording("猫"))
        assertEquals("20170712154846.ogg", source.findRecording("新しい"))
    }
}
