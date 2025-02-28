import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.aws.kotlin.s3)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.firebase.admin)
    implementation(libs.kotlin.cli)
    implementation(libs.kotlin.logging)
    implementation(libs.kotlin.serialization.json)
    implementation(libs.log4j.api.kotlin)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2.impl)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.skrapeit)
    implementation(libs.sqlite.jdbc)

    implementation(project(":common"))

    testImplementation(libs.test.kotest.assertions)
    testImplementation(libs.test.kotest.runner.junit5)
    testImplementation(libs.test.mockk)
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.github.patxibocos.pcsscraper.MainKt"
        archiveFileName.set("${project.name}.jar")
    }
}

tasks.withType<ShadowJar> {
    minimize {
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.log4j.slf4j2.impl.get()))
        exclude(dependency(libs.log4j.core.get()))
        exclude(dependency(libs.sqlite.jdbc.get()))
    }
}
