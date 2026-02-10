import java.io.FileInputStream
import java.security.cert.CRL
import java.security.cert.CRLException
import java.security.cert.CRLReason
import java.security.cert.CRLSelector
import java.security.cert.CertPath
import java.security.cert.CertPathBuilder
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathBuilderResult
import java.security.cert.CertPathBuilderSpi
import java.security.cert.CertPathChecker
import java.security.cert.CertPathParameters
import java.security.cert.CertPathValidator
import java.security.cert.CertPathValidatorException
import java.security.cert.CertPathValidatorResult
import java.security.cert.CertPathValidatorSpi
import java.security.cert.CertSelector
import java.security.cert.CertStore
import java.security.cert.CertStoreException
import java.security.cert.CertStoreParameters
import java.security.cert.CertStoreSpi
import java.security.cert.Certificate
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.CertificateFactorySpi
import java.security.cert.CertificateNotYetValidException
import java.security.cert.CertificateParsingException
import java.security.cert.CertificateRevokedException
import java.security.cert.CollectionCertStoreParameters
import java.security.cert.Extension
import java.security.cert.LDAPCertStoreParameters
import java.security.cert.PKIXBuilderParameters
import java.security.cert.PKIXCertPathBuilderResult
import java.security.cert.PKIXCertPathValidatorResult
import java.security.cert.PKIXParameters
import java.security.cert.PKIXReason
import java.security.cert.PKIXRevocationChecker
import java.security.cert.PolicyNode
import java.security.cert.TrustAnchor
import java.security.cert.URICertStoreParameters
import java.security.cert.X509CRL
import java.security.cert.X509CRLEntry
import java.security.cert.X509CRLSelector
import java.security.cert.X509CertSelector
import java.security.cert.X509Certificate
import java.security.cert.X509Extension
import java.security.spec.AlgorithmParameterSpec
import java.security.spec.DSAGenParameterSpec
import java.security.spec.DSAParameterSpec
import java.security.spec.DSAPrivateKeySpec
import java.security.spec.DSAPublicKeySpec
import java.security.spec.ECField
import java.security.spec.ECFieldF2m
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import java.security.spec.EdDSAParameterSpec
import java.security.spec.EdECPoint
import java.security.spec.EdECPrivateKeySpec
import java.security.spec.EdECPublicKeySpec
import java.security.spec.EllipticCurve
import java.security.spec.EncodedKeySpec
import java.security.spec.InvalidKeySpecException
import java.security.spec.KeySpec
import java.security.spec.MGF1ParameterSpec
import java.security.spec.NamedParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.PSSParameterSpec
import java.security.spec.RSAKeyGenParameterSpec
import java.security.spec.RSAMultiPrimePrivateCrtKeySpec
import java.security.spec.RSAPrivateCrtKeySpec
import java.security.spec.RSAPrivateKeySpec
import java.security.spec.RSAPublicKeySpec
import java.security.spec.X509EncodedKeySpec
import java.security.spec.XECPrivateKeySpec
import java.security.spec.XECPublicKeySpec
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.Signature

