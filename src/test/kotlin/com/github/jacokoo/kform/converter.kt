package com.github.jacokoo.kform

import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class ConverterTest: DescribeSpec({

    describe("string converter") {
        it("should not exceed the max length") {
            val con = StringConverter(pattern = null, maxLength = 10, mapOf())

            con.convert("", "aaabbbdddee").isFailure shouldBe true
            con.convert("", "abc").getOrThrow() shouldBe "abc"
        }

        it("should match the pattern") {
            val con = StringConverter(pattern = "^\\d{2}$", maxLength = null, mapOf())
            con.convert("", "abc").isFailure shouldBe true
            con.convert("", "12").getOrThrow() shouldBe "12"
        }
    }

    describe("int converter") {
        it("should complain when string can't parse to int") {
            val con = IntConverter(0, 10, mapOf())
            con.convert("", "ab").isFailure shouldBe true
        }

        it("should match the value range") {
            val con = IntConverter(0, 10, mapOf())
            con.convert("", 11).isFailure shouldBe true
            con.convert("", 10).getOrThrow() shouldBe 10
        }

        it("should complain when input is not int or string") {
            val con = IntConverter(0, 10, mapOf())
            con.convert("", 10L).isFailure shouldBe true
        }
    }

    describe("long converter") {
        it("should complain when string can't parse to long") {
            val con = LongConverter(0, 10, mapOf())
            con.convert("", "ab").isFailure shouldBe true
        }

        it("should match the value range") {
            val con = LongConverter(0, 10, mapOf())
            con.convert("", -1L).isFailure shouldBe true
            con.convert("", 0).getOrThrow() shouldBe 0L
        }

        it("should complain when input type is not in int, long or string") {
            val con = LongConverter(0, 10, mapOf())
            con.convert("", true).isFailure shouldBe true
        }
    }

    describe("float converter") {
        it("should complain when string can't parse to float") {
            val con = FloatConverter(0F, 10F, mapOf())
            con.convert("", "ab").isFailure shouldBe true
        }

        it("should match the value range") {
            val con = FloatConverter(0F, 10F, mapOf())
            con.convert("", -1F).isFailure shouldBe true
            con.convert("", 0F).getOrThrow() shouldBe 0F
        }

        it("should complain when input type is not in int, long or string") {
            val con = FloatConverter(0F, 10F, mapOf())
            con.convert("", true).isFailure shouldBe true
        }
    }


    describe("boolean converter") {
        it("should keep the origin value if the input is boolean") {
            val con = BooleanConverter(mapOf())
            con.convert("", true).getOrThrow() shouldBe true
            con.convert("", false).getOrThrow() shouldBe false
        }

        it("""should convert all values to true except "false" and "0" 0 """) {
            val con = BooleanConverter(mapOf())
            con.convert("", "false").getOrThrow() shouldBe false
            con.convert("", "0").getOrThrow() shouldBe false
            con.convert("", 0).getOrThrow() shouldBe false
            con.convert("", 1).getOrThrow() shouldBe true
            con.convert("", "a").getOrThrow() shouldBe true
        }
    }

    describe("date converter") {
        it("should keep the origin value if the input value is LocalDate") {
            val con = DateConverter(DateTimeFormatter.ISO_DATE, mapOf())
            val date = LocalDate.now()
            con.convert("", date).getOrThrow() shouldBe date
        }

        it("should use `format` to parse the input") {
            val con = DateConverter(DateTimeFormatter.ofPattern("yyyy/MM-dd"), mapOf())
            val date = LocalDate.of(2022, 6, 18)
            con.convert("", "2022/06-18").getOrThrow() shouldBe date
            con.convert("", "2022-06-18").isFailure shouldBe true
        }

        it("should complain for other inputs") {
            val con = DateConverter(DateTimeFormatter.ISO_DATE, mapOf())
            con.convert("", 0).isFailure shouldBe true
        }
    }


    describe("date time converter") {
        it("should keep the origin value if the input value is LocalDateTime") {
            val con = DateTimeConverter(DateTimeFormatter.ISO_DATE, mapOf())
            val date = LocalDateTime.now()
            con.convert("", date).getOrThrow() shouldBe date
        }

        it("should use `format` to parse the input") {
            val con = DateTimeConverter(DateTimeFormatter.ofPattern("yyyy/MM-dd HH:mm/ss"), mapOf())
            val date = LocalDateTime.of(2022, 6, 18, 16, 27, 0)
            con.convert("", "2022/06-18 16:27/00").getOrThrow() shouldBe date
            con.convert("", "2022-06-18 16:27:00").isFailure shouldBe true
        }

        it("should complain for other inputs") {
            val con = DateTimeConverter(DateTimeFormatter.ISO_DATE, mapOf())
            con.convert("", 0).isFailure shouldBe true
        }
    }

    describe("enum converter") {
        it("should convert follow the index") {
            val con = EnumConverter(DemoEnum::class.java, mapOf())
            con.convert("", 0).getOrThrow() shouldBe DemoEnum.A
            con.convert("", "1").getOrThrow() shouldBe DemoEnum.B
            con.convert("", "3").isFailure shouldBe true
            con.convert("", -1).isFailure shouldBe true
        }
    }
})

enum class DemoEnum { A, B, C }