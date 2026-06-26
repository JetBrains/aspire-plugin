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

    // The embedded DCP Ktor server (com.jetbrains.aspire.worker.dcp). The platform bundles the
    // Ktor CIO server, client, IO, utils, TLS and the kotlinx-json content converter, but NOT the
    // server-side ContentNegotiation, WebSockets and Authentication plugins, so those three are
    // shipped. They are resolved non-transitively because every transitive Ktor dependency they
    // need (server-core, http, http-auth, io, utils, serialization, websocket codec) is already
    // provided by the bundled modules.
    implementation(libs.ktor.server.content.negotiation) { isTransitive = false }
    implementation(libs.ktor.server.websockets) { isTransitive = false }
    implementation(libs.ktor.server.auth) { isTransitive = false }

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.rd.client.base")
        bundledModule("intellij.rider.rdclient.dotnet")
        bundledModule("intellij.libraries.grpc")
        bundledModule("intellij.libraries.grpc.netty.shaded")
        // Bundled Ktor stack consumed by the embedded DCP server. ktor.client is a fat module that
        // also carries io.ktor.http.*, the io.ktor.websocket.* frame codec and the
        // io.ktor.serialization.kotlinx.json.* converter; server.cio carries server-core + the CIO
        // engine + sslConnector. kotlinx.serialization.json backs the @Serializable DTOs at runtime.
        bundledModule("intellij.libraries.ktor.server.cio")
        bundledModule("intellij.libraries.ktor.client")
        bundledModule("intellij.libraries.ktor.io")
        bundledModule("intellij.libraries.ktor.utils")
        bundledModule("intellij.libraries.ktor.network.tls")
        bundledModule("intellij.libraries.kotlinx.serialization.json")
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
