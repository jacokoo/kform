package com.github.jacokoo.kform

import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

class ViolationException(message: String, cause: Throwable? = null): RuntimeException(message, cause)

interface FormData {
    operator fun get(key: String): Any?
}

data class MapFormData(val map: Map<String, Any>): FormData {
    override fun get(key: String): Any? = map[key]
}

interface KForm {
    fun FormData.string(pattern: String? = null) = FormProperty(this, StringConverter(pattern))
    fun FormData.int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) = FormProperty(this, IntConverter(min, max))
    fun FormData.long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE) = FormProperty(this, LongConverter(min, max))
    fun FormData.float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE) = FormProperty(this, FloatConverter(min, max))
    fun FormData.bool() = FormProperty(this, BooleanConverter())
    fun FormData.date(format: String = "yyyy-MM-dd") = FormProperty(this, DateConverter(format))
    fun FormData.dateTime(format: String = "yyyy-MM-dd HH:mm:ss") = FormProperty(this, DateTimeConverter(format))
    fun <T: Enum<T>> FormData.enum(clazz: KClass<T>) = FormProperty(this, EnumConverter(clazz.java))

    fun FormData.stringList(separator: String = ",", pattern: String? = null) =
        FormProperty(this, ListConverter(separator, StringConverter(pattern)))

    fun FormData.intList(separator: String = ",", min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE) =
        FormProperty(this, ListConverter(separator, IntConverter(min, max)))

    fun FormData.longList(separator: String = ",", min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE) =
        FormProperty(this, ListConverter(separator, LongConverter(min, max)))

}
