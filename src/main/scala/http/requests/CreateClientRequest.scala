package http.requests

import authlete.models.ClientRegistrationApiRequest
import authlete.models.ClientRegistrationRequest
import io.scalaland.chimney.dsl.*
import io.scalaland.chimney.Transformer

final case class CreateClientRequest()

object CreateClientRequest {

//   def toClientCreateRequest: Transformer[CreateClientRequest, ClientRegistrationRequest] =
//     Transformer
  /// .define[CreateClientRequest, ClientRegistrationRequest]
}
