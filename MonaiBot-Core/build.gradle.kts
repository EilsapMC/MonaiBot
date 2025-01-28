plugins {
    id("java")
}

dependencies {
    implementation("org.apache.logging.log4j:log4j-core:2.22.0")
    implementation("org.apache.logging.log4j:log4j-api:2.22.0")
    implementation("org.apache.logging.log4j:log4j-slf4j-impl:2.20.0")

    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")

    implementation("com.google.code.gson:gson:2.10.1")
    implementation("top.mrxiaom.mirai:overflow-core-api:1.0.2.549-29bc0c5-SNAPSHOT")
    implementation("top.mrxiaom.mirai:overflow-core:1.0.2.549-29bc0c5-SNAPSHOT")
    implementation("net.mamoe:mirai-core-api-jvm:2.15.0")
    implementation("com.alibaba.fastjson2:fastjson2:2.0.21")

    implementation("org.jetbrains:annotations:24.0.1")
}