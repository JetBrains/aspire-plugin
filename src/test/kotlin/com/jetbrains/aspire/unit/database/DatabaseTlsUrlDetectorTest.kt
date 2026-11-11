package com.jetbrains.aspire.unit.database

import com.jetbrains.aspire.database.DatabaseTlsUrlDetector
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DatabaseTlsUrlDetectorTest {
    private val detector = DatabaseTlsUrlDetector()

    @Test
    fun `local mongo connection with tls is detected`() {
        val url = "mongodb://localhost:27017/?tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `local mongo connection with credentials and tls is detected`() {
        val url = "mongodb://admin:password@localhost:27017/?authSource=admin&tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `local mongo connection with ssl option is detected`() {
        val url = "mongodb://127.0.0.1:27017?ssl=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `local mongo connection with ipv6 host is detected`() {
        val url = "mongodb://[::1]:27017/?tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `mongo connection with multiple local hosts is detected`() {
        val url = "mongodb://localhost:27017,127.0.0.1:27018/?tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `mongo connection without tls is not detected`() {
        val url = "mongodb://localhost:27017/?authSource=admin"

        val result = detector.isLocalMongoTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `mongo connection with disabled tls is not detected`() {
        val url = "mongodb://localhost:27017/?tls=false"

        val result = detector.isLocalMongoTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `mongo connection with conflicting tls options is not detected`() {
        val url = "mongodb://localhost:27017/?ssl=true&tls=false"

        val result = detector.isLocalMongoTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `mongo connection with a remote host is not detected`() {
        val url = "mongodb://localhost:27017,mongo.example.com:27017/?tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `mongo srv connection is not detected`() {
        val url = "mongodb+srv://localhost/?tls=true"

        val result = detector.isLocalMongoTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `local redis connection with ssl is detected`() {
        val url = "jdbc:redis://localhost:6379?ssl=true"

        val result = detector.isLocalRedisTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `local redis connection with credentials and ssl is detected`() {
        val url = "jdbc:redis://default:password@localhost:6379?ssl=true"

        val result = detector.isLocalRedisTlsConnection(url)

        assertTrue(result)
    }

    @Test
    fun `redis connection without ssl is not detected`() {
        val url = "jdbc:redis://localhost:6379"

        val result = detector.isLocalRedisTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `redis connection with disabled ssl is not detected`() {
        val url = "jdbc:redis://localhost:6379?ssl=false"

        val result = detector.isLocalRedisTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `redis connection with a remote host is not detected`() {
        val url = "jdbc:redis://redis.example.com:6379?ssl=true"

        val result = detector.isLocalRedisTlsConnection(url)

        assertFalse(result)
    }

    @Test
    fun `redis connection without jdbc prefix is not detected`() {
        val url = "redis://localhost:6379?ssl=true"

        val result = detector.isLocalRedisTlsConnection(url)

        assertFalse(result)
    }
}
