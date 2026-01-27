package services

import authlete.models.{Hsk, JweAlg, JwsAlg}

/**
  * Hardware Security Module.
  *
  * <p> This is the interface that needs to be implemented for an HSM. Authlete loads
  * implementations of this interface dynamically. The way Authlete finds implementations is
  * disclosed only to customers who use on-premises Authlete as necessary. </p>
  *
  * @since 2.97
  */
abstract class HsmService[F[_]] {

  def createKey(hsk: Hsk): F[Either[Throwable, Map[String, Object]]]

  def deleteKey(hsk: Hsk, info: Map[String, Object]): F[Either[Throwable, Unit]]

  def getPublicKey(hsk: Hsk, info: Map[String, Object]): F[Either[Throwable, String]]

  def sign(
      hsk: Hsk,
      info: Map[String, Object],
      data: Array[Byte]
  ): F[Either[Throwable, Array[Byte]]]

  def decrypt(
      hsk: Hsk,
      info: Map[String, Object],
      data: Array[Byte]
  ): F[Either[Throwable, Array[Byte]]]

  def supportsJwsAlg(alg: JwsAlg): F[Either[Throwable, Boolean]]

  def supportsJweAlg(alg: JweAlg): F[Either[Throwable, Boolean]]

}
