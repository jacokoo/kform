package com.github.jacokoo.kform

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun <T> invalid(name: String, s: String = "invalid") = Result.failure<T>(ViolationException("$name is $s"))

internal fun describeIt(vararg pairs: Pair<String, Any?>): Map<String, Any> =
    pairs.mapNotNull { p -> p.second?.let { p.first to it } }.toMap()

interface Converter<T>: ConverterDescribe {
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

    override fun doDescribe(): Map<String, Any> = mapOf()
}

class StringConverter(
    val pattern: String?, private val maxLength: Int?,
    override val metadata: Map<String, Any>
): Converter<String> {
    private val regexp: Regex? = pattern?.let { Regex(it) }
    override fun convert(name: String, input: Any): Result<String> {
        val i = input.toString()
        if (regexp != null && !regexp.matches(i)) return illegal(name)
        if (maxLength != null && i.length > maxLength) return illegal(name)
        return Result.success(i)
    }

    override fun doDescribe() = describeIt(
        "pattern" to pattern,
        "max-length" to maxLength
    )
}

abstract class ComparableConverter<T: Comparable<T>>(val min: T, val max: T): Converter<T> {
    fun T.ensureRange(name: String) =
        if (this < min || this > max) illegal(name)
        else Result.success(this)
}

class IntConverter(
    min: Int, max: Int, override val metadata: Map<String, Any>
): ComparableConverter<Int>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Int -> input.ensureRange(name)
        is String -> wrap("$name is not an int value") {
            input.toInt().ensureRange(name)
        }
        else -> illegal(name)
    }

    override fun doDescribe() = describeIt(
        "min" to min.takeIf { it != Int.MIN_VALUE },
        "max" to max.takeIf { it != Int.MAX_VALUE }
    )
}

class LongConverter(
    min: Long, max: Long, override val metadata: Map<String, Any>
): ComparableConverter<Long>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Long -> input.ensureRange(name)
        is Int -> input.toLong().ensureRange(name)
        is String -> wrap("$name is not a long value") {
            input.toLong().ensureRange(name)
        }
        else -> illegal(name)
    }

    override fun doDescribe() = describeIt(
        "min" to min.takeIf { it != Long.MIN_VALUE },
        "max" to max.takeIf { it != Long.MAX_VALUE }
    )
}

class FloatConverter(
    min: Float, max: Float, override val metadata: Map<String, Any>
): ComparableConverter<Float>(min, max) {
    override fun convert(name: String, input: Any) = when(input) {
        is Float -> input.ensureRange(name)
        is Double -> input.toFloat().ensureRange(name)
        is String -> wrap("$name is not a float value") {
            input.toFloat().ensureRange(name)
        }
        else -> illegal(name)
    }

    override fun doDescribe() = describeIt(
        "min" to min.takeIf { it != Float.MIN_VALUE },
        "max" to max.takeIf { it != Float.MAX_VALUE }
    )
}

class BooleanConverter(override val metadata: Map<String, Any>): Converter<Boolean> {
    override fun convert(name: String, input: Any) = Result.success(when(input) {
        is Boolean -> input
        "false" -> false
        "0" -> false
        0 -> false
        else -> true
    })

    override fun doDescribe(): Map<String, Any> = mapOf()
}

private val time = LocalDateTime.of(2022, 12, 12, 18, 30, 30)

class DateConverter(
    private val formatter: DateTimeFormatter,
    override val metadata: Map<String, Any>
): Converter<LocalDate> {
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDate -> Result.success(input)
        is String -> wrap("$name is not in date form $formatter") {
            Result.success(LocalDate.parse(input, formatter))
        }
        else -> illegal(name)
    }

    override fun doDescribe() = describeIt(
        "eg." to formatter.format(time)
    )
}

class DateTimeConverter(
    private val formatter: DateTimeFormatter,
    override val metadata: Map<String, Any>
): Converter<LocalDateTime> {
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDateTime -> Result.success(input)
        is String -> wrap("$name is not in date time form $formatter") {
            Result.success(LocalDateTime.parse(input, formatter))
        }
        else -> illegal(name)
    }

    override fun doDescribe() = describeIt(
        "eg." to formatter.format(time)
    )
}

class EnumConverter<T: Enum<T>>(
    private val clazz: Class<T>,
    override val metadata: Map<String, Any>
): Converter<T> {
    private val intConverter = IntConverter(0, clazz.enumConstants.size - 1, mapOf())
    override fun convert(name: String, input: Any) = wrap("$name is not a valid enum value") {
        intConverter.convert(name, input).map { clazz.enumConstants[it] }
    }

    override fun doDescribe() = describeIt(
        "value" to clazz.enumConstants.mapIndexed { i, it -> "$i: ${it.name}" }.joinToString()
    )
}