import java.io.File
import java.util.Properties
import java.security.MessageDigest
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification
import org.gradle.testing.jacoco.tasks.JacocoReport

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    jacoco
}

// ── Secretos (LOCAL, gitignored). Ver secrets.properties.template y SECURITY.md SEC-004/005 ─
val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) secretsFile.inputStream().use { load(it) }
}
fun secret(key: String, default: String = "") = secrets.getProperty(key, default)

val atlasApplicationId = "com.atlaspeak"
val atlasVersionCode = 109
val atlasVersionName = "V-01.09"

fun sha256Hex(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read == -1) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }.uppercase()
}

// ── Firma de release (LOCAL, gitignored). Ver SPEC.md §8.5 ──────────────────────────────────
val keystoreFile = System.getenv("ATLAS_PEAK_KEYSTORE_PROPERTIES")
    ?.takeIf { it.isNotBlank() }
    ?.let { file(it) }
    ?: rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}
fun keystoreStoreFile(): File {
    val configured = keystoreProps.getProperty("storeFile").orEmpty()
    val storeFile = File(configured)
    return if (storeFile.isAbsolute) storeFile else keystoreFile.parentFile.resolve(configured)
}

android {
    namespace = "com.atlaspeak"
    compileSdk = 36

    defaultConfig {
        applicationId = atlasApplicationId
        minSdk = 31
        targetSdk = 35
        versionCode = atlasVersionCode
        versionName = atlasVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Maps API key inyectada en el manifest (NUNCA hardcodeada). SEC-005.
        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")

        // OAuth Web Client ID accesible via BuildConfig. SEC-004.
        buildConfigField("String", "OAUTH_WEB_CLIENT_ID", "\"${secret("OAUTH_WEB_CLIENT_ID")}\"")

        // Room: exportar schema para trackear migraciones. SPEC.md §4.
        ksp { arg("room.schemaLocation", "$projectDir/schemas") }

        vectorDrawables { useSupportLibrary = true }
    }

    androidResources {
        localeFilters += listOf("es", "en")
    }

    signingConfigs {
        if (keystoreFile.exists()) {
            create("release") {
                storeFile = keystoreStoreFile()
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystoreFile.exists()) signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    sourceSets["main"].kotlin.srcDir("src/main/kotlin")

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
    }

    testOptions {
        unitTests.all { it.useJUnitPlatform() } // JUnit5
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.datastore.preferences)

    // Lifecycle
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.service)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Compose (BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // Room + SQLCipher
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.androidx.sqlite.ktx)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Seguridad
    implementation(libs.androidx.security.crypto)

    // Health Connect
    implementation(libs.androidx.health.connect.client)

    // Gráficos
    implementation(libs.vico.compose.m3)

    // Google Drive OAuth
    implementation(libs.play.services.auth)

    // Red (solo Drive REST v3)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.kotlinx)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging) // SOLO se instala el interceptor en BuildConfig.DEBUG
    implementation(libs.kotlinx.serialization.json)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Location + Maps
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Testing
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.work.testing)
}

jacoco {
    toolVersion = libs.versions.jacoco.get()
}

val coverageExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*_MembersInjector.*",
    "**/*_Factory*.*",
    "**/*Module*.*",
    "**/*Hilt*.*",
    "**/Hilt_*.*",
    "**/*Dao_Impl*.*",
    "**/*Database_Impl*.*",
    "**/*JsonAdapter*.*",
    "**/*\$serializer*.*",
    "**/*\$Companion.*",
    "**/*\$DefaultImpls.*",
    "**/data/db/dao/**",
    "**/data/db/entity/**",
    "**/data/repository/Room*.*",
    "**/data/notification/*Worker*.*",
    "**/data/notification/AtlasPeakNotificationHelper*.*",
    "**/data/notification/WorkManagerNotificationScheduler*.*",
    "**/data/healthconnect/HealthConnectManager*.*",
    "**/data/location/LocationTracker*.*",
    "**/data/backup/BackupWorker.*",
    "**/data/backup/BackupWorkScheduler*.*",
    "**/data/backup/BackupCredentialStore*.*",
    "**/data/security/DatabasePassphraseProvider*.*",
    "**/data/drive/DriveApiService*.*",
    "**/data/drive/GoogleDriveAccessTokenProvider*.*",
)
val domainDataIncludes = listOf(
    "com/atlaspeak/domain/**",
    "com/atlaspeak/data/**",
)
fun domainDataClassDirectories() = files(
    fileTree("${layout.buildDirectory.get().asFile}/tmp/kotlin-classes/debug") {
        include(domainDataIncludes)
        exclude(coverageExcludes)
    },
    fileTree("${layout.buildDirectory.get().asFile}/intermediates/javac/debug/compileDebugJavaWithJavac/classes") {
        include(domainDataIncludes)
        exclude(coverageExcludes)
    },
)
fun debugUnitTestExecutionData() = fileTree(layout.buildDirectory) {
    include(
        "jacoco/testDebugUnitTest.exec",
        "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec",
    )
}

