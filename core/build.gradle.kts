import com.google.protobuf.gradle.id
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intelliJPlatformModule)
    alias(libs.plugins.protobuf)
}


kotlin {
    jvmToolchain(25)
}

dependencies {
    compileOnly(libs.serializationJson)

    compileOnly(libs.grpc.protobuf)
    compileOnly(libs.grpc.stub)
    compileOnly(libs.grpc.kotlin.stub)
    compileOnly(libs.grpc.netty.shaded)
    compileOnly(libs.protobuf.java)
    implementation(libs.protobuf.kotlin)
    compileOnly(libs.javaxAnnotationApi)

    implementation(libs.ktor.server.content.negotiation) { isTransitive = false }
    implementation(libs.ktor.server.websockets) { isTransitive = false }
    implementation(libs.ktor.server.auth) { isTransitive = false }
    implementation(libs.ktor.server.netty) { isTransitive = false }
    implementation(libs.netty.transport.classes.epoll) { isTransitive = false }
    implementation(libs.netty.transport.classes.kqueue) { isTransitive = false }

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.rd.client.base")
        bundledModule("intellij.rider.rdclient.dotnet")
        bundledModule("intellij.libraries.grpc")
        bundledModule("intellij.libraries.grpc.netty.shaded")
        bundledModule("intellij.libraries.ktor.server.cio")
        bundledModule("intellij.libraries.ktor.client")
        bundledModule("intellij.libraries.ktor.io")
        bundledModule("intellij.libraries.ktor.utils")
        bundledModule("intellij.libraries.ktor.network.tls")
        bundledModule("intellij.libraries.kotlinx.serialization.json")
        bundledModule("intellij.libraries.netty.codec.http")
        bundledModule("intellij.libraries.netty.buffer")
        testFramework(TestFrameworkType.Bundled)
    }
}

sourceSets {
    main {
        proto {
            srcDir("src/main/protos")
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
