import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
private data class Message(
    val topic: String,
    val content: String,
)

private val PrettyPrintJson = Json {
    prettyPrint = true
}

fun String.countDistinctCharacters() = lowercase().toList().distinct().count()

fun main() {
    println("Hello, enter your name:")
    val name = readln()
    name.replace(" ", "").let {
        println("Your name contains ${it.length} letters")
        println("Your name contains ${it.countDistinctCharacters()} unique letters")
    }
}