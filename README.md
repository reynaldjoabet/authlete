# authlete

NICs typically have queues
- transmit queue for packets
- receive queue packets



## Circe
1. Parsing a JSON string into Circe’s AST
Circe uses `Jawn` under the hood for parsing JSON strings into `io.circe.Json`.

```scala
import io.circe.parser._

val input = """{"name":"Alice","age":30}"""
val json: Either[ParsingFailure, io.circe.Json] = parse(input) 
```
`parse` uses `Jawn` to parse the string and produce a Circe Json object.

```scala
// from package io.circe
 import io.circe.jawn.JawnParser

package object parser extends Parser {
  private[this] val parser = new JawnParser

  def parse(input: String): Either[ParsingFailure, Json] = parser.parse(input)
}

```

2. Decoding to a Scala type
Circe uses type classes to decode the Json to a Scala case class.

```scala
import io.circe.generic.auto._
import io.circe.syntax._

case class Person(name: String, age: Int)

val decoded: Either[io.circe.Error, Person] = json.flatMap(_.as[Person])

```
Uses `implicit Decoder[Person]`, which is derived automatically in this case.

3. Encoding a Scala type to JSON
```scala
val person = Person("Alice", 30)
val jsonFromPerson: io.circe.Json = person.asJson
```
Uses `implicit Encoder[Person]`, also derived.

4. Printing JSON to a string
Circe then uses a `printer` (its own default) to convert Json to a string:

```scala
import io.circe.Printer

val jsonString: String = Printer.noSpaces.print(jsonFromPerson)
```

## jsoniter-circe
- By default, `Jawn` is used for parsing and Circe’s `Printer` for rendering.

- If you replace the parser and printer with jsoniter-circe, the encoding/decoding logic stays the same, but parsing/printing becomes faster.

```sh
       Jawn          io.circe.Encoder
string->io.circe.Json-> case class
    io.circe.Decoder       Printer
case class -> io.circe.Json->string

    circe-jsoniter   io.circe.Encoder
string->JsonValueCodec[io.circe.Json]-> case class
    io.circe.Decoder     circe-jsoniter
case class -> JsonValueCodec[io.circe.Json]->string

```

```scala
/**
 * A type class that provides a way to produce a value of type `A` from a [[Json]] value.
 */
trait Decoder[A] extends Serializable {}
```

```sh

    [ Jawn Parser ]             [ io.circe.Decoder ]
string -------------> Json ------------------> Case Class

    [ io.circe.Encoder ]        [ Circe Printer ]
Case Class -------------> Json -----------------> string

    [ circe-jsoniter Parser ]   [ io.circe.Decoder ]
string ---------------------> Json ------------------> Case Class

    [ io.circe.Encoder ]        [ circe-jsoniter Printer ]
Case Class -------------> Json -----------------------> string

```

```scala
 /**
   * Creates a JSON value codec that parses and serialize to/from circe's JSON AST.
   *
   * @param maxDepth the maximum depth for decoding
   * @param initialSize the initial size hint for object and array collections
   * @param doSerialize a predicate that determines whether a value should be serialized
   * @param numberParser a function that parses JSON numbers
   * @param numberSerializer a routine that serializes JSON numbers
   * @return The JSON codec
   */
  def jsonCodec(
      maxDepth: Int = 128,
      initialSize: Int = 8,
      doSerialize: Json => Boolean = _ => true,
      numberParser: JsonReader => Json = io.circe.JsoniterScalaCodec.defaultNumberParser,
      numberSerializer: (JsonWriter, JsonNumber) => Unit = io.circe.JsoniterScalaCodec.defaultNumberSerializer): JsonValueCodec[Json] =
    new io.circe.JsoniterScalaCodec(maxDepth, initialSize, doSerialize, numberParser, numberSerializer)


```    

`JsonValueCodec[Json]`: Teaches jsoniter to (de)serialize Circe's `Json` tree
`ShortAsciiStringCodec[A]`	Wraps a `JsonValueCodec[A]` in a Circe-compatible `Codec[A]`


```scala
/**
 * A JSON value codec that parses and serialize to/from circe's JSON AST.
 *
 * @param maxDepth the maximum depth for decoding
 * @param initialSize the initial size hint for object and array collections
 * @param doSerialize a predicate that determines whether a value should be serialized
 * @param numberParser a function that parses JSON numbers
 * @param numberSerializer a function that serializes JSON numbers
 * @return The JSON codec
 */
final class JsoniterScalaCodec(
                                maxDepth: Int,
                                initialSize: Int,
                                doSerialize: Json => Boolean,
                                numberParser: JsonReader => Json,
                                numberSerializer: (JsonWriter, JsonNumber) => Unit) extends JsonValueCodec[Json] {
                                
                                }
                          
object JsoniterScalaCodec{
    
}
``` 

The authorization server only issues access tokens, it does not authenticate the user.