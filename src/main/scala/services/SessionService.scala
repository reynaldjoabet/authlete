package services

import scala.concurrent.duration._

abstract class SessionService[F[*]] {

  val num = 9.millisecond

}
