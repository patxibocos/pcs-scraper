import com.google.protobuf.gradle.protobuf
import com.google.protobuf.gradle.protoc

plugins {
    kotlin("jvm")
    id("com.google.protobuf") version "0.8.18"
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.20.0"
    }
}

kotlin {
    jvmToolchain(11)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.protobuf.kotlin)
}
