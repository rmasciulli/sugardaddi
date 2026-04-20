import java.util.Properties;

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

android {
    namespace = "li.masciul.sugardaddi"
    compileSdk = 35

    defaultConfig {
        applicationId = "li.masciul.sugardaddi"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        javaCompileOptions {
            annotationProcessorOptions {
                arguments["room.schemaLocation"] = "$projectDir/schemas"
            }
        }

        // USDA FoodData Central API key
        // Reads from local.properties → env var → DEMO_KEY fallback.
        // Never hardcode a real key here.
        val usdaApiKey: String = localProperties.getProperty("USDA_API_KEY")
            ?: System.getenv("USDA_API_KEY")
            ?: "DEMO_KEY"
        buildConfigField("String", "USDA_API_KEY", "\"$usdaApiKey\"")
    }

    androidResources {
        localeFilters += listOf("en", "fr")
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "META-INF/LICENSE",
                "META-INF/NOTICE"
            )
        }
    }
}

dependencies {
    // AndroidX Core
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.recyclerview)

    // ML Kit
    implementation(libs.play.services.code.scanner)
    implementation(libs.mlkit.language.id)

    // Network & JSON
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.gson)

    // Database
    implementation(libs.room.runtime)
    annotationProcessor(libs.room.compiler)

    // UI Components
    implementation(libs.flexbox)

    // Image loading
    implementation(libs.glide)
    annotationProcessor(libs.glide.compiler)
    implementation(libs.glide.okhttp3.integration)

    // XML parsing for Ciqual
    implementation(libs.simple.xml)

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}