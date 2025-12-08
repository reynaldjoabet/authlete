package example
import authlete.api.*
import authlete.models.*
import authlete.api.AuthorizationEndpoint
import io.scalaland.chimney.dsl.*
import java.util.UUID
import io.scalaland.chimney.Transformer
object Hello extends Greeting with App {
  println(greeting)

  val userID: UUID = UUID.randomUUID()
  val user: User = User(userID, "John", "Doe")

  // Use .transformInto[Type], when don't need to customize anything...:
  val apiUser = user.transformInto[ApiUser]

// ...and .into[Type].customization.transform when you do:
  val user2: User = apiUser.into[User].withFieldConst(_.id, userID).transform

  // If yout want to reuse some Transformation (and you don't want to write it by hand)
  // you can generate it with .derive:
  implicit val transformer: Transformer[User, ApiUser] =
    Transformer.derive[User, ApiUser]

  // ...or with .define.customization.buildTransformer:
  implicit val transformerWithOverrides: Transformer[ApiUser, User] =
    Transformer
      .define[ApiUser, User]
      .withFieldConst(_.id, userID)
      .buildTransformer
}

trait Greeting {
  lazy val greeting: String = "hello"
}

case class User(id: UUID, name: String, surname: String)
case class ApiUser(name: String, surname: String)
