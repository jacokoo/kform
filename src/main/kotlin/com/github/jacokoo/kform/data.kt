package com.github.jacokoo.kform

interface FormData {
    operator fun get(key: String): Any?
}

interface SupportBean {
    fun bean(key: String): Result<FormData>?
}

interface SupportBeanList {
    fun list(key: String): Result<List<FormData>>
}

interface SupportPrimitiveList {
    fun list(key: String, separator: String): Result<List<Any>>
}

data class MapFormData(val map: Map<String, Any>): FormData, SupportBean, SupportPrimitiveList{
    override fun get(key: String): Any? = map[key]

    override fun bean(key: String): Result<FormData>? =
        map
            .filter { it.key.startsWith("$key.") }
            .mapKeys { it.key.substring(key.length + 1) }
            .takeIf { it.isNotEmpty() }
            ?.let {
                Result.success(MapFormData(it))
            }

    override fun list(key: String, separator: String): Result<List<Any>> =
        get(key)?.let { v -> when(v) {
            is Array<*> -> inner(key, v.asIterable())
            is List<*> -> inner(key, v)
            is String -> inner(key, v.split(separator).map { it.trim() })
            else -> invalid(key)
        }} ?: Result.success(listOf())

    @Suppress("unchecked_cast")
    private fun inner(name: String, items: Iterable<*>): Result<List<Any>> =
        Result.success(items.map { it ?: return invalid(name) })
}