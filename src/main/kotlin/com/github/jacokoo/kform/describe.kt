package com.github.jacokoo.kform

import java.lang.reflect.Field

interface ConverterDescribe {
    val metadata: Map<String, Any>
    fun describe(): Map<String, Any> = doDescribe() + metadata
    fun doDescribe(): Map<String, Any>
}

interface PropertyDescribe {
    fun describe(name: String): FieldType
}

sealed interface FieldType {
    val name: String
    val type: String
    val metadata: Map<String, Any>
    val required: Boolean
    val defaultValue: String?
}

class NormalFieldType(
    val clazz: Class<*>,
    override val name: String,
    override val metadata: Map<String, Any>,
    override val required: Boolean,
    override val defaultValue: String?
): FieldType {
    override val type: String = clazz.simpleName
}

class ListFieldType(
    override val name: String,
    override val metadata: Map<String, Any>,
    val inner: FieldType
): FieldType {
    override val type: String = "array of ${inner.type}"
    override val defaultValue: String = "[]"
    override val required: Boolean = false
}

class BeanFieldType(
    override val name: String,
    val clazz: Class<*>,
    override val required: Boolean,
    override val metadata: Map<String, Any>,
    val fields: List<Field>,
    val types: List<FieldType>
): FieldType {
    override val type: String = "object(${clazz.simpleName})"
    override val defaultValue: String? = null
}

class ListBeanFieldType(
    override val name: String,
    override val metadata: Map<String, Any>,
    val inner: BeanFieldType
): FieldType {
    override val type: String = "array of ${inner.type}"
    override val defaultValue: String? = "[]"
    override val required: Boolean = false
}