package dev.pierre.japaneseanki

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ImageMediaProcessorTest {
    @Test fun acceptsStaticAvifMajorOrCompatibleBrand() {
        withHeader { file -> assertTrue(hasStaticAvifFileType(file)) }
        withHeader(majorBrand = "mif1", compatibleBrand = "avif") { file ->
            assertTrue(hasStaticAvifFileType(file))
        }
    }

    @Test fun rejectsNonAvifAndImageSequenceHeaders() {
        withHeader(majorBrand = "jpeg") { file -> assertFalse(hasStaticAvifFileType(file)) }
        withHeader(majorBrand = "avis") { file -> assertFalse(hasStaticAvifFileType(file)) }
    }

    private fun withHeader(
        majorBrand: String = "avif",
        compatibleBrand: String = "mif1",
        block: (File) -> Unit,
    ) {
        val file = File.createTempFile("image-header-", ".bin")
        try {
            file.writeBytes(
                byteArrayOf(
                    0, 0, 0, 20, // ftyp box size
                    'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
                    majorBrand[0].code.toByte(), majorBrand[1].code.toByte(),
                    majorBrand[2].code.toByte(), majorBrand[3].code.toByte(),
                    0, 0, 0, 0, // minor version
                    compatibleBrand[0].code.toByte(), compatibleBrand[1].code.toByte(),
                    compatibleBrand[2].code.toByte(), compatibleBrand[3].code.toByte(),
                ),
            )
            block(file)
        } finally {
            file.delete()
        }
    }
}
