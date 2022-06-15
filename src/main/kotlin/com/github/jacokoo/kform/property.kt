package com.github.jacokoo.kform

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

typealias CheckFn<T> = (T) -> Boolean
typealias KeyGetter = (String) -> String

internal val defaultKey: KeyGetter = { it }
internal val fixedKey: (String) -> KeyGetter = { name -> { name } }
val SnakeKey: KeyGetter = { p ->
    p.replace(Regex("([A-Z])")) {
        "_${it.value.lowercase()}"
    }
}

abstract class CachedProperty<T>: ReadOnlyProperty<Any?, T> {
    private var value: Result<T>? = null

    @Suppress("unchecked_cast")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        (value ?: validate(property.name)).getOrThrow()

    @Suppress("unchecked_cast")
    fun validate(name: String): Result<T> =
        value ?: doValidate(name).also { value = it }

    abstract fun doValidate(name: String): Result<T>
}

private fun <R> Result<R>.check(name: String, fn: CheckFn<R>): Result<R> =
    if (isSuccess && !fn(getOrThrow())) { invalid(name) } else this

open class FormProperty<T>(
    private val form: KForm,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>,
    private var key: KeyGetter = defaultKey
): CachedProperty<T?>() {
    override fun doValidate(name: String): Result<T?> = key(name).let { n ->
        data[n].let {
            if (it == null) Result.success(null)
            else converter.convert(n, it).check(n, fn)
        }
    }

    fun name(name: String) = this.also { it.key = fixedKey(name) }
    fun snake() = this.also { it.key = SnakeKey }

    fun required(fn: CheckFn<T> = { true }) =
        RequiredFormProperty(key, data, converter, fn).also { form.replace(this, it) }

    fun default(d: T, fn: CheckFn<T> = { true }) =
        DefaultValueProperty(key, data, converter, d, fn).also { form.replace(this, it) }

}

class RequiredFormProperty<T>(
    private val key: KeyGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>
): CachedProperty<T>() {
    override fun doValidate(name: String): Result<T> = key(name).let { n ->
        data[name].let {
            if (it == null) invalid(n, "required")
            else converter.convert(n, it).check(n, fn)
        }
    }
}

class DefaultValueProperty<T>(
    private val key: KeyGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val default: T,
    private val fn: CheckFn<T>
): CachedProperty<T>() {
    override fun doValidate(name: String): Result<T> = key(name).let { n ->
        data[name].let {
            if (it == null) Result.success(default)
            else converter.convert(n, it).check(name, fn)
        }
    }
}