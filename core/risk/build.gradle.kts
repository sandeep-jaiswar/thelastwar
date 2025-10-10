plugins {
    id("java-library")
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    
    // JMH for benchmarking
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "1g"
    
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
