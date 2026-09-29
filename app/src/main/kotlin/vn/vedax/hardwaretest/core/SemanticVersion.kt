package vn.vedax.hardwaretest.core

/** Compares the numeric GitHub tag format accepted by the in-app updater. */
object SemanticVersion {
    private val pattern = Regex("^v?(\\d+)\\.(\\d+)(?:\\.(\\d+))?$")

    fun compare(candidate: String, current: String): Int {
        val left = parse(candidate)
        val right = parse(current)
        for (index in 0..2) {
            if (left[index] != right[index]) return left[index].compareTo(right[index])
        }
        return 0
    }

    private fun parse(value: String): List<Long> {
        val match = pattern.matchEntire(value)
            ?: throw IllegalArgumentException("Tag phiên bản không đúng dạng v1.2.3: $value")
        return listOf(match.groupValues[1].toLong(), match.groupValues[2].toLong(),
            match.groupValues[3].takeIf { it.isNotEmpty() }?.toLong() ?: 0L)
    }
}
