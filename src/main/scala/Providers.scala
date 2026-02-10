object Providers extends App {

  // get all providers
  val providers = java.security.Security.getProviders()
  providers.foreach { provider =>
    println(s"Provider: ${provider.getName}, version: ${provider.getVersionStr()}")
    // get all services for each provider
    provider
      .getServices
      .forEach { service =>
        println(s"Type: ${service.getType}, Algo: ${service.getAlgorithm}")
      }
  }

  trait A

  trait B

  def f: A & B = ???

}
