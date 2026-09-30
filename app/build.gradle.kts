plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
    namespace = "com.scenedeck.android"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.scenedeck.android"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.let { value ->
            value.toIntOrNull()?.takeIf { it in 1..2_100_000_000 }
                ?: error("releaseVersionCode must be an integer between 1 and 2100000000")
        } ?: 1
        versionName = providers.gradleProperty("releaseVersionName").getOrElse("0.1.0")
    }

    // CI credentials are ephemeral environment values, never committed Gradle properties.
    val keyStorePath = providers.environmentVariable("ANDROID_KEYSTORE_FILE").orNull
    if (!keyStorePath.isNullOrBlank()) {
        signingConfigs.create("release") {
            storeFile = file(keyStorePath)
            storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
        }
        buildTypes.getByName("release").signingConfig = signingConfigs.getByName("release")
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

// Make a plain `testDebugUnitTest` run verify Roborazzi goldens by default
// (same wiring as :core:designsystem; recording tasks override the property).
tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.verify", "true")
    inputs.files(
        fileTree(layout.projectDirectory.asFile) {
            include("*.png")
            exclude("build/**")
        },
    ).withPropertyName("roborazziGoldenImages")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

dependencies {
    // Keep application and instrumentation runtime constraints compatible.
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.errorprone.annotations)
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:obs"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:database"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:ui"))

    implementation(project(":feature:live"))
    implementation(project(":feature:mixer"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:inventory"))
    implementation(project(":feature:graph"))
    implementation(project(":feature:doctor"))
    implementation(project(":feature:connections"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:onboarding"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.androidx.room.runtime)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    implementation(libs.kotlinx.coroutines.android)

    // M7 platform power features: keep-alive FGS + Glance mini-deck widget.
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.roborazzi)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.glance.appwidget.testing)
    testImplementation(libs.androidx.glance.testing)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
