package com.github.jacokoo.kform

import java.lang.reflect.Modifier
import java.time.LocalDate
import java.time.LocalDateTime
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

abstract class KForm(protected val key: KeyGetter = defaultKey) {
    internal val properties = mutableListOf<CachedProperty<*>>()

    fun <T> FormData.of(converter: Converter<T>, fn: CheckFn<T> = { true }) =
        FormProperty(this@KForm, this, converter, fn, key).also { properties.add(it) }

    fun <T> FormData.listOf(converter: Converter<T>, separator: String = ",", fn: CheckFn<List<T>> = { true }) =
        PrimitiveListProperty(separator, converter, this, fn, key).also { properties.add(it) }

    inline fun <reified T: KForm> FormData.beanOf(noinline fn: CheckFn<T> = { true }) =
        createBeanOf(T::class, fn)

    inline fun <reified T: KForm> FormData.listOf(noinline fn: CheckFn<List<T>> = { true }) =
        createListOfBean(T::class, fn)

    fun <T: KForm> FormData.createBeanOf(clazz: KClass<T>, fn: CheckFn<T>) =
        BeanProperty(this@KForm, clazz, this, fn, key).also { properties.add(it) }

    fun <T: KForm> FormData.createListOfBean(clazz: KClass<T>, fn: CheckFn<List<T>>) =
        ListBeanProperty(clazz, this, fn, key).also { properties.add(it) }

    protected fun string(pattern: String? = null, maxLength: Int? = null) = StringConverter(pattern, maxLength)
    protected fun int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, ) = IntConverter(min, max)
    protected fun long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE) = LongConverter(min, max)
    protected fun float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE) = FloatConverter(min, max)
    protected fun bool() = BooleanConverter()
    protected fun date(format: String = "yyyy-MM-dd") = DateConverter(format)
    protected fun dateTime(format: String = "yyyy-MM-dd HH:mm:ss") = DateTimeConverter(format)
    protected inline fun <reified T: Enum<T>> enum() = EnumConverter(T::class.java)

    fun validate() { validate(this) }

    fun check(): Throwable? = check(this)

    fun FormData.string(pattern: String? = null, maxLength: Int? = null, fn: CheckFn<String> = { true }) =
        of(StringConverter(pattern, maxLength), fn)

    fun FormData.int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, fn: CheckFn<Int> = { true }) =
        of(IntConverter(min, max), fn)

    fun FormData.long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, fn: CheckFn<Long> = { true }) =
        of(LongConverter(min, max), fn)

    fun FormData.float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE, fn: CheckFn<Float> = { true }) =
        of(FloatConverter(min, max), fn)

    fun FormData.bool(fn: CheckFn<Boolean> = { true }) =
        of(BooleanConverter(), fn)

    fun FormData.date(format: String = "yyyy-MM-dd", fn: CheckFn<LocalDate> = { true }) =
        of(DateConverter(format), fn)

    fun FormData.dateTime(format: String = "yyyy-MM-dd HH:mm:ss", fn: CheckFn<LocalDateTime> = { true }) =
        of(DateTimeConverter(format), fn)

    inline fun <reified T: Enum<T>> FormData.enum(noinline fn: CheckFn<T> = { true }) =
        of(EnumConverter(T::class.java), fn)

    override fun toString(): String {
        val vs = properties.map { if (it.value?.isSuccess == true) it.value?.getOrThrow() else "FAIL" }
        return "${this::class.java.simpleName}(${vs.joinToString()})"
    }
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

// property delegate is lazy, so validators will not be called until the property is being used
private class KFormCreator<T: KForm>(clazz: Class<T>) {
    // a form is expected to have only one constructor
    private val c = clazz.constructors.first()
    private val fields: List<String> = getFields(clazz)

    private fun getFields(clazz: Class<*>): List<String> =
        if (KForm::class.java.isAssignableFrom(clazz.superclass)) {
            getFields(clazz.superclass) + doGetFields(clazz)
        } else {
            doGetFields(clazz)
        }

    private fun doGetFields(clazz: Class<*>): List<String> =
        clazz.declaredFields.filter {
            it.name.endsWith("\$delegate")
                    && !Modifier.isStatic(it.modifiers) && Modifier.isPrivate(it.modifiers)
        }.map { it.name.substring(0, it.name.length - 9) }

    @Suppress("unchecked_cast")
    fun create(data: FormData, eagerCheck: Boolean = true): T =
        (c.newInstance(data) as T).also { if (eagerCheck) validate(it) }

    fun validate(form: T) {
        fields.forEachIndexed { idx, it ->
            val re = form.properties[idx].get(it)
            if (re.isFailure) throw re.exceptionOrNull()!!
        }
    }

    fun check(form: T): Throwable? {
        fields.forEachIndexed { idx, it ->
            val re = form.properties[idx].get(it)
            if (re.isFailure) return re.exceptionOrNull()
        }
        return null
    }
}