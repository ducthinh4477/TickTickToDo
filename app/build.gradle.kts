plugins {
    alias(libs.plugins.android.application)
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
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.recyclerview:recyclerview:1.3.2")

    // Room Database
    implementation(libs.room.runtime)
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

    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}