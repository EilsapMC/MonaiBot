plugins {
    id("java")
}

dependencies {
    implementation(project(":MonaiBot-Core"))
    implementation(project(":MonaiBot-Functions"))
    implementation("org.jetbrains:annotations:24.0.1")
    implementation("org.apache.logging.log4j:log4j-api:2.22.0")
}