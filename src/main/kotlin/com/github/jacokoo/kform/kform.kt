package com.github.jacokoo.kform

import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

class ViolationException(message: String, cause: Throwable? = null): RuntimeException(message, cause) {
    override fun fillInStackTrace(): Throwable {
        return this
    }
}

@Suppress("unchecked_cast")
fun <T: KForm> create(key: Class<T>, data: FormData, eagerCheck: Boolean = true): T {
    val kc = cache.computeIfAbsent(key) { KFormCreator(key) } as KFormCreator<T>
    return kc.create(data, eagerCheck)
}

fun <T: KForm> describe(form: Class<T>, name: String = "", required: Boolean = false): BeanFieldType =
    cache.computeIfAbsent(form) { KFormCreator(form) }.describe(name, required)

abstract class KForm(protected val key: KeyGetter = defaultKey) {
    protected open val defaultDateTimeFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    protected open val defaultDateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    internal val properties = mutableListOf<CachedProperty<*>>()

    inline fun <reified T> FormData.of(converter: Converter<T>, noinline fn: CheckFn<T> = { true }) =
        createOf(T::class.java, converter, fn)

    inline fun <reified T> FormData.listOf(converter: Converter<T>, separator: String = ",", metadata: Map<String, Any> = mapOf(), noinline fn: CheckFn<List<T>> = { true }) =
        createListOf(T::class.java, converter, separator, metadata, fn)

    inline fun <reified T: KForm> FormData.beanOf(noinline fn: CheckFn<T> = { true }) =
        createBeanOf(T::class, fn)

    inline fun <reified T: KForm> FormData.listOf(metadata: Map<String, Any> = mapOf(), noinline fn: CheckFn<List<T>> = { true }) =
        createListOfBean(T::class, metadata, fn)

    inline fun <reified T: KForm> FormData.inlineOf() = createInlineBean(T::class)

    fun <T> FormData.createOf(clazz: Class<T>, converter: Converter<T>, fn: CheckFn<T>, data: FormData = this, kg: KeyGetter = key) =
        FormProperty(this@KForm, clazz, data, converter, fn, kg).also { properties.add(it) }

    fun <T> FormData.createListOf(clazz: Class<T>, converter: Converter<T>, separator: String = ",", metadata: Map<String, Any>, fn: CheckFn<List<T>>) =
        PrimitiveListProperty(clazz, metadata, separator, converter, this, fn, key).also { properties.add(it) }

    fun <T: KForm> FormData.createBeanOf(clazz: KClass<T>, fn: CheckFn<T>) =
        BeanProperty(this@KForm, clazz, this, fn, key).also { properties.add(it) }

    fun <T: KForm> FormData.createListOfBean(clazz: KClass<T>, metadata: Map<String, Any>, fn: CheckFn<List<T>>) =
        ListBeanProperty(clazz, this, metadata, fn, key).also { properties.add(it) }

    fun <T: KForm> FormData.createInlineBean(clazz: KClass<T>) =
        InlineBeanProperty(clazz, this).also { properties.add(it) }

    protected fun string(pattern: String? = null, maxLength: Int? = null, metadata: Map<String, Any> = mapOf()) = StringConverter(pattern, maxLength, metadata)
    protected fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, metadata: Map<String, Any> = mapOf()) = IntConverter(min, max, metadata)
    protected fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, metadata: Map<String, Any> = mapOf()) = LongConverter(min, max, metadata)
    protected fun float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE, metadata: Map<String, Any> = mapOf()) = FloatConverter(min, max, metadata)
    protected fun bool(metadata: Map<String, Any> = mapOf()) = BooleanConverter(metadata)
    protected fun date(format: DateTimeFormatter = defaultDateFormat, metadata: Map<String, Any> = mapOf()) = DateConverter(format, metadata)
    protected fun dateTime(format: DateTimeFormatter = defaultDateTimeFormat, metadata: Map<String, Any> = mapOf()) = DateTimeConverter(format, metadata)
    protected inline fun <reified T: Enum<T>> enum(metadata: Map<String, Any> = mapOf()) = EnumConverter(T::class.java, metadata)

    fun validate() { validate(this) }

    fun check(): Throwable? = check(this)

    fun FormData.string(pattern: String? = null, maxLength: Int? = null, metadata: Map<String, Any> = mapOf(), fn: CheckFn<String> = { true }) =
        createOf(String::class.java, StringConverter(pattern, maxLength, metadata), fn)

    fun FormData.int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, metadata: Map<String, Any> = mapOf(), fn: CheckFn<Int> = { true }) =
        createOf(Int::class.java, IntConverter(min, max, metadata), fn)

    fun FormData.long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, metadata: Map<String, Any> = mapOf(), fn: CheckFn<Long> = { true }) =
        createOf(Long::class.java, LongConverter(min, max, metadata), fn)

    fun FormData.float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE, metadata: Map<String, Any> = mapOf(), fn: CheckFn<Float> = { true }) =
        createOf(Float::class.java, FloatConverter(min, max, metadata), fn)

    fun FormData.bool(metadata: Map<String, Any> = mapOf(), fn: CheckFn<Boolean> = { true }) =
        createOf(Boolean::class.java, BooleanConverter(metadata), fn)

    fun FormData.date(format: DateTimeFormatter = defaultDateFormat, metadata: Map<String, Any> = mapOf(), fn: CheckFn<LocalDate> = { true }) =
        createOf(LocalDate::class.java, DateConverter(format, metadata), fn)

    fun FormData.dateTime(format: DateTimeFormatter = defaultDateTimeFormat, metadata: Map<String, Any> = mapOf(), fn: CheckFn<LocalDateTime> = { true }) =
        createOf(LocalDateTime::class.java, DateTimeConverter(format, metadata), fn)

    inline fun <reified T: Enum<T>> FormData.enum(metadata: Map<String, Any> = mapOf(), noinline fn: CheckFn<T> = { true }) =
        createOf(T::class.java, EnumConverter(T::class.java, metadata), fn)

    override fun toString(): String {
        val vs = properties.map { if (it.value?.isSuccess == true) it.value?.getOrThrow() else "FAIL" }
        return "${this::class.java.simpleName}(${vs.joinToString()})"
    }

    open fun describe(): Map<String, Any> = mapOf()
    open fun isValid(): Boolean = true
}

