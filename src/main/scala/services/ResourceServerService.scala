package services

import models.ResourceServer

abstract class ResourceServerService[F[*]] {
  def getResourceServerByClientId(clientId: Long): F[Option[ResourceServer]]
}
