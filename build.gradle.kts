import io.gitlab.arturbosch.detekt.extensions.DetektExtension

// Patch AGP's transitive build tools without changing Android runtime dependencies.
// Version constraints are minimums: newer compatible plugin dependencies can win.
buildscript {
    val securityCatalog = project.extensions.getByType<VersionCatalogsExtension>().named("libs")
    dependencies {
        constraints {
            val bouncyCastleVersion = securityCatalog.findVersion("bouncycastle-security").get().requiredVersion
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { artifact ->
                add("classpath", "org.bouncycastle:$artifact:$bouncyCastleVersion") {
                    because("Align Bouncy Castle build tools with patched certificate/ASN.1 processing")
                }
            }
            add("classpath", "org.bitbucket.b_c:jose4j:${securityCatalog.findVersion("jose4j-security").get().requiredVersion}") {
                because("CVE-2024-29371: bound compressed JWE decompression")
            }
            add("classpath", "org.jdom:jdom2:${securityCatalog.findVersion("jdom2-security").get().requiredVersion}") {
                because("CVE-2021-33813: patched XML processing in Jetifier")
            }
            add("classpath", "org.apache.commons:commons-lang3:${securityCatalog.findVersion("commons-lang3-security").get().requiredVersion}") {
                because("CVE-2025-48924: avoid uncontrolled recursion")
            }
        }
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt)
}

detekt {
    config.setFrom(files("config/detekt/detekt.yml"))
    buildUponDefaultConfig = true
}

subprojects {
    apply(plugin = "io.gitlab.arturbosch.detekt")
    extensions.configure<DetektExtension> {
        config.setFrom(rootProject.files("config/detekt/detekt.yml"))
        buildUponDefaultConfig = true
    }
}

// Analyze the same bytecode target as the application, independent of the host JDK.
allprojects {
    tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
        jvmTarget = "17"
        reports { sarif.required.set(true) }
    }
}

val kotlinFormatter = configurations.create("kotlinFormatter") {
    attributes {
        attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_RUNTIME))
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
        attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
        attribute(org.gradle.api.attributes.java.TargetJvmEnvironment.TARGET_JVM_ENVIRONMENT_ATTRIBUTE,
            objects.named(org.gradle.api.attributes.java.TargetJvmEnvironment.STANDARD_JVM))
    }
}

dependencies {
    add(kotlinFormatter.name, libs.ktfmt)
}

val kotlinSources = fileTree(rootDir) {
    include("app/src/**/*.kt", "core/*/src/**/*.kt", "feature/*/src/**/*.kt")
    exclude("**/build/**")
}

fun JavaExec.configureFormatter(checkOnly: Boolean) {
    classpath = kotlinFormatter
    mainClass.set("com.facebook.ktfmt.cli.Main")
    inputs.files(kotlinSources)
    args("--kotlinlang-style", "--quiet")
    if (checkOnly) args("--dry-run", "--set-exit-if-changed")
    args(kotlinSources.files.sorted().map { it.absolutePath })
}

tasks.register<JavaExec>("ktfmtCheck") {
    group = "verification"
    description = "Check Kotlin source formatting without modifying files."
    configureFormatter(true)
}

tasks.register<JavaExec>("ktfmtFormat") {
    group = "formatting"
    description = "Format Kotlin source using the shared Kotlin style."
    configureFormatter(false)
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Run formatting, static analysis, Android lint, tests and debug build."
    dependsOn("ktfmtCheck")
    subprojects.filter { it.path == ":app" || it.path.count { char -> char == ':' } == 2 }.forEach {
        dependsOn("${it.path}:detekt")
        if (it.path != ":core:model" && it.path != ":core:common") {
            dependsOn("${it.path}:lintDebug", "${it.path}:testDebugUnitTest")
        } else {
            dependsOn("${it.path}:test")
        }
    }
    dependsOn(":app:assembleDebug")
}
