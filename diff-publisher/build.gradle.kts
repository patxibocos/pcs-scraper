import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.firebase.admin)
    implementation(libs.kotlin.logging)
    implementation(libs.log4j.api.kotlin)
    implementation(libs.log4j.core)
    implementation(libs.log4j.slf4j2.impl)

    implementation(project(":common"))
}

kotlin {
    jvmToolchain(11)
}

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = "io.github.patxibocos.diffpublisher.MainKt"
        archiveFileName.set("${project.name}.jar")
    }
}

tasks.withType<ShadowJar> {
    minimize {
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.log4j.slf4j2.impl.get()))
        exclude(dependency(libs.log4j.core.get()))
        exclude(dependency(libs.aws.s3.get()))
        exclude(dependency(libs.sqlite.jdbc.get()))
    }
}
