plugins {
    id("java")
}

group = "i.earthme"
version = "1.0-SNAPSHOT"

allprojects{
    apply(plugin = "java")

    group = "i.earthme"
    version = "1.0-SNAPSHOT"

    repositories {
        maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
        maven("https://repo.papermc.io/repository/maven-public/")
        mavenCentral()
    }
}

repositories {
    maven("https://s01.oss.sonatype.org/content/repositories/snapshots")
    maven("https://repo.papermc.io/repository/maven-public/")
    mavenCentral()
}

dependencies {

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}