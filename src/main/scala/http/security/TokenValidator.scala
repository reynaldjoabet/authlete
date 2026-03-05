package middleware


// use nimbusds and zio


abstract class TokenValidator {
  def validate(token: String): Boolean
}
object TokenValidator {


}
