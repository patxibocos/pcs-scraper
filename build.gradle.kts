import com.diffplug.gradle.spotless.SpotlessPlugin
import com.github.jengelman.gradle.plugins.shadow.ShadowPlugin
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    alias(libs.plugins.spotless)
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
    idea
}

subprojects {
    plugins.apply(SpotlessPlugin::class.java)
    plugins.apply(ShadowPlugin::class.java)
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

    tasks.withType<ShadowJar> {
        minimize {
            exclude(dependency(libs.log4j.slf4j2.impl.get()))
            exclude(dependency(libs.log4j.core.get()))
            exclude(dependency(libs.aws.s3.get()))
        }
    }
}