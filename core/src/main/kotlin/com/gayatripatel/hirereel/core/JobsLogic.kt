package com.gayatripatel.hirereel.core

import java.util.Locale

data class JobFilter(
    val query: String = "",
    val types: Set<JobType> = emptySet(),
    val remoteOnly: Boolean = false,
    val minSalaryK: Int = 0,
) {
    val isActive: Boolean get() = query.isNotBlank() || types.isNotEmpty() || remoteOnly || minSalaryK > 0

    fun apply(jobs: List<Job>): List<Job> {
        val terms = query.lowercase(Locale.ROOT).split(' ').filter { it.isNotBlank() }
        return jobs.filter { job ->
            val haystack = "${job.title} ${job.company} ${job.city} ${job.tags.joinToString(" ")}".lowercase(Locale.ROOT)
            terms.all { it in haystack } &&
                (types.isEmpty() || job.type in types) &&
                (!remoteOnly || job.remote) &&
                job.salaryMaxK >= minSalaryK
        }.sortedByDescending { it.postedAtMillis }
    }
}

object JobFormat {
    fun salary(job: Job): String = "\$${job.salaryMinK}k–\$${job.salaryMaxK}k"

    fun location(job: Job): String = if (job.remote) "${job.city} · Remote" else job.city

    fun posted(postedAtMillis: Long, nowMillis: Long): String {
        val minutes = (nowMillis - postedAtMillis) / 60_000
        return when {
            minutes < 60 -> "Just posted"
            minutes < 24 * 60 -> "${minutes / 60}h ago"
            minutes < 7 * 24 * 60 -> "${minutes / (24 * 60)}d ago"
            else -> "${minutes / (7 * 24 * 60)}w ago"
        }
    }

    fun applicants(count: Int): String = when {
        count < 10 -> "Be an early applicant"
        count < 1000 -> "$count applicants"
        else -> "%.1fk applicants".format(Locale.US, count / 1000.0)
    }

    /** 0:07 style timer for the recorder. */
    fun timer(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)
}

/** Reads the one-time code from an SMS delivered by the SMS Retriever API. */
object OtpParser {
    fun parse(message: String, length: Int = 6): String? {
        val code = Regex("(?<!\\d)\\d{$length}(?!\\d)")
        val keyword = Regex("(?i)\\b(code|otp)\\b").find(message)
        return (keyword?.let { code.find(message, it.range.last + 1) } ?: code.find(message))?.value
    }
}
