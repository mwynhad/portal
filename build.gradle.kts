import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.9.22"
    kotlin("plugin.serialization") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("xyz.jpenilla.run-paper") version "2.2.2"
}

group = "org.mwynhad.portal"
version = "1.0.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://oss.sonatype.org/content/groups/public/")
}

dependencies {
    // Paper API
    compileOnly("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    
    // Kotlin
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:1.6.2")
    
    // Redis for low-latency pub/sub
    implementation("io.lettuce:lettuce-core:6.3.0.RELEASE")
    
    // Netty for direct server-to-server communication
    implementation("io.netty:netty-all:4.1.104.Final")
    
    // Protocol Buffers for efficient serialization
    implementation("com.google.protobuf:protobuf-kotlin:3.25.1")
    
    // Compression
    implementation("org.lz4:lz4-java:1.8.0")
    
    // Metrics
    implementation("io.micrometer:micrometer-core:1.12.1")
}

tasks {
    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = "21"
            freeCompilerArgs = listOf("-Xjsr305=strict", "-opt-in=kotlin.RequiresOptIn")
        }
    }
    
    shadowJar {
        archiveClassifier.set("")
        relocate("io.lettuce", "org.mwynhad.portal.libs.lettuce")
        relocate("io.netty", "org.mwynhad.portal.libs.netty")
        relocate("kotlinx", "org.mwynhad.portal.libs.kotlinx")
        
        minimize {
            exclude(dependency("io.lettuce:.*"))
            exclude(dependency("io.netty:.*"))
        }
    }
    
    build {
        dependsOn(shadowJar)
    }
    
    runServer {
        minecraftVersion("1.21.1")
    }
    
    processResources {
        filteringCharset = "UTF-8"
        filesMatching("plugin.yml") {
            expand("version" to version)
        }
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}
