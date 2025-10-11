plugins {
    id("java-library")
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    api(project(":core:gateway"))
    
    // Netty for WebSocket server
    implementation("io.netty:netty-all:4.1.100.Final")
    
    // MessagePack for binary serialization
    implementation("org.msgpack:msgpack-core:0.9.6")
    implementation("org.msgpack:jackson-dataformat-msgpack:0.9.6")
    
    // Jackson for JSON serialization
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
    implementation("com.fasterxml.jackson.core:jackson-core:2.15.2")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.11.0")
    
    // SLF4J for logging
    implementation("org.slf4j:slf4j-api:2.0.7")
    implementation("org.slf4j:slf4j-simple:2.0.7")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // Mockito for mocking
    testImplementation("org.mockito:mockito-core:5.3.1")
    testImplementation("org.mockito:mockito-junit-jupiter:5.3.1")
    
    // JMH for benchmarking
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}
