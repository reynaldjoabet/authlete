package http.responses

import com.github.plokhotnyuk.jsoniter_scala.core.JsonValueCodec
import com.github.plokhotnyuk.jsoniter_scala.macros.ConfiguredJsonValueCodec

final case class ErrorInfo(
    title: String,
    statusCode: Int,
    statusReasonPhrase: String,
    internalErrorCode: String,
    errorCode: String,
    errorDescription: String
) derives ConfiguredJsonValueCodec

final case class ErrorCodes(
    release: String,
    build: String,
    errors: List[ErrorInfo]
) derives ConfiguredJsonValueCodec
