import java.util.Properties

plugins {
    id("com.android.application")
}

android {
    namespace = "com.deweb.shell"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
        targetSdk = 35
        versionCode = 407
        versionName = "0.1.0"
    }

    flavorDimensions += "channel"

    productFlavors {
        create("prod") {
            dimension = "channel"
            applicationId = "com.deweb.shell"
            resValue("string", "app_name", "DeWEB-Desktop")
            buildConfigField("boolean", "IS_BETA", "false")
            buildConfigField("String", "UPDATE_MANIFEST", "\"https://download.dewebdesk.link/android-latest.json\"")
            buildConfigField("String", "WEB_URL", "\"https://app.dewebdesk.link/?v=local-kernel-2\"")
        }
        create("beta") {
            dimension = "channel"
            applicationId = "com.deweb.shell.beta"
            versionNameSuffix = "-beta"
            resValue("string", "app_name", "DeWEB-Desktop Beta")
            buildConfigField("boolean", "IS_BETA", "true")
            buildConfigField("String", "UPDATE_MANIFEST", "\"https://download.dewebdesk.link/android-latest-beta.json\"")
            buildConfigField("String", "WEB_URL", "\"https://app.dewebdesk.link/?v=local-kernel-2\"")
        }
    }

    val keystorePropsFile = rootProject.file("keystore.properties")
    val hasReleaseSigning = keystorePropsFile.exists()

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                val props = Properties()
                keystorePropsFile.inputStream().use { props.load(it) }
                storeFile = rootProject.file(props.getProperty("storeFile", "../release-signing/deweb-android-release.jks"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".audit"
            versionNameSuffix = "-audit"
        }
        release {
            signingConfig = if (hasReleaseSigning) signingConfigs.getByName("release") else null
            isMinifyEnabled = false
            isShrinkResources = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.12.1")
}
