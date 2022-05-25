import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-base`
    kotlin("jvm")
    id("org.openrewrite.base")
    id("org.gradle.test-retry")
    id("com.gradle.enterprise")
    id("com.gradle.enterprise.test-distribution")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

configurations.all {
    exclude("com.google.errorprone", "*")
    resolutionStrategy.cacheDynamicVersionsFor(0, "seconds")
}

if(name != "rewrite-test") {
    tasks.named<KotlinCompile>("compileKotlin").configure {
        enabled = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.compilerArgs.add("-parameters")
    options.isFork = true
    options.release.set(8)
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
}

tasks.named<Test>("test").configure {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
    useJUnitPlatform {
        excludeTags("debug")
    }
    jvmArgs = listOf(
        "-XX:+UnlockDiagnosticVMOptions",
        "-XX:+ShowHiddenFrames"
    )
    testLogging {
        showExceptions = true
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showCauses = true
        showStackTraces = true
    }

    val releasing = !project.hasProperty("releasing")
    logger.info("This ${if(releasing) "is" else "is not"} a release build")

    val nightly = !System.getenv("GITHUB_WORKFLOW").equals("nightly-ci")
    logger.info("This ${if(nightly) "is" else "is not"} a nightly build")

    // recently failed tests will get selected, so let's DISABLE for the nightly
    // scheduled builds and releases
    predictiveSelection {
//        enabled.set(releasing && nightly)
        enabled.set(true)
    }
}
