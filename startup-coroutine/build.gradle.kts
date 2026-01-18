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
    implementation(libs.androidx.lifecycle.livedata.core.ktx)
    testImplementation(libs.mockito.core)
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.truth.v145)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.1.0")
    testImplementation("androidx.lifecycle:lifecycle-runtime-testing:2.6.1")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "com.github.Dboy233"
            artifactId = "startup-coroutine"
            version = "0.2.1-beta"

            afterEvaluate {
                from(components["release"])
            }
        }
    }
}