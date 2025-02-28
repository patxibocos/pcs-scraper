plugins {
    idea
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.google.protobuf)
}

protobuf {
    protoc {
        artifact = libs.protobuf.protoc.get().toString()
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
