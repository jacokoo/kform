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
```