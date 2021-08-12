package com.github.jacokoo.kform

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface Converter<T> {
    fun convert(name: String, input: Any): T

    fun fire(message: String, cause: Throwable? = null): Nothing {
        throw ViolationException(message, cause)
    }

    fun illegal(name: String): Nothing {
        fire("$name is illegal")
    }

    fun <R> wrap(msg: String, block: () -> R): R {
        try {
            return block()
        } catch (e: ViolationException) {
            throw e
        } catch (e: Exception) {
            fire(msg, null)
        }
    }

}

class StringConverter(pattern: String?): Converter<String> {
    private val regexp: Regex? = pattern?.let { Regex(it) }
    override fun convert(name: String, input: Any): String {
        val i = input.toString()
        if (regexp != null && !regexp.matches(i)) illegal(name)
        return i
    }
}

class IntConverter(private val min: Int, private val max: Int): Converter<Int> {
    override fun convert(name: String, input: Any): Int = when(input) {
        is Int -> input
        is String -> wrap("$name is not an int value") {
            input.toInt().also {
                if (it < min || it > max) illegal(name)
            }
        }
        else -> illegal(name)
    }
}

class LongConverter(private val min: Long, private val max: Long): Converter<Long> {
    override fun convert(name: String, input: Any): Long = when(input) {
        is Long -> input
        is Int -> input.toLong()
        is String -> wrap("$name is not a long value") {
            input.toLong().also {
                if (it < min || it > max) illegal(name)
            }
        }
        else -> illegal(name)
    }
}

class FloatConverter(private val min: Float, private val max: Float): Converter<Float> {
    override fun convert(name: String, input: Any): Float = when(input) {
        is Float -> input
        is Double -> input.toFloat()
        is String -> wrap("$name is not a float value") {
            input.toFloat().also {
                if (it < min || it > max) illegal(name)
            }
        }
        else -> illegal(name)
    }
}

class BooleanConverter(): Converter<Boolean> {
    override fun convert(name: String, input: Any): Boolean = when(input) {
        is Boolean -> input
        "false" -> false
        "0" -> false
        else -> true
    }
}

class DateConverter(private val format: String): Converter<LocalDate> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: Any): LocalDate = when(input) {
        is LocalDate -> input
        is String -> wrap("$name is not in date form $format") {
            LocalDate.parse(input, formatter)
        }
        else -> illegal(name)
    }
}

class DateTimeConverter(private val format: String): Converter<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: Any): LocalDateTime = when(input) {
        is LocalDateTime -> input
        is String -> wrap("$name is not in date time form $format") {
            LocalDateTime.parse(input, formatter)
        }
        else -> illegal(name)
    }
}

class EnumConverter<T: Enum<T>>(private val clazz: Class<T>): Converter<T> {
    private val intConverter = IntConverter(0, clazz.enumConstants.size - 1)
    override fun convert(name: String, input: Any): T = wrap("$name is not a valid enum value") {
        clazz.enumConstants[intConverter.convert(name, input)]
    }
}

class ListConverter<T>(private val separator: String = ",", private val sub: Converter<T>): Converter<List<T>> {
    override fun convert(name: String, input: Any): List<T> = when(input) {
        is Array<*> -> input.map { sub.convert(name, it ?: illegal(name)) }
        is List<*> -> input.map { sub.convert(name, it ?: illegal(name)) }
        is String -> input.split(separator).map { sub.convert(name, it.trim()) }
        else -> illegal(name)
    }
}