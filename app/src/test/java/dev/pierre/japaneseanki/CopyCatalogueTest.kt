package dev.pierre.japaneseanki

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyCatalogueTest {
    @Test
    fun contractAndFailureCatalogueIdsHaveResourcesAndRetiredIdsDoNot() {
        val root = projectRoot()
        val contract = read(root.resolve("docs/card-flow-contract.md"))
        val flow = read(root.resolve("docs/sentence-mining-flow.md"))
        val resourceIds = resourceIds(root)

        val delta = section(contract, "## 10. String delta", "## 11.")
        val deltaRows = delta.lines().mapNotNull { line ->
            Regex("^\\|\\s*(Add|Remove)\\s*\\|\\s*`?([a-z][a-z0-9_]*)`?\\s*\\|")
                .find(line)?.let { it.groupValues[1] to it.groupValues[2] }
        }
        val added = deltaRows.filter { it.first == "Add" }.map { it.second }.toSet()
        val removed = deltaRows.filter { it.first == "Remove" }.map { it.second }.toSet() + "capture"
        assertEquals(
            setOf("card", "card_hint_word", "card_hint_preparing", "update_card", "torch"),
            added,
        )
        assertTrue("Missing contract additions: ${added - resourceIds}", resourceIds.containsAll(added))
        assertTrue("Retired IDs remain: ${removed intersect resourceIds}", (removed intersect resourceIds).isEmpty())

        val staticCatalogue = section(flow, "## Shared static-copy catalogue", "### Failure and recovery copy")
        val failures = section(flow, "### Failure and recovery copy", "## Import verification")
        val staticIds = tableIds(staticCatalogue)
        val failureIds = tableIds(failures)
        val retired = removed + setOf("use_mobile_body", "use_app")
        val cataloguedIds = (staticIds + failureIds + added) - retired
        assertTrue(
            "Catalogue IDs missing from strings.xml: ${cataloguedIds - resourceIds}",
            resourceIds.containsAll(cataloguedIds),
        )
    }

    @Test
    fun uiCopyIsResourceBackedAndEveryLegacyReferenceIsDocumented() {
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
        val legacyDoc = read(root.resolve("docs/legacy-copy.md"))
        val documentedLegacyIds = legacyDoc.lines().mapNotNull { line ->
            Regex("""^\|\s*`(legacy_[a-z0-9_]+)`\s*\|""").find(line)?.groupValues?.get(1)
        }.toSet()
        assertEquals(
            "Legacy resource/source list mismatch. Missing docs: ${sourceLegacyIds - documentedLegacyIds}; undocumented: ${documentedLegacyIds - sourceLegacyIds}",
            sourceLegacyIds,
            documentedLegacyIds,
        )
        val legacyResourceIds = resourceIds.filter { it.startsWith("legacy_") }.toSet()
        assertEquals("Legacy resources must be documented and referenced", sourceLegacyIds, legacyResourceIds)
        assertTrue("Legacy resources missing from strings.xml", resourceIds.containsAll(documentedLegacyIds))

        val target = root.resolve("app/src/main/java/dev/pierre/japaneseanki/ui/TargetScreen.kt")
        val review = root.resolve("app/src/main/java/dev/pierre/japaneseanki/ui/ReviewCard.kt")
        if (!target.exists() && !review.exists()) {
            assertTrue("Card-surface legacy resources remain after the Target/Review owner lands", resourceIds.none { it.startsWith("legacy_card_") })
        }
    }

    private fun tableIds(table: String): Set<String> = table.lines().mapNotNull { line ->
        Regex("^\\|\\s*([a-z][a-z0-9_]*)\\s*\\|").find(line)?.groupValues?.get(1)
    }.toSet()

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

    private fun section(document: String, start: String, end: String): String {
        val startIndex = document.indexOf(start)
        require(startIndex >= 0) { "Missing document section: $start" }
        val contentStart = startIndex + start.length
        val endIndex = document.indexOf(end, contentStart)
        require(endIndex >= 0) { "Missing end marker: $end" }
        return document.substring(contentStart, endIndex)
    }

    private fun read(file: File): String = Files.readString(file.toPath(), StandardCharsets.UTF_8)

    private fun projectRoot(): File {
        var current = File(System.getProperty("user.dir")).absoluteFile
        while (current.parentFile != null) {
            if (File(current, "docs/card-flow-contract.md").isFile) return current
            current = current.parentFile
        }
        error("Could not locate the repository root from ${System.getProperty("user.dir")}")
    }
}
