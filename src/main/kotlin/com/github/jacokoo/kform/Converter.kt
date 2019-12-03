package com.github.jacokoo.kform

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

interface Converter<T> {
    fun convert(name: String, input: String): T

    fun fire(message: String, cause: Throwable? = null): Nothing {
        throw ViolationException(message, cause)
    }

    fun <R> wrap(msg: String, block: () -> R): R {
        try {
            return block()
        } catch (e: ViolationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            fire(msg, e)
        }
    }

}

class StringConverter(pattern: String?): Converter<String> {
    private val regexp: Regex? = pattern?.let { Regex(it) }
    override fun convert(name: String, input: String): String {
        if (regexp != null && !regexp.matches(input)) fire("$name is illegal")
        return input
    }
}

class IntConverter(private val min: Int, private val max: Int): Converter<Int> {
    override fun convert(name: String, input: String): Int = wrap("$name is not an int value") {
        input.toInt().also {
            if (it < min || it > max) fire("$name is illegal")
        }
    }
}

class LongConverter(private val min: Long, private val max: Long): Converter<Long> {
    override fun convert(name: String, input: String): Long = wrap("$name is not a long value") {
        input.toLong().also {
            if (it < min || it > max) fire("$name is illegal")
        }
    }
}

class FloatConverter(private val min: Float, private val max: Float): Converter<Float> {
    override fun convert(name: String, input: String): Float = wrap("$name is not a float value") {
        input.toFloat().also {
            if (it < min || it > max) fire("$name is illegal")
        }
    }
}

class DateConverter(private val format: String): Converter<LocalDate> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: String): LocalDate = wrap("$name is not in date form $format") {
        LocalDate.parse(input, formatter)
    }
}

class DateTimeConverter(private val format: String): Converter<LocalDateTime> {
    private val formatter = DateTimeFormatter.ofPattern(format)
    override fun convert(name: String, input: String): LocalDateTime = wrap("$name is not in date time form $format") {
        LocalDateTime.parse(input, formatter)
    }
}

class EnumConverter<T: Enum<T>>(private val clazz: Class<T>): Converter<T> {
    private val intConverter = IntConverter(0, clazz.enumConstants.size - 1)
    override fun convert(name: String, input: String): T = clazz.enumConstants[intConverter.convert(name, input)]
}

class ListConverter<T>(private val separator: String = ",", private val sub: Converter<T>): Converter<List<T>> {
    override fun convert(name: String, input: String): List<T> =
        input.split(separator).map { sub.convert(name, it.trim()) }
}