import com.sun.crypto.provider.AESCipher
import com.sun.crypto.provider.AESKeyGenerator
import com.sun.crypto.provider.AESParameters
import com.sun.crypto.provider.ARCFOURCipher
import com.sun.crypto.provider.BlowfishCipher
import com.sun.crypto.provider.BlowfishKeyGenerator
import com.sun.crypto.provider.BlowfishParameters
import com.sun.crypto.provider.ChaCha20Cipher
import com.sun.crypto.provider.ChaCha20Poly1305Parameters
import com.sun.crypto.provider.DESCipher
import com.sun.crypto.provider.DESKeyFactory
import com.sun.crypto.provider.DESKeyGenerator
import com.sun.crypto.provider.DESParameters
import com.sun.crypto.provider.DESedeCipher
import com.sun.crypto.provider.DESedeWrapCipher
import com.sun.crypto.provider.DHKEM
import com.sun.crypto.provider.DHKeyAgreement
import com.sun.crypto.provider.DHKeyFactory
import com.sun.crypto.provider.DHKeyPairGenerator
import com.sun.crypto.provider.DHParameterGenerator
import com.sun.crypto.provider.DHParameters
import com.sun.crypto.provider.GCM
import com.sun.crypto.provider.GCMParameters
import com.sun.crypto.provider.GaloisCounterMode
import com.sun.crypto.provider.HmacCore
import com.sun.crypto.provider.HmacMD5
import com.sun.crypto.provider.HmacPKCS12PBECore
import com.sun.crypto.provider.HmacSHA1KeyGenerator
import com.sun.crypto.provider.JceKeyStore
import com.sun.crypto.provider.KeyGeneratorCore
import com.sun.crypto.provider.KeyWrapCipher
import com.sun.crypto.provider.OAEPParameters
import com.sun.crypto.provider.PBEKeyFactory
import com.sun.crypto.provider.PBEParameters
import com.sun.crypto.provider.PBES2Core
import com.sun.crypto.provider.PBEWithMD5AndDESCipher
import com.sun.crypto.provider.PBEWithMD5AndTripleDESCipher
import com.sun.crypto.provider.PBKDF2Core
import com.sun.crypto.provider.PKCS12PBECipherCore
import com.sun.crypto.provider.RC2Cipher
import com.sun.crypto.provider.RC2Parameters
import com.sun.crypto.provider.RSACipher
//import com.sun.crypto.provider.SslMacCore
import com.sun.crypto.provider.SunJCE
import com.sun.crypto.provider.TlsKeyMaterialGenerator
import com.sun.crypto.provider.TlsMasterSecretGenerator
import com.sun.crypto.provider.TlsPrfGenerator
import com.sun.crypto.provider.TlsRsaPremasterSecretGenerator
import javax.crypto.spec.ChaCha20ParameterSpec
import javax.crypto.spec.DESKeySpec
import javax.crypto.spec.DESedeKeySpec
import javax.crypto.spec.DHGenParameterSpec
import javax.crypto.spec.DHParameterSpec
import javax.crypto.spec.DHPrivateKeySpec
import javax.crypto.spec.DHPublicKeySpec
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.RC2ParameterSpec
import javax.crypto.spec.RC5ParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KEM
import javax.crypto.KeyAgreement
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import jdk.security.jarsigner
import jdk.security.jarsigner.JarSigner
import sun.security.action
import sun.security.ec
import sun.security.jca
import sun.security.jgss
import sun.security.krb5
import sun.security.pkcs.ContentInfo
import sun.security.pkcs.EncryptedPrivateKeyInfo
import sun.security.pkcs.PKCS7
import sun.security.pkcs.PKCS8Key
import sun.security.pkcs.PKCS9Attribute
import sun.security.pkcs.PKCS9Attributes
import sun.security.pkcs.SignerInfo
import sun.security.pkcs10.PKCS10
import sun.security.pkcs10.PKCS10Attribute
import sun.security.pkcs10.PKCS10Attributes
import sun.security.pkcs11.wrapper.PKCS11
import sun.security.pkcs11.P11TlsMasterSecretGenerator
import sun.security.pkcs11.P11Util
import sun.security.pkcs11.Secmod
import sun.security.pkcs11.SunPKCS11
import sun.security.pkcs12
import sun.security.pkcs12.PKCS12KeyStore
import sun.security.provider
import sun.security.rsa
import sun.security.smartcardio
import sun.security.ssl
import sun.security.timestamp
import sun.security.util
import sun.security.validator
import sun.security.x509
import sun.security.x509.AlgIdDSA
import sun.security.x509.AlgorithmId
import sun.security.x509.AuthorityInfoAccessExtension
import sun.security.x509.AuthorityKeyIdentifierExtension
import sun.security.x509.BasicConstraintsExtension
import sun.security.x509.CRLDistributionPointsExtension
import sun.security.x509.CertificateAlgorithmId
import sun.security.x509.CertificateExtensions
import sun.security.x509.CertificatePoliciesExtension
import sun.security.x509.CertificateSerialNumber
import sun.security.x509.CertificateSubjectName
import sun.security.x509.CertificateValidity
import sun.security.x509.CertificateVersion
import sun.security.x509.CertificateX509Key
import sun.security.x509.DNSName
import sun.security.x509.ExtendedKeyUsageExtension
import sun.security.x509.IPAddressName
import sun.security.x509.KeyUsageExtension
import sun.security.x509.NetscapeCertTypeExtension
import sun.security.x509.PolicyConstraintsExtension
import sun.security.x509.PolicyInformation
import sun.security.x509.PrivateKeyUsageExtension
import sun.security.x509.RDN
import sun.security.x509.RFC822Name
import sun.security.x509.ReasonFlags
import sun.security.x509.SerialNumber
import sun.security.x509.SubjectAlternativeNameExtension
import sun.security.x509.SubjectInfoAccessExtension
import sun.security.x509.SubjectKeyIdentifierExtension
import sun.security.x509.URIName
import sun.security.x509.UniqueIdentity
import sun.security.x509.X400Address
import sun.security.x509.X500Name
import sun.security.x509.X509CRLEntryImpl
import sun.security.x509.X509CRLImpl
import sun.security.x509.X509CertImpl
import sun.security.x509.X509CertInfo
import sun.security.x509.X509Key

