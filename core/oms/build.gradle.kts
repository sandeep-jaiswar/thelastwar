plugins {
    id("java")
    id("java-library")
}

group = "com.thelastwar"
version = "1.0.0-SNAPSHOT"

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    api(project(":core:orderbook"))
    api(project(":core:risk"))

    // PostgreSQL for persistence
    implementation("org.postgresql:postgresql:42.7.4")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Kafka for event sourcing
    implementation("org.apache.kafka:kafka-clients:3.8.0")

    // JSON for snapshot serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.17.2")

    // Logging
    implementation("org.slf4j:slf4j-api:2.0.16")
    runtimeOnly("ch.qos.logback:logback-classic:1.5.8")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    testImplementation("org.testcontainers:testcontainers:1.20.2")
    testImplementation("org.testcontainers:postgresql:1.20.2")
    testImplementation("org.testcontainers:kafka:1.20.2")
    testImplementation("org.testcontainers:junit-jupiter:1.20.2")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
    options.compilerArgs.addAll(listOf(
        "-Xlint:all",
        "-Xlint:-serial"
    ))
}
