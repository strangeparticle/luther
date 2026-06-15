import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
}

// --- Single-source-of-truth version generation (see docs/README_Versions.md) ---
// Reads `lutherVersion` from gradle.properties and writes LutherVersion.kt into a
// commonMain source dir under build/ (git-ignored). The value is never duplicated in
// tracked source and cannot drift. Passing the task provider to kotlin.srcDir() wires the
// generation to run before any compilation that consumes commonMain.
val lutherVersion: String = providers.gradleProperty("lutherVersion").get()
val generatedVersionDir = layout.buildDirectory.dir("generated/lutherVersion")
val generateKotlinVersionFile = tasks.register("GenerateKotlinVersionFile") {
    val outputDir = generatedVersionDir
    val versionValue = lutherVersion
    inputs.property("lutherVersion", versionValue)
    outputs.dir(outputDir)
    doLast {
        val packageDir = outputDir.get().dir("com/strangeparticle/luther/core").asFile
        packageDir.mkdirs()
        packageDir.resolve("LutherVersion.kt").writeText(
            """
            package com.strangeparticle.luther.core

            /** Generated from `lutherVersion` in gradle.properties — do not edit by hand. */
            public object LutherVersion {
                public const val VERSION: String = "$versionValue"
            }
            """.trimIndent() + "\n",
        )
    }
}

kotlin {
    jvmToolchain(17)

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.strangeparticle.luther.core"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    macosArm64()
    macosX64()
    linuxX64()
    mingwX64()

    sourceSets {
        commonMain {
            kotlin.srcDir(generateKotlinVersionFile)
            dependencies {
                // These appear in luther-core's PUBLIC API and so are exposed transitively:
                // CoroutineScope (createLutherSession), JsonObject/Json (ToolCallHandler,
                // ToolDefinition, ToolCallHandlerResponse), HttpClient (LutherBuiltInProviders.all).
                api(libs.kotlinx.coroutines.core)
                api(libs.kotlinx.serialization.json)
                api(libs.ktor.client.core)
                implementation(libs.kotlinx.datetime)
            }
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}
