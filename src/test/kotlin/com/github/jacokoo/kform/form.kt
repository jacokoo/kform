package com.github.jacokoo.kform

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

class KeyTest: DescribeSpec({

    describe("build in keys") {
        it("should return the property name itself when using defaultKey") {
            defaultKey("name") shouldBe "name"
        }

        it("should change the property name to snake case if using snakeKey") {
            SnakeKey("someThingElse") shouldBe "some_thing_else"
            SnakeKey("abc") shouldBe "abc"
        }

        it("should return the specified key when using fixedKey") {
            val k = fixedKey("a")
            k("abc") shouldBe "a"
        }
    }

})

class PrimitiveForm(data: FormData): KForm() {
    val stringField by data.string().name("string")
    val intField by data.int().default(10) { it % 2 == 1}
    val longField by data.long().snake().required()
    val floatField by data.float().required()
    val booleanField by data.bool().default(false)
    val dateField by data.date()
    val dateTimeField by data.dateTime()
    val enumField by data.enum<DemoEnum>()
}

class PrimitiveListForm(data: FormData): KForm() {
    val stringList by data.listOf(string()).name("string")
    val intList by data.listOf(int())
    val longList by data.listOf(long()).snake()
    val floatList by data.listOf(float())
    val boolList by data.listOf(bool())
    val dateList by data.listOf(date())
    val dateTimeList by data.listOf(dateTime())
    val enumList by data.listOf(enum<DemoEnum>())
}

class InnerForm(data: FormData): KForm() {
    val id by data.of(int()).required()
    val name by data.of(string()).required()
}

class OuterForm(data: FormData): KForm() {
    val inner1 by data.beanOf<InnerForm>()
    val inner2 by data.beanOf<InnerForm>().required()
    val inner3 by data.beanOf<InnerForm>().name("name")
    val inner4 by data.beanOf<InnerForm>() { it.id > 10 }
    val innerForm by data.beanOf<InnerForm>().snake()
}

class ListBeanForm(data: FormData): KForm() {
    val list1 by data.listOf<InnerForm>()
    val list2 by data.listOf<InnerForm>().name("list")
    val beanList by data.listOf<InnerForm>().snake()
}

