import com.google.protobuf.gradle.id

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.protobuf)
    id("org.jetbrains.intellij.platform.module")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()

    intellijPlatform {
        defaultRepositories()
        jetbrainsRuntime()
    }
}

dependencies {
    implementation(libs.serializationJson)

    compileOnly(libs.grpc.protobuf)
    compileOnly(libs.grpc.stub)
    compileOnly(libs.grpc.kotlin.stub)
    compileOnly(libs.grpc.netty.shaded)
    compileOnly(libs.protobuf.java)
    compileOnly(libs.protobuf.kotlin)
    compileOnly("javax.annotation:javax.annotation-api:1.3.2")

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        jetbrainsRuntime()

        bundledModule("intellij.libraries.grpc.netty.shaded")
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/kotlin/com/jetbrains/aspire/protos")
        }
    }
}

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    plugins {
        id("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${libs.versions.grpc.get()}"
        }
        id("grpckt") {
            artifact = "io.grpc:protoc-gen-grpc-kotlin:${libs.versions.grpcKotlin.get()}:jdk8@jar"
        }
    }
    generateProtoTasks {
        all().forEach { task ->
            task.plugins {
                id("grpc")
                id("grpckt")
            }
            task.builtins {
                id("kotlin")
            }
        }
    }
}
