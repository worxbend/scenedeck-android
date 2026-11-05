plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
}

android {
    testFixtures { enable = true }
    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
    namespace = "com.scenedeck.android.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Build migration fixtures from committed schemas through AGP's variant source API.
androidComponents {
    onVariants { variant ->
        variant.hostTests.values.forEach { test ->
            test.sources.resources?.addStaticSourceDirectory("../database/schemas")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    // Re-exported: ObsClient is the repository layer's input boundary (the interface
    // only — ktobs/Ktor stay hidden inside :core:obs).
    api(project(":core:obs"))
    implementation(project(":core:database"))
    implementation(project(":core:datastore"))

    // @Immutable on ConnectionProfile (Compose stability for UiState lists).
    compileOnly(platform(libs.androidx.compose.bom))
    compileOnly(libs.androidx.compose.runtime)

    implementation(libs.javax.inject)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kaml)

    testFixturesApi(project(":core:database"))
    testFixturesApi(project(":core:model"))
    testFixturesApi(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.runtime)
    testImplementation(libs.androidx.room.ktx)
    testImplementation(libs.androidx.datastore.preferences)
}
