import com.google.protobuf.gradle.id
import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.serialization)
    alias(libs.plugins.intelliJPlatformModule)
    alias(libs.plugins.protobuf)
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

    testImplementation(libs.junit)
    testImplementation(libs.testng)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test) {
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-core-jvm")
        exclude(group = "org.jetbrains.kotlinx", module = "kotlinx-coroutines-bom")
    }

    intellijPlatform {
        rider(providers.gradleProperty("riderVersion")) {
            useInstaller = false
            useCache = true
        }
        bundledModule("intellij.libraries.grpc")
        bundledModule("intellij.libraries.grpc.netty.shaded")
        testFramework(TestFrameworkType.Bundled)
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
