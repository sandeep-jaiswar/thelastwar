plugins {
    id("java-library")
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    api(project(":core:orderbook"))
    api(project(":core:risk"))
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.11.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
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
    
    // Add JVM args for Aeron to access internal Java modules
    jvmArgs(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
    )
    
    testLogging {
        events("passed", "skipped", "failed")
        showStandardStreams = false
    }
}

// JMH benchmark task with optimized settings
tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks for matching engine"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    
    // Memory settings to prevent OOM
    jvmArgs(
        "-Xms512m",
        "-Xmx1g",
        "-XX:+UseG1GC"
    )
    
    // Default to CacheWarmingServiceBenchmark, can be overridden with -Pargs="pattern"
    args = if (project.hasProperty("args")) {
        listOf(project.property("args").toString())
    } else {
        listOf(
            "CacheWarmingServiceBenchmark",
            "-wi", "2",  // 2 warmup iterations
            "-i", "3",   // 3 measurement iterations
            "-f", "1",   // 1 fork
            "-r", "500ms",  // 500ms per iteration
            "-w", "500ms"   // 500ms warmup
        )
    }
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
