import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("java")
    id("io.github.goooler.shadow") version "8.1.2"
}

dependencies {
    implementation(project(":MonaiBot-Core"))
    implementation(project(":MonaiBot-Functions"))
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("org.apache.logging.log4j:log4j-api:2.22.0")
}

tasks.withType(ShadowJar::class) {
    manifest {
        attributes("Main-Class" to "i.earthme.monaibot.boot.Main")
    }
    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
    exclude("META-INF/NOTICE", "META-INF/NOTICE.txt")
    exclude("META-INF/LICENSE", "META-INF/LICENSE.txt")
    exclude("META-INF/DEPENDENCIES")
}
