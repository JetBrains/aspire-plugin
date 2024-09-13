package com.jetbrains.rider.aspire.util

class WorkloadVersion(versionString: String) : Comparable<WorkloadVersion> {
    companion object {
        private const val PATTERN = "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?\$"
        private val versionRegex = Regex(PATTERN)
    }

    private val major: Int
    private val minor: Int
    private val patch: Int
    private val prerelease: String?

    init {
        val result = versionRegex.find(versionString)
        major = result?.groups?.get(1)?.value?.toInt() ?: 0
        minor = result?.groups?.get(2)?.value?.toInt() ?: 0
        patch = result?.groups?.get(3)?.value?.toInt() ?: 0
        prerelease = result?.groups?.get(4)?.value
    }

    override fun compareTo(other: WorkloadVersion): Int {
        if (this.major > other.major) return 1
        if (this.major < other.major) return -1

        if (this.minor > other.minor) return 1
        if (this.minor < other.minor) return -1

        if (this.patch > other.patch) return 1
        if (this.patch < other.patch) return -1

        if (this.prerelease == null && other.prerelease == null) return 0
        if (this.prerelease != null && other.prerelease == null) return 1
        if (this.prerelease == null && other.prerelease != null) return -1

        return this.prerelease!!.compareTo(other.prerelease!!)
    }

    override fun toString(): String = "$major.$minor.$patch"
}