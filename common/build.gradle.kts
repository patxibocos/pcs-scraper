plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.google.protobuf)
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:3.23.1"
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
