import org.gradle.api.configuration.BuildFeatures
import javax.inject.Inject

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class SigningBuildFeatures {
    @get:Inject
    abstract val buildFeatures: BuildFeatures
}

val uploadSigningVariables = listOf(
    "DADABLUBB_UPLOAD_STORE_FILE",
    "DADABLUBB_UPLOAD_STORE_PASSWORD",
    "DADABLUBB_UPLOAD_KEY_ALIAS",
    "DADABLUBB_UPLOAD_KEY_PASSWORD",
)
val uploadSigningValues = uploadSigningVariables.associateWith {
    providers.environmentVariable(it).orNull?.takeIf(String::isNotBlank)
}
val missingUploadSigningVariables = uploadSigningVariables.filter { uploadSigningValues[it] == null }

// AGP's signing DSL holds passwords in memory; never serialize them into a configuration cache.
val configurationCacheRequested = objects.newInstance<SigningBuildFeatures>()
    .buildFeatures.configurationCache.requested.getOrElse(false)
if (uploadSigningValues.values.any { it != null } && configurationCacheRequested) {
    throw GradleException("Upload signing requires --no-configuration-cache. See README.md for the release workflow.")
}

val validateReleaseSigningEnvironment = tasks.register("validateReleaseSigningEnvironment") {
    group = "verification"
    description = "Checks required upload-signing variable names without opening the keystore."
    // Only missing variable names are task inputs, never credentials.
    inputs.property("missingVariables", missingUploadSigningVariables)
    doLast {
        val missing = inputs.properties.getValue("missingVariables") as List<*>
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing is not configured. Missing or blank environment variables: " +
                    missing.joinToString() +
                    ". Supply them outside Codex and use --no-configuration-cache; see README.md. " +
                    "No unsigned or debug-key release fallback is allowed."
            )
        }
    }
}

// Guard both the release lifecycle and the AAB packaging task before an artifact can be produced.
tasks.configureEach {
    if (name in setOf("preReleaseBuild", "validateSigningRelease", "packageReleaseBundle")) {
        dependsOn(validateReleaseSigningEnvironment)
    }
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
        versionCode = 6
        versionName = "1.2.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("upload") {
            storeFile = uploadSigningValues["DADABLUBB_UPLOAD_STORE_FILE"]?.let { file(it) }
            storePassword = uploadSigningValues["DADABLUBB_UPLOAD_STORE_PASSWORD"]
            keyAlias = uploadSigningValues["DADABLUBB_UPLOAD_KEY_ALIAS"]
            keyPassword = uploadSigningValues["DADABLUBB_UPLOAD_KEY_PASSWORD"]
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("upload")
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
            it.inputs.dir(file("src/main/res/xml")).withPropertyName("xmlResources")
            it.systemProperty("app.manifest", file("src/main/AndroidManifest.xml").absolutePath)
            it.inputs.file(file("src/main/AndroidManifest.xml")).withPropertyName("manifest")
            it.systemProperty("privacy.policy", rootProject.file("docs/privacy.html").absolutePath)
            it.inputs.file(rootProject.file("docs/privacy.html")).withPropertyName("privacyPolicy")
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
