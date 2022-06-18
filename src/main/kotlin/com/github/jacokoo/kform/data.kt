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
