import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val updateKeystoreFile = layout.projectDirectory.file("signing/client-ssh-update.jks").asFile

android {
    namespace = "eu.blackserv.clientssh"
    compileSdk = 36

    defaultConfig {
        applicationId = "eu.blackserv.clientssh"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.2.5"
    }

    signingConfigs {
        create("update") {
            storeFile = updateKeystoreFile
            storePassword = "android"
            keyAlias = "blackserv-update"
            keyPassword = "android"
        }
    }

    buildTypes {
        debug {
            if (updateKeystoreFile.exists()) {
                signingConfig = signingConfigs.getByName("update")
            }
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("update")
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

    val composeBom = platform("androidx.compose:compose-bom:2025.12.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
