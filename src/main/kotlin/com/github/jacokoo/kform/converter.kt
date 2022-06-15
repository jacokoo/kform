package com.github.jacokoo.kform

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal fun <T> invalid(name: String, s: String = "invalid") = Result.failure<T>(ViolationException("$name is $s"))

interface Converter<T> {
    fun convert(name: String, input: Any): Result<T>

    fun fail(message: String, cause: Throwable? = null) =
        Result.failure<T>(ViolationException(message, cause))

    fun illegal(name: String): Result<T> = invalid(name)

    fun wrap(msg: String, block: () -> Result<T>): Result<T> =
        try {
            block()
        } catch (e: Exception) {
            fail(msg, null)
        }

}

class StringConverter(pattern: String?, private val maxLength: Int?): Converter<String> {
    private val regexp: Regex? = pattern?.let { Regex(it) }
    override fun convert(name: String, input: Any): Result<String> {
        val i = input.toString()
        if (regexp != null && !regexp.matches(i)) illegal(name)
        if (maxLength != null && i.length > maxLength) illegal(name)
        return Result.success(i)
    }
}

class IntConverter(private val min: Int, private val max: Int): Converter<Int> {
    override fun convert(name: String, input: Any) = when(input) {
        is Int -> Result.success(input)
        is String -> wrap("$name is not an int value") {
            input.toInt().let {
                if (it < min || it > max) illegal(name)
                else Result.success(it)
            }
        }
        else -> illegal(name)
    }
}

class LongConverter(private val min: Long, private val max: Long): Converter<Long> {
    override fun convert(name: String, input: Any) = when(input) {
        is Long -> Result.success(input)
        is Int -> Result.success(input.toLong())
        is String -> wrap("$name is not a long value") {
            input.toLong().let {
                if (it < min || it > max) illegal(name)
                else Result.success(it)
            }
        }
        else -> illegal(name)
    }
}

class FloatConverter(private val min: Float, private val max: Float): Converter<Float> {
    override fun convert(name: String, input: Any) = when(input) {
        is Float -> Result.success(input)
        is Double -> Result.success(input.toFloat())
        is String -> wrap("$name is not a float value") {
            input.toFloat().let {
                if (it < min || it > max) illegal(name)
                else Result.success(it)
            }
        }
        else -> illegal(name)
    }
}

class BooleanConverter: Converter<Boolean> {
    override fun convert(name: String, input: Any) = Result.success(when(input) {
        is Boolean -> input
        "false" -> false
        "0" -> false
        else -> true
    })
}

class DateConverter(private val format: String): Converter<LocalDate> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDate -> Result.success(input)
        is String -> wrap("$name is not in date form $format") {
            Result.success(LocalDate.parse(input, formatter))
        }
        else -> illegal(name)
    }
}

class DateTimeConverter(private val format: String): Converter<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: Any) = when(input) {
        is LocalDateTime -> Result.success(input)
        is String -> wrap("$name is not in date time form $format") {
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

class ListConverter<T>(
    private val separator: String = ",",
    private val sub: Converter<T>,
    private val maxLength: Int?
): Converter<List<T>> {

    override fun convert(name: String, input: Any): Result<List<T>> = when(input) {
        is Array<*> -> inner(name, input.asIterable())
        is List<*> -> inner(name, input)
        is String -> {
            if (maxLength != null && input.length > maxLength) illegal(name)
            inner(name, input.split(separator).map { it.trim() })
        }
        else -> illegal(name)
    }

    @Suppress("unchecked_cast")
    private fun inner(name: String, items: Iterable<*>): Result<List<T>> =
        Result.success(items.map {
            if (it == null) return illegal(name)
            val re = sub.convert(name, it)
            if (re.isFailure) return re as Result<List<T>>
            re.getOrThrow()
        })
}