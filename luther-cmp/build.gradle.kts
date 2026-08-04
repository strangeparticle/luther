import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.vanniktechMavenPublish) apply false
}

// luther-cmp is the Compose Multiplatform UI layer. It has no native-desktop targets:
// Compose desktop (macOS/Linux/Windows) is served by the JVM artifact. It depends on
// luther-core via `api` so consumers get the core types transitively.
kotlin {
    jvmToolchain(17)

    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    androidLibrary {
        namespace = "com.strangeparticle.luther.cmp"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            api(project(":luther-core"))
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
            // Multiplatform Compose UI-test API (runComposeUiTest + finders/actions). Structural
            // UI tests live in commonTest so they are multiplatform-capable; they execute on the
            // JVM desktop runner now (see jvmTest), with other platforms expandable later.
            @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
        }
        // The JVM desktop runner that actually executes the commonTest UI tests, plus the
        // JVM-pinned pixel/hover tests (captureToImage/performMouseInput are desktop-reliable).
        jvmTest.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(compose.desktop.uiTestJUnit4)
        }
    }
}

// Publishing to Maven Central via the Central Portal (see docs/RELEASING.md).
// Sign only when a signing key is supplied (the CI release path). This keeps
// publishToMavenLocal usable for local verification without any keys.
val signingKeyPresent = providers.gradleProperty("signingInMemoryKey").isPresent

// Apply the Maven Central publish plugin ONLY when luther builds standalone: as an included (composite) build it triggers a MavenCentralBuildService classloader conflict in KMP consumers (e.g. springboard -PlutherDev).
if (gradle.parent == null) {
    apply(plugin = libs.plugins.vanniktechMavenPublish.get().pluginId)
    configure<com.vanniktech.maven.publish.MavenPublishBaseExtension> {
        publishToMavenCentral()
        if (signingKeyPresent) {
            signAllPublications()
        }

        coordinates(group.toString(), "luther-cmp", version.toString())

        pom {
            name = "luther-cmp"
            description = "Compose Multiplatform chat UI for luther. Depends on (and re-exports) luther-core."
            inceptionYear = "2026"
            url = "https://github.com/strangeparticle/luther"
            licenses {
                license {
                    name = "BSD 3-Clause License"
                    url = "https://opensource.org/license/bsd-3-clause"
                    distribution = "https://opensource.org/license/bsd-3-clause"
                }
            }
            developers {
                developer {
                    id = "strangeparticle"
                    name = "Strange Particle"
                    url = "https://github.com/strangeparticle"
                }
            }
            scm {
                url = "https://github.com/strangeparticle/luther"
                connection = "scm:git:git://github.com/strangeparticle/luther.git"
                developerConnection = "scm:git:ssh://git@github.com/strangeparticle/luther.git"
            }
        }
    }
}

// Bundle the BSD-3-Clause LICENSE into the JVM & KMP-metadata jars (META-INF/LICENSE) so binary
// redistributions carry the copyright notice alongside the bytes (the POM only declares it).
tasks.withType<org.gradle.api.tasks.bundling.Jar>().configureEach {
    from(rootProject.file("LICENSE")) { into("META-INF") }
}
