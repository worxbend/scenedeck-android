plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
}

android {
    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
    namespace = "com.scenedeck.android.core.designsystem"
    compileSdk = 37

    defaultConfig {
        minSdk = 28
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

// Make a plain `testDebugUnitTest` run verify Roborazzi goldens by default.
// The recordRoborazziDebug / verifyRoborazziDebug / verifyAndRecordRoborazziDebug tasks
// overwrite these system properties in their own doFirst hook (registered before this
// configuration, so it runs after), so recording is unaffected.
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.verify", "true")
    // Track golden images as inputs so changed goldens invalidate up-to-date checks
    // (the plugin only wires this itself when its own verify task is in the graph).
    inputs.files(
        fileTree(layout.projectDirectory.asFile) {
            include("*.png")
            exclude("build/**")
        },
    ).withPropertyName("roborazziGoldenImages")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

dependencies {
    implementation(project(":core:model"))
    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.ui.text.google.fonts)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}
