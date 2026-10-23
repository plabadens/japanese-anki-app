package dev.pierre.japaneseanki

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyCatalogueTest {

    @Test
    fun uiCopyIsResourceBackedAndLegacyResourcesAreUsed() {
        val root = projectRoot()
        val sourceFiles = buildList {
            addAll(root.resolve("app/src/main/java/dev/pierre/japaneseanki/ui").walkTopDown()
                .filter { it.isFile && it.extension == "kt" }.toList())
            add(root.resolve("app/src/main/java/dev/pierre/japaneseanki/MainActivity.kt"))
            add(root.resolve("app/src/main/java/dev/pierre/japaneseanki/PitchPatternDiagram.kt"))
        }
        val allSource = sourceFiles.associate { it to read(it) }
        val resourceIds = resourceIds(root)
        val referencedIds = allSource.values.flatMap { source ->
            Regex("\\bR\\.string\\.([a-z][a-z0-9_]*)").findAll(source).map { it.groupValues[1] }.toList()
        }.toSet()
        assertTrue("Source refers to missing resources: ${referencedIds - resourceIds}", resourceIds.containsAll(referencedIds))

        val remainingStaticText = allSource.flatMap { (file, source) ->
            val literalPatterns = listOf(
                Regex("\\bText\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"") ,
                Regex("\\btext\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"") ,
                Regex("\\b(?:imageStatus|meaningStatus|continueHint|sendHint)\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"") ,
            )
            literalPatterns.flatMap { pattern ->
                pattern.findAll(source).mapNotNull { match ->
                    val literal = match.groupValues[1]
                    val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                    val sourceData = literal == "  ${'$'}detail" || literal.contains("voice.displayName") ||
                        literal.contains("entry.expression") || literal.contains("block.text")
                    if ('\u0024' in literal && sourceData) null
                    else "${file.relativeTo(root)}:$line: $literal"
                }.toList()
            }
        }
        assertTrue(
            "Hardcoded Text literals remain (only interpolated source/data may stay):\n${remainingStaticText.joinToString("\n")}",
            remainingStaticText.isEmpty(),
        )

        val hardcodedSemantics = allSource.flatMap { (file, source) ->
            Regex("(?:contentDescription|stateDescription)\\s*=\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .findAll(source).map { match ->
                    val line = source.substring(0, match.range.first).count { it == '\n' } + 1
                    "${file.relativeTo(root)}:$line: ${match.groupValues[1]}"
                }.toList()
        }
        assertTrue("Hardcoded accessibility strings remain:\n${hardcodedSemantics.joinToString("\n")}", hardcodedSemantics.isEmpty())

        val sourceLegacyIds = referencedIds.filter { it.startsWith("legacy_") }.toSet()
        val legacyResourceIds = resourceIds.filter { it.startsWith("legacy_") }.toSet()
        assertEquals("Legacy resources must be referenced", sourceLegacyIds, legacyResourceIds)

        assertTrue("Obsolete card-surface resources remain", resourceIds.none { it.startsWith("legacy_card_") })
    }


    private fun resourceIds(root: File): Set<String> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val document = factory.newDocumentBuilder().parse(root.resolve("app/src/main/res/values/strings.xml"))
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length).map { nodes.item(it).attributes.getNamedItem("name").nodeValue }.toSet()
    }


    private fun read(file: File): String = Files.readString(file.toPath(), StandardCharsets.UTF_8)

    private fun projectRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "app/src/main/res/values/strings.xml").isFile) return current
            current = current.parentFile
        }
        error("Could not locate the repository root from ${System.getProperty("user.dir")}")
    }
}
