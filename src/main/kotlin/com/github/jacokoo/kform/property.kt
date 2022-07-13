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
    private val clazz: Class<T>,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>,
    private var key: KeyGetter
): CachedProperty<T?>(), PropertyDescribe {
    override fun doGet(name: String): Result<T?> = key(name).let { n ->
        data[n].let {
            if (it == null) Result.success(null)
            else converter.convert(n, it).check(n, fn)
        }
    }

    fun name(name: String) = this.also { key = fixedKey(name) }
    fun snake() = this.also { key = SnakeKey }

    fun required(fn: CheckFn<T> = { true }) =
        RequiredFormProperty(clazz, key, data, converter, fn).also { form.properties.replace(this, it) }

    fun default(d: T, fn: CheckFn<T> = { true }) =
        DefaultValueProperty(clazz, key, data, converter, d, fn).also { form.properties.replace(this, it) }

    override fun describe(name: String) = NormalFieldType(clazz, key(name), converter.describe(), false, null)
}

class RequiredFormProperty<T>(
    private val clazz: Class<T>,
    private val key: KeyGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val fn: CheckFn<T>
): CachedProperty<T>(), PropertyDescribe {
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        data[n].let {
            if (it == null) invalid(n, "required")
            else converter.convert(n, it).check(n, fn)
        }
    }

    override fun describe(name: String) = NormalFieldType(clazz, key(name), converter.describe(), true, null)
}

class DefaultValueProperty<T>(
    private val clazz: Class<T>,
    private val key: KeyGetter,
    private val data: FormData,
    private val converter: Converter<T>,
    private val default: T,
    private val fn: CheckFn<T>
): CachedProperty<T>(), PropertyDescribe {
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        data[n].let {
            if (it == null) Result.success(default)
            else converter.convert(n, it).check(n, fn)
        }
    }

    override fun describe(name: String) = NormalFieldType(clazz, key(name), converter.describe(), false, "$default")
}

class PrimitiveListProperty<T>(
    private val clazz: Class<T>,
    private val metadata: Map<String, Any>,
    private val separator: String,
    private val converter: Converter<T>,
    private val data: FormData,
    private val fn: CheckFn<List<T>>,
    private var key: KeyGetter
): CachedProperty<List<T>>(), PropertyDescribe {
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

    override fun describe(name: String) = ListFieldType(key(name), metadata, NormalFieldType(
        clazz, "", converter.describe(), true, null
    ))
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
): CachedProperty<T?>(), PropertyDescribe {
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

    override fun describe(name: String) = describe(clazz.java, key(name))
}

class RequiredBeanProperty<T: KForm> internal constructor(
    private val clazz: KClass<T>,
    private val data: FormData,
    private val fn: CheckFn<T>,
    private var key: KeyGetter
): CachedProperty<T>(), PropertyDescribe {
    override fun doGet(name: String): Result<T> = key(name).let { n ->
        (data as SupportBean).bean(n)?.flatMap { createBean(n, clazz, it) }?.check(n, fn) ?: invalid(n)
    }

    override fun describe(name: String): FieldType = describe(clazz.java, key(name), true)
}

class ListBeanProperty<T: KForm>(
    private val clazz: KClass<T>,
    private val data: FormData,
    private val metadata: Map<String, Any>,
    private val fn: CheckFn<List<T>>,
    private var key: KeyGetter
): CachedProperty<List<T>>(), PropertyDescribe {
    init {
        if (data !is SupportBeanList) throw RuntimeException("the form data $data doesn't support bean list")
    }

    fun name(name: String) = this.also { key = fixedKey(name) }
    fun snake() = this.also { key = SnakeKey }

    override fun doGet(name: String): Result<List<T>> = key(name).let { n ->
        (data as SupportBeanList).list(n).innerMap { createBean(n, clazz, it) }.check(n, fn)
    }

    override fun describe(name: String) =
        ListBeanFieldType(key(name), metadata, describe(clazz.java))
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
