import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

// Build facts shown on the licenses screen, so a release can be matched to its source.
val sfScript = rootProject.file("scripts/build-stockfish.sh").readText()
fun sfVar(name: String) = Regex("""^$name=(\S+)""", RegexOption.MULTILINE).find(sfScript)!!.groupValues[1]
val engineFile = file("src/main/jniLibs/arm64-v8a/libstockfish.so")
val engineSha = if (engineFile.exists()) {
    MessageDigest.getInstance("SHA-256").digest(engineFile.readBytes()).joinToString("") { "%02x".format(it) }
} else "missing"
val gitCommit = providers.exec { commandLine("git", "rev-parse", "HEAD") }.standardOutput.asText.get().trim()
val gitDirty = providers.exec { commandLine("git", "status", "--porcelain") }.standardOutput.asText.get().isNotBlank()

android {
    namespace = "com.uncosens.rocketchess"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.uncosens.rocketchess"
        // Stockfish NDK build targets API 29.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit${if (gitDirty) "-dirty" else ""}\"")
        buildConfigField("String", "STOCKFISH_TAG", "\"${sfVar("SF_TAG")}\"")
        buildConfigField("String", "STOCKFISH_COMMIT", "\"${sfVar("SF_COMMIT")}\"")
        buildConfigField("String", "ENGINE_SHA256", "\"$engineSha\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Extract libstockfish.so to nativeLibraryDir so it can be run as a process.
    packaging { jniLibs { useLegacyPackaging = true } }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("com.github.bhlangonijr:chesslib:1.3.7")
    implementation("com.caverock:androidsvg-aar:1.4")
    implementation("com.squareup.okhttp3:okhttp:5.5.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    ksp("androidx.room:room-compiler:2.8.5")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
}
