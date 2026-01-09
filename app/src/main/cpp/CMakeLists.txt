plugins {
    id 'com.android.application'
    id 'org.jetbrains.kotlin.android'
}

android {
    namespace 'io.canccode.aca'
    compileSdk 34

    defaultConfig {
        applicationId "io.canccode.aca"
        minSdk 26
        targetSdk 34
        versionCode 1
        versionName "1.0"

        ndk {
            abiFilters "arm64-v8a"
        }

        externalNativeBuild {
            cmake {
                cppFlags "-std=c++17"
            }
        }
    }

    buildTypes {
        debug {
            debuggable true
        }
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile(
                    'proguard-android-optimize.txt'),
                    'proguard-rules.pro'
        }
    }

    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    externalNativeBuild {
        cmake {
            path "src/main/cpp/CMakeLists.txt"
            // 🚫 DO NOT pin cmakeVersion
        }
    }

    buildFeatures {
        viewBinding true
    }
}

dependencies {

    implementation "org.jetbrains.kotlin:kotlin-stdlib:1.9.22"

    implementation 'androidx.core:core-ktx:1.13.1'
    implementation 'androidx.appcompat:appcompat:1.7.0'
    implementation 'com.google.android.material:material:1.12.0'
    implementation 'androidx.constraintlayout:constraintlayout:2.2.0'

    implementation 'androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.6'
    implementation 'androidx.lifecycle:lifecycle-livedata-ktx:2.8.6'

    implementation 'androidx.fragment:fragment-ktx:1.8.5'
}