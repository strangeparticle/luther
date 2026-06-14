plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
}

// group + version for every published module. The version is the single-source-of-truth
// `lutherVersion` from gradle.properties (see docs/README_Versions.md).
val lutherVersion: String = providers.gradleProperty("lutherVersion").get()

allprojects {
    group = "com.strangeparticle"
    version = lutherVersion
}
