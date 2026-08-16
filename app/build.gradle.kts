import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * Configuración sensible: se lee de `local.properties` (gitignored) y, si no está ahí,
 * de variables de entorno (útil en CI). Nunca se commitea un valor real.
 */
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

/**
 * Una clave presente pero vacía cuenta como ausente. Sin esto, copiar el `.example`
 * con un valor sin completar generaría un `BuildConfig` inválido (`= L;`) y el error
 * aparecería lejos de su causa.
 */
fun config(key: String, default: String): String =
    (
        localProperties.getProperty(key)?.takeIf { it.isNotBlank() }
            ?: System.getenv(key)?.takeIf { it.isNotBlank() }
            ?: default
        ).trim()

android {
    namespace = "com.vinculovial.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.vinculovial.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${config("API_BASE_URL", "http://10.0.2.2:8080/")}\"")
        buildConfigField(
            "String",
            "IDENTITY_HPKE_PUBLIC_KEY",
            "\"${config("IDENTITY_HPKE_PUBLIC_KEY", "")}\"",
        )
        buildConfigField("String", "APP_ATTESTATION_PSK", "\"${config("APP_ATTESTATION_PSK", "")}\"")
        buildConfigField("long", "ATTESTATION_TOKEN_TTL_SECONDS", config("ATTESTATION_TOKEN_TTL_SECONDS", "60") + "L")
        buildConfigField("long", "NETWORK_TIMEOUT_MS", config("NETWORK_TIMEOUT_MS", "15000") + "L")

        // Umbral de similitud coseno para dar por válido el match facial.
        // Comparar una foto impresa de DNI contra una selfie es más ruidoso que
        // comparar dos selfies: calibrar con datos reales antes de mover este valor.
        //
        // Bajado de 0.62 a 0.45 a partir de UNA sola muestra real (verdadero positivo, log
        // BIOMETRY): 0.479 con el recorte de documento todavía degradado a 145x145px por el
        // cuello de botella de resolución de ImageCapture (ver DocumentScanScreen). Con la
        // resolución de captura ya recuperada (switchToCaptureMode/switchToAnalysisMode) el
        // recorte va a ser sustancialmente más grande, y ese mismo score real puede terminar
        // bien por encima de 0.62 sin que haga falta este ajuste. Un solo verdadero positivo
        // no calibra nada: falta la otra mitad del dato, la tasa de falsos positivos (impostor
        // con otro documento), y ese dato no se puede estimar con una persona. Volver a medir
        // con la resolución nueva antes de dar este valor por definitivo.
        buildConfigField("float", "FACE_MATCH_THRESHOLD", config("FACE_MATCH_THRESHOLD", "0.45") + "f")
        // Nombre del .tflite en app/src/main/assets/. Default alineado con el archivo
        // real que se usa en desarrollo (ver app/src/main/assets/README.md); si tu
        // modelo se llama distinto, sobreescribí FACE_EMBEDDER_MODEL_ASSET.
        buildConfigField(
            "String",
            "FACE_EMBEDDER_MODEL_ASSET",
            "\"${config("FACE_EMBEDDER_MODEL_ASSET", "mobilefacenet.tflite")}\"",
        )

        // `play_integrity` = veredicto real de Google (default); `mock` = JWT firmado con
        // la PSK compartida, para emuladores y CI. El Gateway tiene que estar preparado
        // para el modo elegido.
        buildConfigField("String", "ATTESTATION_MODE", "\"${config("ATTESTATION_MODE", "play_integrity")}\"")
        buildConfigField(
            "long",
            "PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER",
            config("PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER", "0") + "L",
        )
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    androidResources {
        // TFLite mapea el modelo directo desde el APK; comprimirlo rompe ese mapeo.
        noCompress += "tflite"
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/{AL2.0,LGPL2.1}",
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.moshi)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.tink.android)
    implementation(libs.jjwt.api)
    runtimeOnly(libs.jjwt.impl)
    runtimeOnly(libs.jjwt.orgjson)

    implementation(libs.play.integrity)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.face.detection)
    implementation(libs.tensorflow.lite)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.orgjson)
    testRuntimeOnly(libs.json)
}
