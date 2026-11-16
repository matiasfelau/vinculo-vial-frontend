import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

/**
 * `google-services.json` es un secreto de despliegue (identifica el proyecto de Firebase):
 * nunca se versiona real, sólo `google-services.json.example` documenta su forma. El plugin
 * se aplica sólo si el archivo real está presente -- sin esto, cualquier build sin ese
 * archivo (CI, un clon nuevo del repo) fallaría en la fase de configuración, antes de
 * compilar una sola línea de Kotlin.
 */
if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
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
    namespace = "ar.felaufernandezbottaro.vinculovial"
    compileSdk = 35

    defaultConfig {
        applicationId = "ar.felaufernandezbottaro.vinculovial"
        minSdk = 26
        targetSdk = 35
        versionCode = 8
        versionName = "0.10.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "API_BASE_URL", "\"${config("API_BASE_URL", "http://10.0.2.2:8080/")}\"")
        buildConfigField(
            "String",
            "IDENTITY_HPKE_PUBLIC_KEY",
            "\"${config("IDENTITY_HPKE_PUBLIC_KEY", "")}\"",
        )
        // Par nuevo de US-04 (Vinculación de Vehículo): cifra la patente contra
        // `vinculo-vial-vehicle`, nunca contra Identity -- ver el cifrado diferenciado en
        // HpkeEncryptor.
        buildConfigField(
            "String",
            "VEHICLE_HPKE_PUBLIC_KEY",
            "\"${config("VEHICLE_HPKE_PUBLIC_KEY", "")}\"",
        )
        // US-08: cifra las fotos de evidencia (y nada más) contra `vinculo-vial-ia`. Es la
        // pública del par IA_HPKE_PRIVATE_KEY del repo de IA, la misma que usa Vehicle para
        // `pat_enc_ia`.
        buildConfigField(
            "String",
            "IA_HPKE_PUBLIC_KEY",
            "\"${config("IA_HPKE_PUBLIC_KEY", "")}\"",
        )
        buildConfigField("String", "APP_ATTESTATION_PSK", "\"${config("APP_ATTESTATION_PSK", "")}\"")
        buildConfigField("long", "ATTESTATION_TOKEN_TTL_SECONDS", config("ATTESTATION_TOKEN_TTL_SECONDS", "60") + "L")
        buildConfigField("long", "NETWORK_TIMEOUT_MS", config("NETWORK_TIMEOUT_MS", "15000") + "L")
        // Presupuesto propio para el Task de Play Integrity (prepare + request, en conjunto,
        // no uno cada uno -- ver PlayIntegrityAttestationProvider). Separado de
        // NETWORK_TIMEOUT_MS a propósito: son fases distintas de la misma llamada (attestation
        // corre antes de abrir el socket) y compartir una sola constante entre las dos rompía
        // la invariante callTimeout > ATTESTATION_TIMEOUT_MS + connect + read -- ver NetworkModule.
        buildConfigField("long", "ATTESTATION_TIMEOUT_MS", config("ATTESTATION_TIMEOUT_MS", "8000") + "L")

        // Circuit breaker por servicio upstream (según el prefijo de ruta), ver
        // CircuitBreakerInterceptor. Umbral/ventana/apertura configurables por entorno, igual
        // que los timeouts de arriba.
        buildConfigField(
            "int",
            "CIRCUIT_BREAKER_FAILURE_THRESHOLD",
            config("CIRCUIT_BREAKER_FAILURE_THRESHOLD", "3"),
        )
        buildConfigField("long", "CIRCUIT_BREAKER_WINDOW_MS", config("CIRCUIT_BREAKER_WINDOW_MS", "60000") + "L")
        buildConfigField("long", "CIRCUIT_BREAKER_OPEN_MS", config("CIRCUIT_BREAKER_OPEN_MS", "30000") + "L")

        // US-12: tiempo en segundo plano a partir del cual la sesión se bloquea al volver a
        // primer plano (ver SessionLockCoordinator). Configurable por local.properties.
        buildConfigField(
            "long",
            "SESSION_LOCK_AFTER_BACKGROUND_SECONDS",
            config("SESSION_LOCK_AFTER_BACKGROUND_SECONDS", "300") + "L",
        )

        // US-13: cuenta regresiva del botón "Reenviar" del código por email. Tiene que coincidir con
        // EMAIL_CODE_RESEND_SECONDS de Identity, que es quien aplica el throttle de verdad.
        buildConfigField("int", "EMAIL_CODE_RESEND_SECONDS", config("EMAIL_CODE_RESEND_SECONDS", "60"))

        // Pivote RENAPER-Ready: ya no hay umbral de similitud coseno ni modelo .tflite que
        // configurar acá — la verificación de identidad (comparar la selfie contra el
        // documento) se movió al backend, contra el padrón del Estado. FACE_MATCH_THRESHOLD
        // y FACE_EMBEDDER_MODEL_ASSET existieron mientras esa comparación corría on-device
        // con TensorFlow Lite; los dos se retiraron junto con esa lógica.

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
            // Sin esto el Release Build queda sin firmar (Gradle lo arma igual, pero Android
            // se niega a instalarlo). Todavía no hay un keystore de producción, así que se
            // firma con el de debug -- lo autogenera AGP, no requiere secretos -- para que
            // el APK que sube CI a los GitHub Releases sea instalable. No es apto para Play
            // Store: cuando exista un keystore real, reemplazar esta línea por un
            // signingConfigs.create("release") con las credenciales en GitHub Secrets.
            signingConfig = signingConfigs.getByName("debug")
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
        // Robolectric necesita los recursos y el manifiesto para las pruebas de pantalla (createComposeRule).
        unitTests.isIncludeAndroidResources = true
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

/**
 * US-08: el manifiesto mergeado de release no puede traer permisos de lectura de medios (la
 * galería sólo existe en `debug`). `ReleaseNoGalleryTest` lo verifica leyendo el manifiesto que
 * AGP genera para release; esta ruta se le pasa como propiedad del sistema.
 */
androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val mergedManifest = variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST)
        tasks.withType<Test>().matching { it.name == "testReleaseUnitTest" }.configureEach {
            inputs.file(mergedManifest)
            doFirst { systemProperty("mergedManifestPath", mergedManifest.get().asFile.absolutePath) }
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

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

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    implementation(libs.mlkit.barcode.scanning)
    implementation(libs.mlkit.face.detection)
    implementation(libs.mlkit.text.recognition)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testRuntimeOnly(libs.jjwt.impl)
    testRuntimeOnly(libs.jjwt.orgjson)
    testRuntimeOnly(libs.json)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
