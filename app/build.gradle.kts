import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Optional: keep build output outside the project folder (set by scripts/env.sh).
System.getenv("PV_BUILD_DIR")?.let { layout.buildDirectory.set(file(it)) }

android {
    namespace = "com.pharrlabs.voice"
    compileSdk = 35

    defaultConfig {
        // -Pshare builds the neutral copy for other people (empty dictionary, no owner terms).
        // -PappIdSuffix=.fresh installs it next to the owner's build for side-by-side testing.
        val share = project.hasProperty("share")
        applicationId = "com.pharrlabs.voice" + (project.findProperty("appIdSuffix") as String? ?: "")
        buildConfigField("boolean", "SHARE", share.toString())
        // Optional starter dictionary for your own build, read from ~/.pharr-voice/owner-terms.txt as
        // "spoken=Written|spoken=Written". It is not in the repo; without the file the build starts with an empty dictionary.
        val ownerTermsFile = File(System.getProperty("user.home"), ".pharr-voice/owner-terms.txt")
        val ownerTerms = if (ownerTermsFile.isFile) ownerTermsFile.readText().trim().replace("\"", "") else ""
        buildConfigField("String", "OWNER_TERMS", if (share) "\"\"" else "\"$ownerTerms\"")
        minSdk = 31
        targetSdk = 35
        versionCode = 15
        versionName = "0.4.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                // Always optimized, even for debug builds (a Debug whisper.cpp is unusably slow).
                arguments += listOf("-DCMAKE_BUILD_TYPE=Release", "-DANDROID_STL=c++_shared")
            }
        }
    }

    ndkVersion = "27.2.12479018"
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // Release signing: credentials live OUTSIDE the repo in ~/.pharr-voice/keystore.properties.
    val keystoreProps = Properties().apply {
        val f = File(System.getProperty("user.home"), ".pharr-voice/keystore.properties")
        if (f.isFile) f.inputStream().use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        // Debug builds use the release key too, so they update the installed app in place and keep its model and settings.
        debug {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
        release {
            if (keystoreProps.isNotEmpty()) signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { buildConfig = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20231013")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
}
