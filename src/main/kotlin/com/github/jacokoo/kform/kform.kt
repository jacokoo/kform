package com.github.jacokoo.kform

import java.lang.reflect.Modifier
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

abstract class KForm(protected val key: KeyGetter = defaultKey) {
    internal val properties = mutableListOf<CachedProperty<*>>()

    fun FormData.string(pattern: String? = null, maxLength: Int? = null, fn: CheckFn<String> = { true }) =
        create(this, StringConverter(pattern, maxLength), fn)

    fun FormData.int(min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, fn: CheckFn<Int> = { true }) =
        create(this, IntConverter(min, max), fn)

    fun FormData.long(min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, fn: CheckFn<Long> = { true }) =
        create(this, LongConverter(min, max), fn)

    fun FormData.float(min: Float = Float.MIN_VALUE, max: Float = Float.MAX_VALUE, fn: CheckFn<Float> = { true }) =
        create(this, FloatConverter(min, max), fn)

    fun FormData.bool(fn: CheckFn<Boolean> = { true }) =
        create(this, BooleanConverter(), fn)

    fun FormData.date(format: String = "yyyy-MM-dd", fn: CheckFn<LocalDate> = { true }) =
        create(this, DateConverter(format), fn)

    fun FormData.dateTime(format: String = "yyyy-MM-dd HH:mm:ss", fn: CheckFn<LocalDateTime> = { true }) =
        create(this, DateTimeConverter(format), fn)

    fun <T: Enum<T>> FormData.enum(clazz: KClass<T>, fn: CheckFn<T> = { true }) =
        create(this, EnumConverter(clazz.java), fn)

    fun FormData.stringList(separator: String = ",", pattern: String? = null, maxLength: Int? = null, fn: CheckFn<List<String>> = { true }) =
        create(this, ListConverter(separator, StringConverter(pattern, null), maxLength), fn)

    fun FormData.intList(separator: String = ",", maxLength: Int? = null, min: Int = Int.MIN_VALUE, max: Int = Int.MAX_VALUE, fn: CheckFn<List<Int>> = { true }) =
        create(this, ListConverter(separator, IntConverter(min, max), maxLength), fn)

    fun FormData.longList(separator: String = ",", maxLength: Int? = null, min: Long = Long.MIN_VALUE, max: Long = Long.MAX_VALUE, fn: CheckFn<List<Long>> = { true }) =
        create(this, ListConverter(separator, LongConverter(min, max), maxLength), fn)

    protected fun <T> create(data: FormData, converter: Converter<T>, fn: CheckFn<T>) =
        FormProperty(this, data, converter, fn, key).also { properties.add(it) }

    internal fun replace(c1: CachedProperty<*>, c2: CachedProperty<*>) {
        properties.apply {
            remove(c1)
            add(c2)
        }
    }
}

// property delegate is lazy, so validators will not be called until the property is being used
class KFormCreator<T: KForm>(clazz: Class<out T>) {
    // a form is expected to have only one constructor
    private val c = clazz.constructors.first()
    private val fields = clazz.declaredFields.filter {
        it.name.endsWith("\$delegate")
                && !Modifier.isStatic(it.modifiers) && Modifier.isPrivate(it.modifiers)
    }.map { it.name.substring(0, it.name.length - 9) }

    @Suppress("unchecked_cast")
    fun create(data: FormData, eagerCheck: Boolean = true): T =
        (c.newInstance(data) as T).also { if (eagerCheck) check(it) }

    fun check(form: T) {
        fields.forEachIndexed { idx, it ->
            val re = form.properties[idx].validate(it)
            if (re.isFailure) throw re.exceptionOrNull()!!
        }
    }
}