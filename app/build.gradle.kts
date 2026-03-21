plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.jetbrainsKotlinAndroid)
}

android {
    namespace = "io.bropines.wiresocks"
    compileSdk = 35 // Обновлено до Android 15

    signingConfigs {
        create("release") {
            // Эти свойства берутся из параметров, которые мы передаем в GitHub Actions
            storeFile = file(project.findProperty("ORG_GRADLE_PROJECT_KEYSTORE_FILE") ?: "wiresocks.jks")
            storePassword = project.findProperty("ORG_GRADLE_PROJECT_KEYSTORE_PASSWORD") as String?
            keyAlias = project.findProperty("ORG_GRADLE_PROJECT_KEY_ALIAS") as String?
            keyPassword = project.findProperty("ORG_GRADLE_PROJECT_KEY_PASSWORD") as String?
        }
    }

    defaultConfig {
        applicationId = "io.bropines.wiresocks"
        minSdk = 24
        targetSdk = 35 // Обновлено до Android 15
        versionCode = 4
        versionName = "1.0.6"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true // Дополнительная оптимизация размера
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.10"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

dependencies {
    implementation(project(":appctr"))
    
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.navigation:navigation-compose:2.7.7")
    implementation("androidx.compose.material:material-icons-extended:1.6.7")
}