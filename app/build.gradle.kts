plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.github.jschbe.dadablubb"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.github.jschbe.dadablubb"
        minSdk = 28
        targetSdk = 37
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    // Language switching must also work offline after installation from an app bundle.
    bundle {
        language {
            enableSplit = false
        }
    }
    testOptions {
        unitTests.all {
            it.systemProperty("dada.asset", file("src/main/assets/dada.json").absolutePath)
            it.inputs.file(file("src/main/assets/dada.json")).withPropertyName("bundledDada")
            it.systemProperty("app.resources", file("src/main/res").absolutePath)
            it.inputs.dir(file("src/main/res/values")).withPropertyName("englishResources")
            it.inputs.dir(file("src/main/res/values-de")).withPropertyName("germanResources")
        }
    }
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
