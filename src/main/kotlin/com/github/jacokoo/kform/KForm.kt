package com.github.jacokoo.kform

import java.lang.RuntimeException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.reflect.KClass

class ViolationException(message: String, cause: Throwable? = null): RuntimeException(message, cause) {
    override fun fillInStackTrace(): Throwable {
        return this
    }
}

interface FormData {
    operator fun get(key: String): Any?
}

data class MapFormData(val map: Map<String, Any>): FormData {
    override fun get(key: String): Any? = map[key]
}

interface KForm {
    fun FormData.string(pattern: String? = null, fn: CheckFn<String?> = {}) =
        FormProperty(this, StringConverter(pattern), fn, createValueGetter())

    fun FormData.int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, fn: CheckFn<Int?> = {}) =
        FormProperty(this, IntConverter(min, max), fn, createValueGetter())

    fun FormData.long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, fn: CheckFn<Long?> = {}) =
        FormProperty(this, LongConverter(min, max), fn, createValueGetter())

    fun FormData.float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE, fn: CheckFn<Float?> = {}) =
        FormProperty(this, FloatConverter(min, max), fn, createValueGetter())

    fun FormData.bool(fn: CheckFn<Boolean?> = {}) =
        FormProperty(this, BooleanConverter(), fn, createValueGetter())

    fun FormData.date(format: String = "yyyy-MM-dd", fn: CheckFn<LocalDate?> = {}) =
        FormProperty(this, DateConverter(format), fn, createValueGetter())

    fun FormData.dateTime(format: String = "yyyy-MM-dd HH:mm:ss", fn: CheckFn<LocalDateTime?> = {}) =
        FormProperty(this, DateTimeConverter(format), fn, createValueGetter())

    fun <T: Enum<T>> FormData.enum(clazz: KClass<T>, fn: CheckFn<T?> = {}) =
        FormProperty(this, EnumConverter(clazz.java), fn, createValueGetter())

    fun FormData.stringList(separator: String = ",", pattern: String? = null, fn: CheckFn<List<String>?> = {}) =
        FormProperty(this, ListConverter(separator, StringConverter(pattern)), fn, createValueGetter())

    fun FormData.intList(separator: String = ",", min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, fn: CheckFn<List<Int>?> = {}) =
        FormProperty(this, ListConverter(separator, IntConverter(min, max)), fn, createValueGetter())

    fun FormData.longList(separator: String = ",", min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, fn: CheckFn<List<Long>?> = {}) =
        FormProperty(this, ListConverter(separator, LongConverter(min, max)), fn, createValueGetter())

    fun createValueGetter(): ValueGetter = DefaultValueGetter()
}
