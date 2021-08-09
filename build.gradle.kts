import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.21"
    kotlin("plugin.serialization") version "1.5.21"
    application
    id("com.diffplug.spotless") version "5.14.2"
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
    testImplementation(kotlin("test"))

    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.skrapeit)
    implementation(libs.sqlite.jdbc)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
        archiveFileName.set("${application.applicationName}.jar")
    }
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

spotless {
    kotlin {
        target("**/*.kt")
        targetExclude("$buildDir/**/*.kt", "bin/**/*.kt")
        ktlint("0.42.1")
    }

    kotlinGradle {
        target("*.gradle.kts")
        ktlint("0.42.1")
    }
}