object Security {

  val md = MessageDigest.getInstance("HmacSHA256")

  def hash(input: String): String = md
    .digest(input.getBytes("UTF-8"))
    .map("%02x".format(_))
    .mkString

  val secretKey = SecretKeyFactory
    .getInstance("PBKDF2WithHmacSHA256")
    .generateSecret(new PBEKeySpec("password".toCharArray(), "salt".getBytes("UTF-8"), 65536, 256))

  val keySpec = new SecretKeySpec(secretKey.getEncoded(), "AES")

  val keySpec2 = new SecretKeySpec(secretKey.getEncoded(), "HmacSHA256")

  val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
  cipher.init(Cipher.ENCRYPT_MODE, secretKey)

  val signature = Signature.getInstance("SHA256withRSA")

  val keyPairGen = KeyPairGenerator.getInstance("RSA")

  val mac = Mac.getInstance("HmacSHA256")
  mac.init(keySpec2)

  val hmac = mac.doFinal("message".getBytes("UTF-8"))

  val hashed = hmac.map("%02x".format(_)).mkString

  val encrypted = cipher.doFinal("plaintext".getBytes("UTF-8"))

  val encryptedHex = encrypted.map("%02x".format(_)).mkString

  val decrypted = cipher.init(Cipher.DECRYPT_MODE, secretKey); cipher.doFinal(encrypted)

//val decryptedText=String(decrypted,"UTF-8")

  val ivSpec = new IvParameterSpec("1234567890123456".getBytes("UTF-8"))

  val gcmSpec = new GCMParameterSpec(128, "123456789012".getBytes("UTF-8"))

  val chaCha20Spec = new ChaCha20ParameterSpec("123456789012345678901234".getBytes("UTF-8"), 1)

  val desKeySpec = new DESKeySpec(secretKey.getEncoded().slice(0, 8))

  val desedeKeySpec = new DESedeKeySpec(secretKey.getEncoded().slice(0, 24))

  val rc2Spec = new RC2ParameterSpec(128)

  val dhGenSpec = new DHGenParameterSpec(2048, 224)

// val dhPrivateKeySpec=new DHPrivateKeySpec( BigInt(1,secretKey.getEncoded().slice(0,256)).bigInteger , dhParamSpec)

// val dhPublicKeySpec=new DHPublicKeySpec( BigInt(1,secretKey.getEncoded().slice(256,512)).bigInteger , dhParamSpec)

  val oaepSpec = new OAEPParameterSpec("SHA-256", "MGF1", null, null)

  val rc5Spec = new RC5ParameterSpec(32, 12, 16)

  val kem = KEM.getInstance("RSA-KEM")

