plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

val fdroidBuild = providers.gradleProperty("fdroidBuild").map { value ->
    require(value == "true" || value == "false") { "fdroidBuild must be true or false" }
    value.toBoolean()
}.getOrElse(false)

android {
    lint {
        lintConfig = rootProject.file("lint.xml")
        warningsAsErrors = true
    }
    namespace = "com.scenedeck.android.feature.connections"
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
    testOptions { unitTests.isIncludeAndroidResources = true }
}

// AGP 9.4 exposes legacy source-set instances through its new library DSL container.
(android.sourceSets as NamedDomainObjectContainer<com.android.build.gradle.api.AndroidSourceSet>).named("main") {
    // F-Droid removes src/play before scanning; no proprietary QR code enters that build.
    val distribution = if (fdroidBuild) "fdroid" else "play"
    manifest.srcFile("src/$distribution/AndroidManifest.xml")
}

androidComponents {
    onVariants(selector().all()) { variant ->
        val distribution = if (fdroidBuild) "fdroid" else "play"
        variant.sources.kotlin?.addStaticSourceDirectory("src/$distribution/java")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

tasks.withType<Test>().configureEach {
    systemProperty("roborazzi.test.verify", "true")
    inputs.files(fileTree(layout.projectDirectory.asFile) {
        include("*.png")
        exclude("build/**")
    }).withPropertyName("roborazziGoldenImages")
        .withPathSensitivity(org.gradle.api.tasks.PathSensitivity.RELATIVE)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(project(":core:designsystem"))
    implementation(project(":core:data"))
    implementation(project(":core:model"))

    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.androidx.hilt.navigation.compose)

    testImplementation(libs.junit)
    testImplementation(project(":core:database"))
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

if (!fdroidBuild) {
    // Keep proprietary distribution dependencies in the folder removed by F-Droid.
    val catalog = extensions.getByType<VersionCatalogsExtension>().named("libs")
    file("src/play/qr-dependencies.txt").readLines().filter { it.isNotBlank() }.forEach { alias ->
        dependencies.add("implementation", catalog.findLibrary(alias).get())
    }
}
