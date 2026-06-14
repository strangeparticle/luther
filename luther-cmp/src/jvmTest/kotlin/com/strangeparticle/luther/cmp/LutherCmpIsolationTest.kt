package com.strangeparticle.luther.cmp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class LutherCmpIsolationTest {
    @Test
    fun lutherCmpPackageHasNoSpringboardImports() {
        val root = lutherCmpSourceRoot()
        val offenders = root.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file -> file.readLines().any { it.trimStart().startsWith("import com.strangeparticle.springboard") } }
            .map { it.path }
            .toList()
        assertTrue(offenders.isEmpty(), "luther-cmp must not import springboard:\n${offenders.joinToString("\n")}")
    }

    private fun lutherCmpSourceRoot(): File {
        // The jvmTest working directory differs by setup; try the likely roots and use the first that exists.
        val candidates = listOf(
            File("src/commonMain/kotlin/com/strangeparticle/luther/cmp"),
            File("shared/src/commonMain/kotlin/com/strangeparticle/luther/cmp"),
        )
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate luther-cmp source root from working dir ${File(".").absolutePath}")
    }
}
