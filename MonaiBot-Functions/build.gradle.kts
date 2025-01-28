plugins {
    id("java")
}

dependencies {
    compileOnly(project(":MonaiBot-Core"))
    compileOnly("org.apache.logging.log4j:log4j-api:2.22.0")
    compileOnly("com.google.code.gson:gson:2.10.1")
    compileOnly("net.mamoe:mirai-core-api-jvm:2.15.0")
    compileOnly("com.alibaba.fastjson2:fastjson2:2.0.21")
    compileOnly("org.jetbrains:annotations:24.0.1")

    implementation("org.lmdbjava:lmdbjava:0.9.0")
    implementation("it.unimi.dsi:fastutil:8.5.9")
    implementation("com.squareup.okhttp3:okhttp:4.9.3")
}