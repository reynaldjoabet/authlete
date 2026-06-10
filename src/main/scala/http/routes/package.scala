package http

import org.http4s.*
given CanEqual[Method, Method]     = CanEqual.derived
given CanEqual[Uri.Path, Uri.Path] = CanEqual.derived
