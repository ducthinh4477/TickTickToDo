plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "hcmute.edu.vn.tickticktodo"
    compileSdk = 35

    defaultConfig {
        applicationId = "hcmute.edu.vn.tickticktodo"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        buildConfig = false
        viewBinding = true
    }

    lint {
        // Existing localization debt should not block CI/build while feature work continues.
        disable += "MissingTranslation"
    }
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))
    implementation("com.google.firebase:firebase-analytics")

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.androidx.navigation.fragment)
    implementation(libs.androidx.navigation.ui)
    annotationProcessor(libs.room.compiler)

    // Lifecycle (ViewModel + LiveData)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)

    // LocalBroadcastManager (tách ra khỏi support library từ AndroidX)
    implementation("androidx.localbroadcastmanager:localbroadcastmanager:1.1.0")

    // WorkManager
    implementation("androidx.work:work-runtime:2.9.0")

    // Biweekly for parsing iCal / iCalendar (.ics) files
    implementation("net.sf.biweekly:biweekly:0.6.6")

    // Google Gemini SDK (Java Support)
    implementation("com.google.ai.client.generativeai:generativeai:0.8.0")
    implementation("org.reactivestreams:reactive-streams:1.0.4")
    implementation("com.google.guava:guava:32.1.3-android")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-guava:1.7.3")

    testImplementation(libs.junit)
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.json:json:20240303")
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
}