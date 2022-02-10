import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.6.10"
    kotlin("plugin.serialization") version "1.6.10"
    application
    id("com.diffplug.spotless") version "6.2.2"
}

group = "io.github.patxibocos"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("io.github.patxibocos.pcsscraper.MainKt")
    applicationName = rootProject.name
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.aws.s3)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.firebase.admin)
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.protobuf.kotlin)
    implementation(libs.skrapeit)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.mockk)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
    kotlinOptions.freeCompilerArgs += "-Xopt-in=kotlin.RequiresOptIn"
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
        archiveFileName.set("${application.applicationName}.jar")
    }
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("$buildDir/**/*.kt", "bin/**/*.kt", "**/protobuf/**/*.kt")
        ktlint("0.43.2")
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.43.2")
    }
}
