package com.github.jacokoo.kform

import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KClass
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

open class FormProperty<T>(
    private val form: KForm,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>,
    private var key: KeyGetter
): CachedProperty<T?>() {
    override fun doGet(name: String): Result<T?> = key(name).let { n ->
        data[n].let {
            if (it == null) Result.success(null)
            else converter.convert(n, it).check(n, fn)
        }
    }

    fun name(name: String) = this.also { key = fixedKey(name) }
    fun snake() = this.also { key = SnakeKey }

    fun required(fn: CheckFn<T> = { true }) =
        RequiredFormProperty(key, data, converter, fn).also { form.properties.replace(this, it) }

    fun default(d: T, fn: CheckFn<T> = { true }) =
        DefaultValueProperty(key, data, converter, d, fn).also { form.properties.replace(this, it) }

}

class RequiredFormProperty<T>(
    private val key: KeyGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>
): CachedProperty<T>() {
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        data[n].let {
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
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        data[n].let {
            if (it == null) Result.success(default)
            else converter.convert(n, it).check(n, fn)
        }
    }
}

class PrimitiveListProperty<T>(
    private val separator: String,
    private val converter: Converter<T>,
    private val data: FormData,
    private val fn: CheckFn<List<T>>,
    private var key: KeyGetter
): CachedProperty<List<T>>() {
    init {
        if (data !is SupportPrimitiveList) throw RuntimeException("the form data $data doesn't support primitive list")
    }

    fun name(name: String) = this.also { key = fixedKey(name) }
    fun snake() = this.also { key = SnakeKey }

    override fun doGet(name: String): Result<List<T>> = key(name).let { n ->
        (data as SupportPrimitiveList).list(n, separator)
            .innerMap { converter.convert(n, it) }
            .check(n, fn)
    }
}

private fun <T: KForm> createBean(name: String, clazz: KClass<T>, data: FormData): Result<T> =
    Result.success(create(clazz.java, data, false).apply {
        val e = check()
        if (e != null) return Result.failure(e)
    })

class BeanProperty<T: KForm>(
    private val form: KForm,
    private val clazz: KClass<T>,
    private val data: FormData,
    private val fn: CheckFn<T>,
    private var key: KeyGetter
): CachedProperty<T?>() {
    init {
        if (data !is SupportBean) throw RuntimeException("the form data $data doesn't support bean")
    }

    override fun doGet(name: String): Result<T?> = key(name).let { n ->
        (data as SupportBean).bean(n)?.flatMap { createBean(n, clazz, it) }?.check(n, fn)
    } ?: Result.success(null)

    fun name(name: String) = this.also { key = fixedKey(name) }
    fun snake() = this.also { key = SnakeKey }

    fun required(fn: CheckFn<T> = { true }) =
        RequiredBeanProperty(clazz, data, fn, key).also { form.properties.replace(this, it) }
}

class RequiredBeanProperty<T: KForm> internal constructor(
    private val clazz: KClass<T>,
    private val data: FormData,
    private val fn: CheckFn<T>,
    private var key: KeyGetter
): CachedProperty<T>() {
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        (data as SupportBean).bean(n)?.flatMap { createBean(n, clazz, it) }?.check(n, fn) ?: invalid(n)
    }
}

class ListBeanProperty<T: KForm>(
    private val clazz: KClass<T>,
    private val data: FormData,
    private val fn: CheckFn<List<T>>,
    private var key: KeyGetter
): CachedProperty<List<T>>() {
    init {
        if (data !is SupportBeanList) throw RuntimeException("the form data $data doesn't support bean list")
    }

    override fun doGet(name: String): Result<List<T>> = key(name).let { n ->
        (data as SupportBeanList).list(n).innerMap { createBean(n, clazz, it) }.check(n, fn)
    }
}

abstract class CachedProperty<T>: ReadOnlyProperty<Any?, T> {
    internal var value: Result<T>? = null

    @Suppress("unchecked_cast")
    override fun getValue(thisRef: Any?, property: KProperty<*>): T =
        (value ?: get(property.name)).getOrThrow()

    @Suppress("unchecked_cast")
    fun get(name: String): Result<T> =
        value ?: doGet(name).also { value = it }

    abstract fun doGet(name: String): Result<T>
}

@Suppress("unchecked_cast")
internal fun <T, R> Result<List<T>>.innerMap(fn: (T) -> Result<R>): Result<List<R>> =
    map { d -> d.map { fn(it).run {
        if (isSuccess) getOrThrow()
        else return this as Result<List<R>>
    }}}

internal fun <R> Result<R>.check(name: String, fn: CheckFn<R>): Result<R> =
    if (isSuccess && !fn(getOrThrow())) { invalid(name) } else this

internal fun <T, R> Result<T>.flatMap(fn: (T) -> Result<R>): Result<R> = when {
    isFailure -> Result.failure(exceptionOrNull()!!)
    else -> fn(getOrThrow())
}
