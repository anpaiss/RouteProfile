import java.util.Base64
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.jetbrains.kotlin.android)
}

// Release identity comes from CI: the tag gives the version name, the run
// number the version code. A local build keeps fixed values.
val releaseVersion: String =
    System.getenv("RELEASE_VERSION")?.removePrefix("v")?.takeIf { it.isNotBlank() } ?: "1.0"
val buildNumber: Int = System.getenv("BUILD_NUMBER")?.toIntOrNull() ?: 0
val appVersionCode: Int = if (buildNumber > 0) 100 + buildNumber else 100

// Signing, the same key as every other extension (updates install over):
//  - CI: the keystore travels as a base64 secret, decoded next to the module, outside build/
//    so `clean` cannot remove it mid-build (*.keystore is ignored);
//  - local: signing.storeFile / storePassword / keyAlias / keyPassword from local.properties,
//    or from ../BigFields/local.properties, where the shared key is already configured.
val keystoreBase64: String? = System.getenv("KEYSTORE_BASE64")?.takeIf { it.isNotBlank() }
val localSigning: Properties? = listOf(rootProject.file("local.properties"), rootProject.file("../BigFields/local.properties"))
    .filter { it.exists() }
    .map { f -> Properties().apply { f.inputStream().use { load(it) } } }
    .firstOrNull { it.containsKey("signing.storeFile") }

android {
    namespace  = "com.anpaiss.routeprofile"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.anpaiss.routeprofile"
        minSdk        = 31
        targetSdk     = 34
        versionCode   = appVersionCode
        versionName   = releaseVersion
    }

    buildFeatures {
        buildConfig = true   // BuildConfig.VERSION_NAME for the KarooExtension version
    }

    signingConfigs {
        if (keystoreBase64 != null) {
            create("release") {
                val ksFile = layout.projectDirectory.file(".release.keystore").asFile
                ksFile.writeBytes(Base64.getDecoder().decode(keystoreBase64))
                storeFile     = ksFile
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias      = System.getenv("KEY_ALIAS")
                keyPassword   = System.getenv("KEY_PASSWORD")
            }
        } else if (localSigning != null) {
            create("release") {
                storeFile     = file(localSigning["signing.storeFile"] as String)
                storePassword = localSigning["signing.storePassword"] as String
                keyAlias      = localSigning["signing.keyAlias"] as String
                keyPassword   = localSigning["signing.keyPassword"] as String
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
    }
}

// Karoo extension manifest: the JSON the Karoo fetches from MANIFEST_URL (see
// AndroidManifest.xml). Published next to the APK with every release; the APK
// is uploaded under the fixed name RouteProfile.apk so releases/latest/download
// works. Schema: io.hammerhead.karooext.models.KarooAppManifest.
val generateManifest by tasks.registering {
    val out  = layout.projectDirectory.file("manifest.json")
    val repo = "https://github.com/anpaiss/RouteProfile"
    val version = releaseVersion
    val code = appVersionCode
    val notes = System.getenv("RELEASE_NOTES").orEmpty().ifBlank { "See the GitHub release." }
    inputs.property("version", version)
    inputs.property("code", code)
    inputs.property("notes", notes)
    outputs.file(out)
    doLast {
        fun q(v: String) = "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
        out.asFile.writeText(
            """
            {
              "label": "RouteProfile",
              "packageName": "com.anpaiss.routeprofile",
              "iconUrl": ${q("https://raw.githubusercontent.com/anpaiss/RouteProfile/master/docs/icon.png")},
              "latestApkUrl": ${q("$repo/releases/latest/download/RouteProfile.apk")},
              "latestVersion": ${q(version)},
              "latestVersionCode": $code,
              "developer": "Andrea Paissan",
              "description": ${q("The whole route's elevation under the map, coloured by gradient, with where you are and the distance and climb still to go. Tap for the next 10 km; auto zoom on the climbs you choose.")},
              "releaseNotes": ${q(notes)},
              "screenshotUrls": [
                ${q("https://raw.githubusercontent.com/anpaiss/RouteProfile/master/docs/map.png")},
                ${q("https://raw.githubusercontent.com/anpaiss/RouteProfile/master/docs/zoom.png")},
                ${q("https://raw.githubusercontent.com/anpaiss/RouteProfile/master/docs/app.png")}
              ],
              "tags": ["navigation"]
            }
            """.trimIndent()
        )
    }
}
tasks.named("preBuild") { dependsOn(generateManifest) }

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.hammerhead.karoo.ext)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
}
