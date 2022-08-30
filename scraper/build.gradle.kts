import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    application
    idea
    kotlin("jvm") version "1.7.0"
    kotlin("plugin.serialization") version "1.7.0"
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
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.log4j.api)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j.impl)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.skrapeit) {
        exclude(group = "ch.qos.logback", module = "logback-classic")
    }
    implementation(libs.sqlite.jdbc)

    implementation(project(":common"))

    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.mockk)
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
        archiveFileName.set("${project.name}.jar")
    }
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
}