  val keyGen = KeyGenerator.getInstance("AES")

  val keyAgreement = KeyAgreement.getInstance("DH")

  val cipherInputStream = new CipherInputStream(System.in, cipher)

  val cipherOutputStream = new CipherOutputStream(System.out, cipher)

  val signatureInit = signature.initSign(keyPairGen.generateKeyPair().getPrivate())

  signature.update("data".getBytes("UTF-8"))

  signature.sign()

//Using the Apple Provider for Keychain signatures
// This uses the "Apple" provider to perform a SHA256 with ECDSA signature
  val appleSigner = Signature.getInstance("SHA256withECDSA", "Apple")

  val cf = CertificateFactory.getInstance("X.509", "SUN")

  val cert = cf
    .generateCertificate(new FileInputStream("server.crt"))
    .asInstanceOf[java.security.cert.X509Certificate]

// Use 'SunEC' to verify the signature of an Elliptic Curve cert
  val publicKey = cert.getPublicKey
  val verifier  = Signature.getInstance("SHA256withECDSA", "SunEC")
  verifier.initVerify(publicKey)

  val v = MessageDigest.getInstance("SHA3-256")

  val skf =
    SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")

  val spec = new PBEKeySpec("password".toCharArray(), "salt".getBytes("UTF-8"), 65536, 256)
  val key  = skf.generateSecret(spec)

  Cipher.getInstance("AES/GCM/NoPadding")
  Cipher.getInstance("ChaCha20-Poly1305")

  val kf    = KeyFactory.getInstance("RSA")
  val bytes = Array[Byte]( /* byte array representing the private key in PKCS#8 format */ )
  val pk    = kf.generatePrivate(new PKCS8EncodedKeySpec(bytes))

}

object SunProviderExample extends App {

  // Create a MessageDigest instance using the Sun provider
  val digest = MessageDigest.getInstance("SHA-256", "SUN")

//KeyPairGenerator.getInstance("RSA", "SUN") should fail with eror ava.security.NoSuchAlgorithmException: no such algorithm: RSA for provider SUN
  val keyPairGenerator = KeyPairGenerator.getInstance("DSA", "SUN")

  val keyPair = keyPairGenerator.generateKeyPair()

  println(s"Digest Algorithm: ${digest.getAlgorithm}")

  val sha1 = MessageDigest.getInstance("SHA-1", "SUN")

  val certPathBuilder = CertPathBuilder.getInstance("PKIX", "SUN")

  val secureRandom = java.security.SecureRandom.getInstance("SHA1PRNG", "SUN")

  val sha512_256 = MessageDigest.getInstance("SHA-512/256", "SUN")

  val sha3_512 = MessageDigest.getInstance("SHA3-512", "SUN")

  val keyfactory = KeyFactory.getInstance("HSS/LMS", "SUN")

  val signature = Signature.getInstance("SHA3-256withDSAinP1363Format", "SUN")

  val signature2 = Signature.getInstance("SHA3-512withDSAinP1363Format", "SUN")

  val signature3 = Signature.getInstance("SHA3-256withECDSAinP1363Format", "SUN")

  val signature4 = Signature.getInstance("SHA1withDSA", "SUN")

//SHA1withDSAinP1363Format

  val signature5 = Signature.getInstance("SHA1withDSAinP1363Format", "SUN")

  val keystore = java.security.KeyStore.getInstance("JKS", "SUN")

  val keystore2 = java.security.KeyStore.getInstance("PKCS12", "SUN")

  val keystore3 = java.security.KeyStore.getInstance("PKCS12", "SunJSSE")

  val keystore4 = java.security.KeyStore.getInstance("JKS", "SunJSSE")

}

object SunRsaSignExample extends App {

  // Create a Signature instance using the SunRsaSign provider
  val signature = Signature.getInstance("SHA256withRSA", "SunRsaSign")

  val keyfactory = KeyFactory.getInstance("RSASSA-PSS", "SunRsaSign")

  val P = "SunRsaSign"

