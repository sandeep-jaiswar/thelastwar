plugins {
    id("java")
}

allprojects {
    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")
    
    java {
        toolchain {
            // TODO: Upgrade to Java 21 as specified in architecture
            languageVersion.set(JavaLanguageVersion.of(17))
        }
    }
}
