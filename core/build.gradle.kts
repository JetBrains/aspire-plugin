import com.google.protobuf.gradle.id
import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.ComposedJarTask

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
    compileOnly(libs.protobuf.kotlin)
    compileOnly(libs.javaxAnnotationApi)

    // No platform module ships these libraries, and the core module class loader cannot read
    // lib/*.jar. ComposedJarTask merges them into lib/modules/intellij.aspire.core.jar instead.
    // Keep the compile side and the merge side in step: a library listed here must also appear
    // as a pluginComposedModule below.
    compileOnly(libs.ktor.server.content.negotiation) { isTransitive = false }
    compileOnly(libs.ktor.server.websockets) { isTransitive = false }
    compileOnly(libs.ktor.server.auth) { isTransitive = false }
    compileOnly(libs.ktor.server.netty) { isTransitive = false }
    compileOnly(libs.netty.transport.classes.epoll) { isTransitive = false }
    compileOnly(libs.netty.transport.classes.kqueue) { isTransitive = false }

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.rd.client.base")
        bundledModule("intellij.rider.rdclient.dotnet")
        bundledModule("intellij.libraries.grpc")
        bundledModule("intellij.libraries.grpc.netty.shaded")
        bundledModule("intellij.libraries.protobuf")
        bundledModule("intellij.libraries.protobuf.kotlin")
        bundledModule("intellij.libraries.ktor.server.cio")
        bundledModule("intellij.libraries.ktor.client")
        bundledModule("intellij.libraries.ktor.io")
        bundledModule("intellij.libraries.ktor.utils")
        bundledModule("intellij.libraries.ktor.network.tls")
        bundledModule("intellij.libraries.kotlinx.serialization.json")
        bundledModule("intellij.libraries.netty.codec.http")
        bundledModule("intellij.libraries.netty.buffer")

        // The merge side of the six compileOnly libraries above.
        pluginComposedModule(project.dependencies.create(libs.ktor.server.content.negotiation.get()))
        pluginComposedModule(project.dependencies.create(libs.ktor.server.websockets.get()))
        pluginComposedModule(project.dependencies.create(libs.ktor.server.auth.get()))
        pluginComposedModule(project.dependencies.create(libs.ktor.server.netty.get()))
        pluginComposedModule(project.dependencies.create(libs.netty.transport.classes.epoll.get()))
        pluginComposedModule(project.dependencies.create(libs.netty.transport.classes.kqueue.get()))

        testFramework(TestFrameworkType.Bundled)
    }
}

// Entries to drop from the merged libraries.
val composedJarExcludes = listOf(
    // The session authentication provider of ktor-server-auth needs ktor-server-sessions, and that
    // library needs a newer kotlin-reflect than the IDE bundles. The server installs bearer
    // authentication only, so these classes never load. The plugin verifier reads the merged bytes
    // as plugin code, so it reports the unreachable reference and fails the build.
    "io/ktor/server/auth/Session*",
    // Junk in a merged jar.
    "META-INF/INDEX.LIST",
    "META-INF/maven/**",
    "META-INF/versions/**",
    "META-INF/*.SF",
    "META-INF/*.DSA",
    "META-INF/*.RSA",
)

tasks.withType<ComposedJarTask>().configureEach {
    exclude(composedJarExcludes)
    // ComposedJarTask does not track its copy-spec patterns, so the task stays up to date and the
    // build cache can restore an unfiltered jar. Declare the patterns to make the key follow them.
    inputs.property("composedJarExcludes", composedJarExcludes)
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