  // --- KeyFactories ---
  val kfPss = KeyFactory.getInstance("RSASSA-PSS", P)
  val kfRsa = KeyFactory.getInstance("RSA", P)

  // --- KeyPairGenerators ---
  val kpgRsa = KeyPairGenerator.getInstance("RSA", P)
  val kpgPss = KeyPairGenerator.getInstance("RSASSA-PSS", P)

  // --- AlgorithmParameters ---
  val apPss = AlgorithmParameters.getInstance("RSASSA-PSS", P)

  // --- Signatures (Legacy & Standard) ---
  val sigMd2    = Signature.getInstance("MD2withRSA", P)
  val sigMd5    = Signature.getInstance("MD5withRSA", P)
  val sigSha1   = Signature.getInstance("SHA1withRSA", P)
  val sigSha224 = Signature.getInstance("SHA224withRSA", P)
  val sigSha256 = Signature.getInstance("SHA256withRSA", P)
  val sigSha384 = Signature.getInstance("SHA384withRSA", P)
  val sigSha512 = Signature.getInstance("SHA512withRSA", P)

  // --- Signatures (Modern SHA-3) ---
  val sigSha3_224 = Signature.getInstance("SHA3-224withRSA", P)
  val sigSha3_256 = Signature.getInstance("SHA3-256withRSA", P)
  val sigSha3_384 = Signature.getInstance("SHA3-384withRSA", P)
  val sigSha3_512 = Signature.getInstance("SHA3-512withRSA", P)

  // --- Signatures (Special Bit-Lengths & PSS) ---
  val sigPss        = Signature.getInstance("RSASSA-PSS", P)
  val sigSha512_224 = Signature.getInstance("SHA512/224withRSA", P)
  val sigSha512_256 = Signature.getInstance("SHA512/256withRSA", P)

}

object SunECExample extends App {

  val P = "SunEC"

  // --- 1. Traditional Elliptic Curve (ECDSA/ECDH) ---
  val kpgEc  = KeyPairGenerator.getInstance("EC", P)
  val kfEc   = KeyFactory.getInstance("EC", P)
  val apEc   = AlgorithmParameters.getInstance("EC", P)
  val kaEcdh = KeyAgreement.getInstance("ECDH", P)

  // --- 2. Modern "Edwards" Curves (EdDSA - For Fast Signing) ---
  // Ed25519 is the industry standard for fast, secure signatures
  val kpgEd25519 = KeyPairGenerator.getInstance("Ed25519", P)
  val sigEd25519 = Signature.getInstance("Ed25519", P)

  val kpgEd448 = KeyPairGenerator.getInstance("Ed448", P)
  val sigEd448 = Signature.getInstance("Ed448", P)

  // --- 3. Montgomery Curves (XDH - For Key Exchange/Agreement) ---
  // X25519 is used in almost all modern TLS 1.3 handshakes
  val kpgX25519 = KeyPairGenerator.getInstance("X25519", P)
  val kaX25519  = KeyAgreement.getInstance("X25519", P)

  val kpgX448 = KeyPairGenerator.getInstance("X448", P)
  val kaX448  = KeyAgreement.getInstance("X448", P)

  // --- 4. ECDSA Signatures (NIST Standard) ---
  val sigSha256 = Signature.getInstance("SHA256withECDSA", P)
  val sigSha384 = Signature.getInstance("SHA384withECDSA", P)
  val sigSha512 = Signature.getInstance("SHA512withECDSA", P)

  // --- 5. ECDSA Signatures (SHA-3 / Keccak) ---
  val sigSha3_256 = Signature.getInstance("SHA3-256withECDSA", P)
  val sigSha3_512 = Signature.getInstance("SHA3-512withECDSA", P)

  // --- 6. P1363 Format Signatures ---
  // Standard ECDSA (IEEE P1363) ensures a fixed-length signature (R|S)
  // rather than the variable-length ASN.1 DER format.
  val sigP1363 = Signature.getInstance("SHA256withECDSAinP1363Format", P)

}

