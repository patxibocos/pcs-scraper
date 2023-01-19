import com.diffplug.gradle.spotless.SpotlessPlugin

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm) apply false
    idea
}

subprojects {
    plugins.apply(SpotlessPlugin::class.java)
    spotless {
        kotlin {
            target("**/*.kt")
            targetExclude("$buildDir/**/*.kt", "bin/**/*.kt", "**/protobuf/**/*.kt")
            ktlint(libs.versions.ktlint.get())
        }

        kotlinGradle {
            target("*.gradle.kts")
            ktlint(libs.versions.ktlint.get())
        }
    }
}