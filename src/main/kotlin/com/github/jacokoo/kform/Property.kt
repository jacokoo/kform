package com.github.jacokoo.kform

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

object Checker {
    fun ensure(cond: Boolean, msg: (() -> String)? = null) {
        if (!cond) throw ViolationException(if (msg == null) "invalid" else msg())
    }

    internal fun <T> check(name: String, value: T, fn: CheckFn<T>) {
        try {
            fn(value)
        } catch (e: ViolationException) {
            throw ViolationException("$name: ${e.message}")
        }
    }
}

typealias CheckFn<T> = Checker.(T) -> Unit

interface ValueGetter {
    fun get(data: FormData, name: String): Any?
}

class DefaultValueGetter: ValueGetter {
    override fun get(data: FormData, name: String): Any? = data[name]
}

class FixedValueGetter(private val key: String): ValueGetter {
    override fun get(data: FormData, name: String): Any? = data[key]
}

class SnakeValueGetter: ValueGetter {
    lateinit var key: String
    override fun get(data: FormData, name: String): Any? {
        if (!::key.isInitialized) {
            key = name.replace(Regex("([A-Z])")) {
                "_${it.value.toLowerCase()}"
            }
        }
        return data[key]
    }
}

class FormProperty<T>(
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T?>,
    private val getter: ValueGetter = DefaultValueGetter()
): ReadOnlyProperty<Any?, T?> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T? =
        getter.get(data, property.name).let {
            if (it == null) null
            else converter.convert(property.name, it)
        }.also {
            Checker.check(property.name, it, fn)
        }

    fun name(name: String) = FormProperty(data, converter, fn, FixedValueGetter(name))
    fun snake() = FormProperty(data, converter, fn, SnakeValueGetter())

    fun required(fn: CheckFn<T> = {}): RequiredFormProperty<T> = RequiredFormProperty(getter, data, converter, fn)
    fun default(d: T, fn: CheckFn<T> = {}) = DefaultValueProperty(getter, data, converter, d, fn)
}

class RequiredFormProperty<T>(
    private val getter: ValueGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>
): ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        getter.get(data, property.name).let {
            if (it == null) throw ViolationException("${property.name} is required")
            else converter.convert(property.name, it)
        }.also {
            Checker.check(property.name, it, fn)
        }
}

class DefaultValueProperty<T>(
    private val getter: ValueGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val default: T,
    private val fn: CheckFn<T>
): ReadOnlyProperty<Any?, T> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        (getter.get(data, property.name)?.let { converter.convert(property.name, it) } ?: default).also {
            Checker.check(property.name, it, fn)
        }
}