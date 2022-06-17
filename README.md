# KForm

```kotlin

enum class Hello { FOO, BAR }

class IdForm(data: FormData): KForm {
    val id: String by data.string().required()
    val name: String? by data.string()
    val hello: Hello by data.enum(Hello::class).required()
    val date: LocalDate by data.date().required()
    val dateTime: LocalDateTime by data.dateTime().required()
    val stringList: List<String> by data.stringList().required()
    val longList: List<Long> by data.longList().required()
}

fun main() {
    val data = FormData(mapOf(
        "id" to "id1", "name" to "name", "hello" to "0",
        "date" to "2018-10-10", "dateTime" to "2018-11-11 15:10:14",
        "stringList" to "a, b, c, d",
        "longList" to "1, 2, 3, 4"
    ))
    val form = IdForm(data)

    println(form.longList)
}

enum class E { E1, E2 }

class AS(data: FormData): KForm() {
    val s1 by data.of(int()).required()
    val s2 by data.bool().required()
}

class AA(data: FormData): KForm() {
    val a by data.string().name("aa")
    val b by data.long(min = 10, max = 200).default(11) { it % 2 == 0L }
    val demoEnum by data.enum<E>().required()
    val list by data.listOf(int()) { it.isNotEmpty() }
    val ss by data.beanOf<AS>()
    val sss by data.listOf<AS>()
}

fun main() {
    val aa = create(AA::class.java, MapFormData(mapOf(
        "aa" to "3",
        "b" to "12",
        "demoEnum" to "1",
        "list" to "1,2,3,4",
        "ss.s1" to "1",
        "ss.s2" to "0"
    )))

    println(aa)
}

```