package example
import authlete.api.* 
import authlete.models.*
import authlete.api.AuthorizationEndpoint

object Hello extends Greeting with App {
  println(greeting)
}

trait Greeting {
  lazy val greeting: String = "hello"
}
