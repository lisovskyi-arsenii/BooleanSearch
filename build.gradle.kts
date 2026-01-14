plugins {
    id("java")
    kotlin("jvm")
}

group = "org.code"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val slf4jVersion = "2.0.9"
val logbackVersion = "1.4.14"

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(kotlin("stdlib-jdk8"))

    // SLF4J API
    implementation("org.slf4j:slf4j-api:${slf4jVersion}")

    // Logback
    implementation("ch.qos.logback:logback-classic:${logbackVersion}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    reports.html.required.set(false)
    reports.junitXml.required.set(false)
}

kotlin {
    jvmToolchain(24)
}