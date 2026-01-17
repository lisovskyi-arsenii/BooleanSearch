plugins {
    java
    application
}

group = "org.code"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val slf4jVersion = "2.0.9"
val logbackVersion = "1.5.13"
val jacksonVersion = "2.18.2"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

application {
    mainClass.set("Main")
}

tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    reports.html.required.set(false)
    reports.junitXml.required.set(false)
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(24))
    }
}
