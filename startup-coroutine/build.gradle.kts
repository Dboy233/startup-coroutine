plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    `maven-publish`
}

android {
    namespace = "com.dboy.startup.coroutine"
    compileSdk = 36

    defaultConfig {
        minSdk = 21

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit)
    testImplementation(libs.truth.v145)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Dboy233"
            artifactId = "startup-coroutine"
            version = "0.1.0-deta"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}