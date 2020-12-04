package com.github.jacokoo.kform

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object Checker {
    fun assert(cond: Boolean, msg: String) {
        if (!cond) throw ViolationException("assert fail: $msg")
    }

    internal fun <T> check(name: String, value: T, fn: CheckFn<T>) {
        try {
            fn(value)
        } catch (e: ViolationException) {
            throw ViolationException("$name ${e.message}")
        }
    }
}

typealias CheckFn<T> = Checker.(T) -> Unit

class FormProperty<T>(
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T?>
): ReadOnlyProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        data[property.name].let {
            if (it == null) null
            else converter.convert(property.name, it)
        }.also {
            Checker.check(property.name, it, fn)
        }

    fun required(fn: CheckFn<T> = {}): RequiredFormProperty<T> = RequiredFormProperty(data, converter, fn)
    fun default(d: T, fn: CheckFn<T> = {}) = DefaultValueProperty(data, converter, d, fn)
}

class RequiredFormProperty<T>(
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>
): ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        data[property.name].let {
            if (it == null) throw ViolationException("${property.name} is required")
            else converter.convert(property.name, it)
        }.also {
            Checker.check(property.name, it, fn)
        }
}

class DefaultValueProperty<T>(
    private val data: FormData,
    private val converter: Converter<T>,
    private val default: T,
    private val fn: CheckFn<T>
): ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        (data[property.name]?.let { converter.convert(property.name, it) } ?: default).also {
            Checker.check(property.name, it, fn)
        }
}