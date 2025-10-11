plugins {
    id("java-library")
}

dependencies {
    // Internal dependencies
    api(project(":core:eventbus"))
    
    // Spring WebFlux (Reactor Netty) for reactive REST
    implementation("org.springframework.boot:spring-boot-starter-webflux:3.2.2")
    
    // DSL-JSON for ultra-fast serialization
    implementation("com.dslplatform:dsl-json:2.0.2")
    annotationProcessor("com.dslplatform:dsl-json:2.0.2")
    
    // JWT authentication
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    
    // Bucket4j for rate limiting
    implementation("com.bucket4j:bucket4j-core:8.7.0")
    
    // Micrometer for metrics
    implementation("io.micrometer:micrometer-core:1.11.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
    
    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("org.springframework.boot:spring-boot-starter-test:3.2.2")
    testImplementation("io.projectreactor:reactor-test:3.6.2")
    
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
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
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
