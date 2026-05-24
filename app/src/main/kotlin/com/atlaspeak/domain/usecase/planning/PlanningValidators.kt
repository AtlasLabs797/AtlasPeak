package com.atlaspeak.domain.usecase.planning

private val timeRegex = Regex("^(?:[01]\\d|2[0-3]):[0-5]\\d$")

fun String?.isValidClockTime(): Boolean {
    return this == null || matches(timeRegex)
}
