package dev.pierre.japaneseanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
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
            connectionFactory = { error("Bundled index lookup must not access the network: $it") },
        )
        assertEquals("20170726141547.ogg", source.findRecording("猫"))
        assertEquals("20170712154846.ogg", source.findRecording("新しい"))
    }

    @Test fun bundledJapaneseOcrRecognizesLocalFixtureWithoutNetwork() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val image = File.createTempFile("offline-japanese-ocr-", ".jpg", context.cacheDir)
        try {
            instrumentation.context.assets.open("textbook-specimen.jpg").use { input ->
                image.outputStream().use { output -> input.copyTo(output) }
            }
            val result = JapaneseOcr().recognize(image)
            assertTrue("Bundled model failed on Japanese fixture: ${result.text}", result.text.contains("図書館で本を読みました"))
        } finally {
            image.delete()
        }
    }
}
