plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Values written by Duck Native from native/config.py. The defaults let the
// template open on its own in Android Studio.
fun duck(name: String, default: String): String =
    providers.gradleProperty("duck.$name").orNull ?: default

val javaVersion = JavaVersion.toVersion(duck("javaVersion", "17").toInt())
val generatedDir = duck("generatedDir", "")
val keystore = duck("keystore", "")

// Keep Gradle output inside the project's build folder.
duck("buildDir", "").takeIf { it.isNotEmpty() }?.let {
    layout.buildDirectory.set(file("$it/gradle"))
}

android {
    namespace = "com.duckframework.bridge"
    compileSdk = duck("compileSdk", "36").toInt()
    duck("buildTools", "").takeIf { it.isNotEmpty() }?.let { buildToolsVersion = it }

    defaultConfig {
        applicationId = duck("packageName", "com.duckframework.bridge")
        minSdk = duck("minSdk", "23").toInt()
        targetSdk = duck("targetSdk", "36").toInt()
        versionCode = duck("versionCode", "1").toInt()
        versionName = duck("versionName", "0.1.0")

        buildConfigField("boolean", "FULLSCREEN", duck("fullscreen", "false"))
        buildConfigField("boolean", "KEEP_SCREEN_ON", duck("keepScreenOn", "false"))
    }

    signingConfigs {
        if (keystore.isNotEmpty()) {
            create("release") {
                storeFile = file(keystore)
                storePassword = System.getenv("DUCK_KEYSTORE_PASSWORD")
                keyAlias = duck("keyAlias", "")
                keyPassword = System.getenv("DUCK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }

    // Generated resources and manifest override the template's defaults.
    if (generatedDir.isNotEmpty()) {
        for (type in listOf("debug", "release")) {
            sourceSets.getByName(type) {
                res.srcDir("$generatedDir/res")
                manifest.srcFile("$generatedDir/AndroidManifest.xml")
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = javaVersion
        targetCompatibility = javaVersion
    }

    kotlinOptions {
        jvmTarget = javaVersion.toString()
    }
}

// Intentionally no dependencies: the bridge uses only platform APIs.
