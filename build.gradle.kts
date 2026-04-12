plugins {
    java
    application
}

group = "org.code"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

val slf4jVersion            = "2.0.9"
val logbackVersion          = "1.5.32"
val jacksonVersion          = "2.21.1"
val lombokVersion           = "1.18.42"
val guavaLibVersion         = "33.5.0"
val apacheCommonsCompress   = "1.28.0"
val apacheLuceneAnalysis    = "10.4.0"
val caffeineVersion         = "3.2.3"
val junitVersion            = "5.14.0"

dependencies {
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.junit.jupiter:junit-jupiter:${junitVersion}")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    implementation("org.slf4j:slf4j-api:$slf4jVersion")
    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")

    implementation("com.google.guava:guava:${guavaLibVersion}-jre")

    implementation(fileTree("libs") {include ( "*.jar") })

    implementation("org.apache.commons:commons-compress:${apacheCommonsCompress}")
    implementation("org.apache.lucene:lucene-analysis-common:${apacheLuceneAnalysis}")

    implementation("com.github.ben-manes.caffeine:caffeine:${caffeineVersion}")

    compileOnly("org.projectlombok:lombok:${lombokVersion}")
    annotationProcessor("org.projectlombok:lombok:${lombokVersion}")

    testCompileOnly("org.projectlombok:lombok:${lombokVersion}")
    testAnnotationProcessor("org.projectlombok:lombok:${lombokVersion}")
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
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
