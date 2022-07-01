package com.github.jacokoo.kform

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun <T> invalid(name: String, s: String = "invalid") = Result.failure<T>(ViolationException("$name is $s"))

interface Converter<T> {
    fun convert(name: String, input: Any): Result<T>

    fun fail(message: String) =
        Result.failure<T>(ViolationException(message))

    fun illegal(name: String): Result<T> = invalid(name)

    fun wrap(msg: String, block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: Exception) {
            fail(msg)
        }
}

class StringConverter(pattern: String?, private val maxLength: Int?): Converter<String> {
    private val regexp: Regex? = pattern?.let { Regex(it) }
    override fun convert(name: String, input: Any): Result<String> {
        val i = input.toString()
        if (regexp != null && !regexp.matches(i)) return illegal(name)
        if (maxLength != null && i.length > maxLength) return illegal(name)
        return Result.success(i)
    }
}

abstract class ComparableConverter<T: Comparable<T>>(val min: T, val max: T): Converter<T> {
    fun T.ensureRange(name: String) =
        if (this < min || this > max) illegal(name)
        else Result.success(this)
}

class IntConverter(min: Int, max: Int): ComparableConverter<Int>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Int -> input.ensureRange(name)
        is String -> wrap("$name is not an int value") {
            input.toInt().ensureRange(name)
        }
        else -> illegal(name)
    }
}

class LongConverter(min: Long, max: Long): ComparableConverter<Long>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Long -> input.ensureRange(name)
        is Int -> input.toLong().ensureRange(name)
        is String -> wrap("$name is not a long value") {
            input.toLong().ensureRange(name)
        }
        else -> illegal(name)
    }
}

class FloatConverter(min: Float, max: Float): ComparableConverter<Float>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Float -> input.ensureRange(name)
        is Double -> input.toFloat().ensureRange(name)
        is String -> wrap("$name is not a float value") {
            input.toFloat().ensureRange(name)
        }
        else -> illegal(name)
    }
}

class BooleanConverter: Converter<Boolean> {
    override fun convert(name: String, input: Any) = Result.success(when(input) {
        is Boolean -> input
        "false" -> false
        "0" -> false
        0 -> false
        else -> true
    })
}

class DateConverter(private val formatter: DateTimeFormatter): Converter<LocalDate> {
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDate -> Result.success(input)
        is String -> wrap("$name is not in date form $formatter") {
            Result.success(LocalDate.parse(input, formatter))
        }
        else -> illegal(name)
    }
}

class DateTimeConverter(private val formatter: DateTimeFormatter): Converter<LocalDateTime> {
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDateTime -> Result.success(input)
        is String -> wrap("$name is not in date time form $formatter") {
            Result.success(LocalDateTime.parse(input, formatter))
        }
        else -> illegal(name)
    }
}

class EnumConverter<T: Enum<T>>(private val clazz: Class<T>): Converter<T> {
    private val intConverter = IntConverter(0, clazz.enumConstants.size - 1)
    override fun convert(name: String, input: Any) = wrap("$name is not a valid enum value") {
        intConverter.convert(name, input).map { clazz.enumConstants[it] }
    }
}