package my.noveldokusha.core.domain

data class AppVersion(val major: Int, val minor: Int, val fix: Int) : Comparable<AppVersion> {

    companion object {
        fun fromString(version: String): AppVersion = runCatching {
            version.uppercase()
                .removePrefix("V")
                .split(".")
                .map { it.toInt() }
                .let { AppVersion(major = it[0], minor = it[1], fix = it[2]) }
        }.getOrDefault(AppVersion(0, 0, 0))
    }

    override fun compareTo(other: AppVersion): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.fix })

    override fun toString() = "v$major.$minor.$fix"
}