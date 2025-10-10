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
    // Depend on eventbus for OrderEvent
    api(project(":core:eventbus"))
    
    // Chronicle Map for off-heap collections
    implementation("net.openhft:chronicle-map:3.25ea5")
    
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.10.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.0")
    
    // JMH for microbenchmarking
    testImplementation("org.openjdk.jmh:jmh-core:1.37")
    testAnnotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}

tasks.test {
    useJUnitPlatform()
    maxHeapSize = "2g"
    
    jvmArgs(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
    )
    
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

// JMH benchmark task
tasks.register<JavaExec>("jmh") {
    group = "benchmark"
    description = "Run JMH benchmarks"
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("org.openjdk.jmh.Main")
    
    jvmArgs(
        "--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED",
        "--add-opens", "java.base/java.util.zip=ALL-UNNAMED"
    )
    
    // Default to all benchmarks, can be overridden with -Pargs="pattern"
    args = if (project.hasProperty("args")) {
        project.property("args").toString().split(" ")
    } else {
        listOf(".*Benchmark.*")
    }
}
