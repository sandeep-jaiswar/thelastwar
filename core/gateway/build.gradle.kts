plugins {
    id("java-library")
    id("jacoco")
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    
    // Agrona for zero-copy buffers and object pooling
    implementation("org.agrona:agrona:1.21.1")
    
    // QuickFIX/J for FIX protocol
    implementation("org.quickfixj:quickfixj-core:2.3.1")
    implementation("org.quickfixj:quickfixj-messages-all:2.3.1")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.11.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
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
    maxHeapSize = "1g"
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
    
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    violationRules {
        rule {
            limit {
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// JMH benchmark task
tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    
    // Default to all benchmarks, can be overridden with -Pargs="pattern"
    args = if (project.hasProperty("args")) {
        listOf(project.property("args").toString())
    } else {
        listOf(".*Benchmark.*")
    }
}
