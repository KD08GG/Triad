plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.triad"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.triad"
        minSdk = 24
        targetSdk = 36
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
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    //Conexion a Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.12.0"))


    //Google Sign In
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    // Facebook SDK
    implementation("com.facebook.android:facebook-login:17.0.0")

    // GitHub
    implementation("com.google.firebase:firebase-auth")

    // Firebase
    implementation("com.google.firebase:firebase-firestore")

    // Biometicos
    implementation("androidx.biometric:biometric:1.1.0")

    // Notificaciones
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // Maps
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")

}