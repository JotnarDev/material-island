import java.util.Properties

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
        versionCode = 6
        versionName = "1.2.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // Sin el commit de git dentro de la APK: cambia según dónde se compile y rompe las builds reproducibles.
            vcsInfo.include = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // La firma va por versión (abajo): foss y play con la clave de debug; la APK de GitHub se vuelve a
            // firmar con la clave de release + linaje (scripts/sign-apk.sh, workflow Release; docs/RELEASING.md).
            // fdroid sale sin firmar: la firma F-Droid.
        }
    }

    // Clave de subida a Google Play (fuera del repo, en ../material-island-keys/upload.properties).
    // Si no está (CI, otros desarrolladores), la versión play se firma con la clave de debug.
    val uploadProps = rootProject.file("../material-island-keys/upload.properties")
        .takeIf { it.exists() }
        ?.let { f -> Properties().apply { f.inputStream().use { load(it) } } }
    signingConfigs {
        if (uploadProps != null) create("upload") {
            storeFile = file(uploadProps.getProperty("storeFile"))
            storePassword = uploadProps.getProperty("storePassword")
            keyAlias = uploadProps.getProperty("keyAlias")
            keyPassword = uploadProps.getProperty("keyPassword")
        }
    }

    // Tres versiones:
    //  · foss: la de GitHub, Obtainium, F-Droid e IzzyOnDroid. Sin anuncios ni código de Google; se actualiza sola.
    //  · fdroid: la que compila F-Droid. Como foss pero sin actualizador (actualiza F-Droid).
    //  · play: para Google Play. Anuncios solo en las pantallas de ajustes (nunca en la isla); la actualiza Play.
    flavorDimensions += "store"
    productFlavors {
        create("foss") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "true")
            signingConfig = signingConfigs.getByName("debug")
        }
        // Igual que foss, pero para F-Droid: lo compila y firma F-Droid, y es F-Droid quien la actualiza.
        create("fdroid") {
            dimension = "store"
            buildConfigField("boolean", "SELF_UPDATE", "false")
        }
        create("play") {
            dimension = "store"
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.getByName("debug")
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

    // Sin el bloque de dependencias cifrado con la clave de Google (F-Droid no lo acepta).
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
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