tasks.register("packageReleaseUpdate") {
    dependsOn("assembleRelease")
    group = "distribution"
    description = "Generates a signed release APK update package that preserves installed app data."
    notCompatibleWithConfigurationCache("Writes release distribution files from project paths during execution.")

    doLast {
        if (atlasApplicationId != "com.atlaspeak") {
            throw org.gradle.api.GradleException(
                "Refusing to package an update for '$atlasApplicationId'. The release app must stay com.atlaspeak.",
            )
        }
        if (!keystoreFile.exists()) {
            throw org.gradle.api.GradleException(
                "Release signing is required. Set ATLAS_PEAK_KEYSTORE_PROPERTIES to an external keystore.properties file.",
            )
        }

        val releaseApk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        if (!releaseApk.isFile) {
            throw org.gradle.api.GradleException("Release APK not found: ${releaseApk.absolutePath}")
        }

        val distributionDir = rootProject.layout.buildDirectory.dir("distribution").get().asFile
        distributionDir.mkdirs()

        val outputApkName = "AtlasPeak-$atlasVersionName-release.apk"
        val outputApk = distributionDir.resolve(outputApkName)
        releaseApk.copyTo(outputApk, overwrite = true)

        val checksum = sha256Hex(outputApk)
        distributionDir.resolve("SHA256SUMS.txt").writeText("$checksum  $outputApkName\r\n")
        distributionDir.resolve("install-adb.bat").writeText(
            """
            @echo off
            setlocal
            set "APK=%~dp0$outputApkName"

            echo Checking connected Android devices...
            adb devices
            echo.
            echo Updating $atlasApplicationId with %APK%
            echo This uses adb install -r, so Android keeps the existing app data.
            adb install -r "%APK%"
            """.trimIndent().replace("\n", "\r\n"),
        )
        distributionDir.resolve("README-INSTALACION.txt").writeText(
            """
            Atlas Peak $atlasVersionName - APK release/update

            APK:
              $outputApkName

            Quick update with ADB:
              1. Enable Developer options on the phone.
              2. Enable USB debugging.
              3. Connect the phone by USB and accept the RSA prompt.
              4. Run:
                   install-adb.bat

            Manual update:
              1. Copy $outputApkName to the phone.
              2. Open the APK from the phone.
              3. Confirm the update prompt.

            Data preservation rules:
              - Do not uninstall the old app first. Uninstalling deletes local app data.
              - This APK updates the existing app only because the package is $atlasApplicationId.
              - Android keeps data only when the APK is signed with the same release key and has a higher versionCode.
              - If Android reports INSTALL_FAILED_UPDATE_INCOMPATIBLE, the APK was signed with another key. Stop and rebuild with the original keystore.
              - Debug builds use a different package and do not update the release app.

            Version:
              versionName $atlasVersionName
              versionCode $atlasVersionCode

            SHA-256:
              $checksum
            """.trimIndent().replace("\n", "\r\n"),
        )

        logger.lifecycle("Release update package ready: ${outputApk.absolutePath}")
        logger.lifecycle("SHA-256: $checksum")
    }
}

tasks.register<JacocoReport>("jacocoDebugDomainDataReport") {
    dependsOn("testDebugUnitTest")
    group = "verification"
    description = "Generates JaCoCo coverage for JVM-testable domain and data debug unit tests."

    classDirectories.setFrom(domainDataClassDirectories())
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    executionData.setFrom(debugUnitTestExecutionData())

    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.register<JacocoCoverageVerification>("jacocoDebugDomainDataCoverageVerification") {
    dependsOn("jacocoDebugDomainDataReport")
    group = "verification"
    description = "Fails when JVM-testable domain and data line coverage drops below 70 percent."

    classDirectories.setFrom(domainDataClassDirectories())
    sourceDirectories.setFrom(files("src/main/kotlin", "src/main/java"))
    executionData.setFrom(debugUnitTestExecutionData())

    violationRules {
        rule {
            element = "BUNDLE"
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.70".toBigDecimal()
            }
        }
    }
}
