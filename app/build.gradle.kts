import java.util.Properties
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

// ── Firma de release (LOCAL, gitignored). Ver SPEC.md §8.5 ──────────────────────────────────
val keystoreFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystoreFile.exists()) keystoreFile.inputStream().use { load(it) }
}

android {
    namespace = "com.atlaspeak"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.atlaspeak"
        minSdk = 31
        targetSdk = 35
        versionCode = 102
        versionName = "V-01.02"

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
                storeFile = file(keystoreProps.getProperty("storeFile"))
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
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.mockk.android)
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