internal fun MutableList<CachedProperty<*>>.replace(c1: CachedProperty<*>, c2: CachedProperty<*>) {
    remove(c1)
    add(c2)
}

private val cache = ConcurrentHashMap<Class<*>, KFormCreator<*>>()

@Suppress("unchecked_cast")
internal fun <T: KForm> validate(form: T) = form::class.java
    .let { cache.computeIfAbsent(it) { _ -> KFormCreator(it) } as KFormCreator<T> }
    .validate(form)

@Suppress("unchecked_cast")
internal fun <T: KForm> check(form: T): Throwable? = form::class.java
    .let { cache.computeIfAbsent(it) { _ -> KFormCreator(it) } as KFormCreator<T> }
    .check(form)

private val emptyData = object: FormData, SupportBean, SupportBeanList, SupportPrimitiveList {
    override fun get(key: String): Any? = null
    override fun bean(key: String): Result<FormData>? = null
    override fun list(key: String): Result<List<FormData>> = Result.success(listOf())
    override fun list(key: String, separator: String): Result<List<Any>> = Result.success(listOf())
}

// property delegate is lazy, so validators will not be called until the property is being used
private class KFormCreator<T: KForm>(val clazz: Class<T>) {
    // a form is expected to have only one constructor
    private val c = clazz.constructors.first()
    private val fields: List<Pair<String, Field>> = getFields(clazz)

    private fun getFields(clazz: Class<*>): List<Pair<String, Field>> =
        if (KForm::class.java.isAssignableFrom(clazz.superclass)) {
            getFields(clazz.superclass) + doGetFields(clazz)
        } else {
            doGetFields(clazz)
        }

    private fun doGetFields(clazz: Class<*>): List<Pair<String, Field>> =
        clazz.declaredFields.filter {
            it.name.endsWith("\$delegate")
                    && !Modifier.isStatic(it.modifiers) && Modifier.isPrivate(it.modifiers)
        }.map { it.name.substring(0, it.name.length - 9) to it }

    @Suppress("unchecked_cast")
    fun create(data: FormData, eagerCheck: Boolean = true): T =
        (c.newInstance(data) as T).also { if (eagerCheck) validate(it) }

    fun validate(form: T) {
        fields.forEachIndexed { idx, it ->
            val re = form.properties[idx].get(it.first)
            if (re.isFailure) throw re.exceptionOrNull()!!
        }

        if (!form.isValid()) {
            throw ViolationException("form is invalid")
        }
    }

    fun check(form: T): Throwable? {
        fields.forEachIndexed { idx, it ->
            val re = form.properties[idx].get(it.first)
            if (re.isFailure) return re.exceptionOrNull()
        }
        return if (form.isValid()) null else ViolationException("form is invalid")
    }

    fun describe(name: String, required: Boolean): BeanFieldType {
        val form = c.newInstance(emptyData) as KForm
        val types = form.properties.mapIndexed { i, it -> (it as PropertyDescribe).describe(fields[i].first) }
        val fs = fields.map { it.second }
        return BeanFieldType(name, clazz, required, form.describe(), fs, types)
    }
}