class PropertyTest: DescribeSpec({
    describe("primitive fields") {
        it("should complain if any field is invalid") {
            val form = PrimitiveForm(SimpleFormData(mapOf()))

            assertThrows<ViolationException> {
                form.validate()
            }

            form.check() shouldNotBe null
        }

        it("should pass the check fn") {
            val form = PrimitiveForm(SimpleFormData(mapOf(
                "intField" to 8
            )))

            assertThrows<ViolationException> {
                form.intField
            }
        }

        it("should not apply check fn on default value") {
            val form = PrimitiveForm(SimpleFormData(mapOf()))
            assertDoesNotThrow {
                form.intField
            }
        }

        it("should have correct value") {
            val form = PrimitiveForm(SimpleFormData(mapOf(
                "string" to "a",
                "long_field" to "10",
                "floatField" to "1.22",
                "booleanField" to "0",
                "dateField" to "2000-01-01",
                "enumField" to "1"
            )))

            assertDoesNotThrow {
                form.validate()
            }

            form.check() shouldBe null

            form.intField shouldBe 10
            form.stringField shouldBe "a"
            form.longField shouldBe 10L
            form.floatField shouldBe 1.22F
            form.booleanField shouldBe false
            form.dateField shouldBe LocalDate.of(2000, 1, 1)
            form.dateTimeField shouldBe null
            form.enumField shouldBe DemoEnum.B
        }

        it("should complain immediately if the data is invalid when using create function") {
            assertThrows<ViolationException> {
                create(PrimitiveForm::class.java, SimpleFormData(mapOf()))
            }

            assertDoesNotThrow {
                create(PrimitiveForm::class.java, SimpleFormData(mapOf("string" to "a")), eagerCheck = false)
            }
        }

        it("is lazy init") {
            val form = PrimitiveForm(SimpleFormData(mapOf(
                "string" to "a",
                "long_field" to "10",
                "floatField" to "1.22",
                "booleanField" to "0",
                "dateField" to "2000-01-01",
                "enumField" to "1"
            )))

            form.toString() shouldBe "PrimitiveForm(FAIL, FAIL, FAIL, FAIL, FAIL, FAIL, FAIL, FAIL)"
            form.stringField shouldBe "a"
            form.toString() shouldBe "PrimitiveForm(a, FAIL, FAIL, FAIL, FAIL, FAIL, FAIL, FAIL)"
        }
    }

    describe("primitive list properties") {
        it("should complain if the data does not implement SupportPrimitiveList") {
            assertThrows<RuntimeException> {
                PrimitiveListForm(SimpleFormData(mapOf()))
            }
        }

        it("should have an empty list as default value") {
            val form = PrimitiveListForm(PrimitiveListFormData(mapOf()))

            assertDoesNotThrow {
                form.validate()
            }

            form.check() shouldBe null
        }

        it("should have correct value") {
            val form = PrimitiveListForm(PrimitiveListFormData(mapOf(
                "string" to "a,b,c",
                "intList" to listOf(1, "2", 3),
                "long_list" to listOf(1, "2", 3L)
            )))

            form.stringList shouldBe listOf("a", "b", "c")
            form.intList shouldBe listOf(1, 2, 3)
            form.longList shouldBe listOf(1L, 2L, 3L)
        }

        it("should complain if any primitive value parse failed") {
            val form = PrimitiveListForm(PrimitiveListFormData(mapOf(
                "intList" to listOf("a")
            )))

            assertThrows<ViolationException> {
                form.validate()
            }
        }
    }

    describe("bean form") {
        it("should complain if the data does not implement SupportBean") {
            assertThrows<RuntimeException> {
                OuterForm(SimpleFormData(mapOf()))
            }
        }

        it("should have correct value") {
            val form = OuterForm(BeanFormData(mapOf(
                "inner2" to mapOf("id" to 1, "name" to "i1"),
                "name" to mapOf("id" to 2, "name" to "i2"),
                "inner4" to mapOf("id" to 11, "name" to "i3"),
                "inner_form" to mapOf("id" to 3, "name" to "i4")
            )))

            form.inner2.id shouldBe 1
            form.inner2.name shouldBe "i1"

            form.inner3 shouldNotBe null
            form.inner3!!.id shouldBe 2
            form.inner3!!.name shouldBe "i2"

            form.inner4 shouldNotBe null
            form.inner4!!.id shouldBe 11
            form.inner4!!.name shouldBe "i3"

            form.innerForm shouldNotBe null
            form.innerForm!!.id shouldBe 3
            form.innerForm!!.name shouldBe "i4"
        }
    }

    describe("list bean properties") {
        it("should complain if the data does not implement SupportBeanList") {
            assertThrows<RuntimeException> {
                ListBeanForm(SimpleFormData(mapOf()))
            }
        }

        it("should have correct value") {
            val form = ListBeanForm(ListBeanFormData(mapOf(
                "list" to listOf(mapOf("id" to "1", "name" to "n1"), mapOf("id" to 2, "name" to "n2")),
                "bean_list" to listOf(mapOf("id" to "3", "name" to "n3")),
            )))

            form.list1.isEmpty() shouldBe true
            form.list2.size shouldBe 2
            form.list2[1].id shouldBe 2
            form.list2[1].name shouldBe "n2"
            form.beanList.size shouldBe 1
            form.beanList[0].name shouldBe "n3"
        }
    }
})

open class SimpleFormData(private val map: Map<String, Any>): FormData {
    override fun get(key: String): Any? = map[key]
}

class PrimitiveListFormData(map: Map<String, Any>): SimpleFormData(map), SupportPrimitiveList {
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

class BeanFormData(map: Map<String, Any>): SimpleFormData(map), SupportBean {
    @Suppress("unchecked_cast")
    override fun bean(key: String): Result<FormData>? {
        val v = get(key) ?: return null
        return Result.success(SimpleFormData(v as Map<String, Any>))
    }
}

class ListBeanFormData(map: Map<String, Any>): SimpleFormData(map), SupportBeanList {
    @Suppress("unchecked_cast")
    override fun list(key: String): Result<List<FormData>> {
        val v = get(key) ?: return Result.success(listOf())
        return Result.success((v as List<Map<String, Any>>).map { SimpleFormData(it) })
    }
}