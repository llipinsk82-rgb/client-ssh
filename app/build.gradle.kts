import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseKeystoreFile = layout.projectDirectory.file("signing/client-ssh-release.jks").asFile
val releaseStorePassword = System.getenv("CLIENT_SSH_RELEASE_STORE_PASSWORD").orEmpty()
val releaseKeyAlias = System.getenv("CLIENT_SSH_RELEASE_KEY_ALIAS").orEmpty()
val releaseKeyPassword = System.getenv("CLIENT_SSH_RELEASE_KEY_PASSWORD").orEmpty()
val releaseSigningAvailable = releaseKeystoreFile.exists() &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

android {
    namespace = "eu.blackserv.clientssh"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.blackserv.clientssh"
        minSdk = 26
        targetSdk = 36
        versionCode = 37
        versionName = "0.3.4"
    }

    signingConfigs {
        if (releaseSigningAvailable) {
            create("releaseSecure") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            // Debug builds always use Android's generated debug key.
            // Release credentials are never exposed to pull-request builds.
        }
        release {
            isMinifyEnabled = false
            if (releaseSigningAvailable) {
                signingConfig = signingConfigs.getByName("releaseSecure")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/DEPENDENCIES",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.github.mwiede:jsch:2.28.2")
    // JSch uses Bouncy Castle for Ed25519 and encrypted PuTTY PPK v3 keys on Android.
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
