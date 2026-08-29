import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        FileInputStream(file).use { load(it) }
    }
}

android {
    namespace = "com.hidenobunagai.linkbridge"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.hidenobunagai.linkbridge"
        minSdk = 30
        targetSdk = 36
        versionCode = 3
        versionName = "1.2"
    }

    signingConfigs {
        val storeFileProp = keystoreProperties.getProperty("storeFile")
        if (storeFileProp != null) {
            create("release") {
                storeFile = rootProject.file(storeFileProp)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let {
                signingConfig = it
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.activity:activity:1.13.0")
    implementation("com.google.android.material:material:1.13.0")
    implementation("dev.rikka.shizuku:api:13.1.0")
    implementation("dev.rikka.shizuku:provider:13.1.0")
    testImplementation("junit:junit:4.13.2")
}
