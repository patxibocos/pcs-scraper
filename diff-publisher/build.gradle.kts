plugins {
    application
    idea
    alias(libs.plugins.kotlin.jvm)
}

group = "io.github.patxibocos"
version = "1.0"

application {
    mainClass.set("io.github.patxibocos.diffpublisher.MainKt")
    applicationName = rootProject.name
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
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = application.mainClass
        archiveFileName.set("${project.name}.jar")
    }
    from(configurations.compileClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    exclude("META-INF/*.SF")
    exclude("META-INF/*.DSA")
}
