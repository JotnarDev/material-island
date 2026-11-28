plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.jotnar.materialisland"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "dev.jotnar.materialisland"
        minSdk = 31
        targetSdk = 36
        versionCode = 5
        versionName = "1.2.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Gradle firma con la clave de debug; la APK que se publica se vuelve a firmar con la clave
            // de release + linaje de rotación (scripts/sign-apk.sh, lo hace el workflow Release). Ver docs/RELEASING.md.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    // Dos versiones:
    //  · foss: la de GitHub, Obtainium, F-Droid e IzzyOnDroid. Sin anuncios ni código de Google; se actualiza sola.
    //  · play: para Google Play. Anuncios solo en las pantallas de ajustes (nunca en la isla); la actualiza Play.
    flavorDimensions += "store"
    productFlavors {
        create("foss") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
        }
        create("play") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
            // IDs de PRUEBA de AdMob. Antes de publicar en Play, cambiarlos por los de tu cuenta de AdMob.
            manifestPlaceholders["admobAppId"] = "ca-app-pub-3940256099942544~3347511713"
            buildConfigField("String", "ADMOB_BANNER_ID", "\"ca-app-pub-3940256099942544/9214589741\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi")
        freeCompilerArgs.add("-opt-in=androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.11.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.1")
    implementation("androidx.savedstate:savedstate-ktx:1.5.0")
    implementation("androidx.graphics:graphics-shapes:1.1.0")
    implementation("androidx.palette:palette-ktx:1.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.13.0")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.2")

    // Solo en la versión de Play: anuncios en ajustes y el consentimiento (RGPD) de Google.
    "playImplementation"("com.google.android.gms:play-services-ads:25.5.0")
    "playImplementation"("com.google.android.ump:user-messaging-platform:4.0.0")
}