object SUNJSSEExample extends App {

  val P = "SunJSSE"

  // --- 1. SSLContext: The Heart of TLS ---
  // TLSv1.3 is the modern gold standard
  val ctxTls13   = SSLContext.getInstance("TLSv1.3", P)
  val ctxDefault = SSLContext.getInstance("Default", P)

  // DTLS is "Datagram TLS" used for securing UDP traffic (like WebRTC/VoIP)
  val ctxDtls = SSLContext.getInstance("DTLS", P)

  // --- 2. KeyManagerFactory: Handling YOUR Identity ---
  // This manages the private keys and certificates you present to others.
  // "NewSunX509" is the standard modern implementation.
  val kmf       = KeyManagerFactory.getInstance("NewSunX509", P)
  val kmfLegacy = KeyManagerFactory.getInstance("SunX509", P)

  // --- 3. TrustManagerFactory: Handling OTHERS' Identity ---
  // This decides if you should trust the certificate the other side sent.
  // "PKIX" is the standard for checking certificate chains (RFC 5280).
  val tmf = TrustManagerFactory.getInstance("PKIX", P)

  // --- 4. KeyStore: Storing the certs ---
  // PKCS12 is the industry-standard file format (.p12 / .pfx)
  val ks = KeyStore.getInstance("PKCS12", P)

  // --- 5. Specialty Signature ---
  // Used internally for legacy TLS 1.0/1.1 handshakes
  val sigLegacy = Signature.getInstance("MD5andSHA1withRSA", P)

}

object SUNJCESymmetricCiphers extends App {

  import javax.crypto.Cipher

  val P = "SunJCE"

// Modern Standard: AES in GCM mode (Authenticated Encryption)
  val cipherAesGcm = Cipher.getInstance("AES/GCM/NoPadding", P)

// Modern Stream Cipher: ChaCha20-Poly1305 (Fast on mobile/ARM)
  val cipherChaCha = Cipher.getInstance("ChaCha20-Poly1305", P)

// Password-Based Encryption (PBE)
  val cipherPbe = Cipher.getInstance("PBEWithHmacSHA256AndAES_256", P)

// Legacy Ciphers (For compatibility only)
  val cipherDes  = Cipher.getInstance("DES", P)
  val cipher3Des = Cipher.getInstance("DESede", P)
  val cipherArc4 = Cipher.getInstance("ARCFOUR", P)

}

object SUNJCEMessageAuthenticationCodes extends App {

  import javax.crypto.Mac
  val P = "SunJCE"
  // Standard HMACs
  val macSha256 = Mac.getInstance("HmacSHA256", P)
  val macSha512 = Mac.getInstance("HmacSHA512", P)

  // Next-Gen SHA-3 HMACs
  val macSha3_256 = Mac.getInstance("HmacSHA3-256", P)
  val macSha3_512 = Mac.getInstance("HmacSHA3-512", P)

}

//Use KeyGenerator for random keys and SecretKeyFactory to turn passwords into keys (PBKDF2).
object SUNJCEKeyGeneratorsSecretKeyFactories extends App {

  import javax.crypto.{KeyGenerator, SecretKeyFactory}

  val P = "SunJCE"
// Generate a random 256-bit AES key
  val kgAes = KeyGenerator.getInstance("AES", P)
  kgAes.init(256)

// PBKDF2: Turning a user password into a secure cryptographic key
  val skfPbkdf2 = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256", P)

// Legacy Triple DES Factory
  val skfDesede = SecretKeyFactory.getInstance("DESede", P)

  val chacha20 = KeyGenerator.getInstance("ChaCha20", P)

}

object KeyAgreementDiffieHellman extends App {

  import java.security.{KeyFactory, KeyPairGenerator}
  val P     = "SunJCE"
  val kpgDh = KeyPairGenerator.getInstance("DiffieHellman", P)
  val kaDh  = KeyAgreement.getInstance("DiffieHellman", P)
  val kfDh  = KeyFactory.getInstance("DiffieHellman", P)

}
