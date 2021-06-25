import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.20"
    kotlin("plugin.serialization") version "1.5.20"
    application
}

group = "io.github.patxibocos"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("MainKt")
    applicationName = rootProject.name
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("it.skrape:skrapeit:1.1.3")
    implementation("org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.2.1")
    implementation("org.xerial:sqlite-jdbc:3.34.0")
    implementation("org.jetbrains.exposed:exposed-core:0.32.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.32.1")
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