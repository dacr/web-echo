package webecho.routing

import java.net.InetSocketAddress
import java.util.UUID
import sttp.tapir.*
import sttp.tapir.json.json4s.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import sttp.capabilities.pekko.PekkoStreams
import org.json4s.{Formats, Serialization}
import webecho.apimodel.*
import webecho.tools.JsonImplicits
import org.json4s.JValue

import java.nio.charset.StandardCharsets

object TapirEndpoints extends JsonImplicits {
  // JsonImplicits provides implicit formats and serialization
  // implicitly available chosenFormats and chosenSerialization will be picked up by jsonBody

  // Common parameters
  val uuidPath = path[UUID]("uuid").description("recorder identifier")
  val countQuery = query[Option[Int]]("count").description("Returns a limited number of records")
  val userAgent = header[Option[String]]("User-Agent")
  val clientIp = extractFromRequest(req => req.connectionInfo.remote.flatMap {
    case isa: InetSocketAddress => Some(isa.getAddress.getHostAddress)
    case _ => None
  })

  // /api/info
  val infoEndpoint = endpoint.get
    .in("api" / "info")
    .out(jsonBody[ApiServiceInfo])
    .errorOut(statusCode(StatusCode.PreconditionFailed).and(jsonBody[ApiErrorMessage]))
    .description("General information about the service")

  // /api/info/{uuid}
  val infoUuidEndpoint = endpoint.get
    .in("api" / "info" / uuidPath)
    .out(jsonBody[ApiRecorderInfoDetail])
    .errorOut(statusCode(StatusCode.Forbidden).and(jsonBody[ApiErrorMessage]))
    .description("get information about a defined recorder")

  // /api/recorder (and /api/webhook)
  val createRecorderEndpoint = endpoint.post
    .in("api" / "recorder")
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiRecorderCreationResult])
    .description("Create a JSON recorder")

  val createWebhookEndpoint = endpoint.post
    .in("api" / "webhook")
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiRecorderCreationResult])
    .deprecated()
    .description("Create a JSON recorder (deprecated)")

  // /api/echoed/{uuid} GET
  // Streaming response.
  val getEchoEndpoint = endpoint.get
    .in("api" / "echoed" / uuidPath)
    .in(countQuery)
    .out(
      streamBody(PekkoStreams)(Schema.binary, CodecFormat.Json())
    ) // TODO how to provide information about the fact we want NDJSON output of ApiOwner ?
    .errorOut(statusCode.and(jsonBody[ApiErrorMessage]))
    .description("Get the already sent JSON content stored by the recorder")

  // /api/echoed/{uuid} POST
  val postEchoEndpoint = endpoint.post
    .in("api" / "echoed" / uuidPath)
    .in(jsonBody[JValue]) // JValue is a domain model, not an API model, so keep JValue here
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiPostEchoResult])
    .errorOut(statusCode(StatusCode.Forbidden).and(jsonBody[ApiErrorMessage]))
    .description("Webhook API end point, can also be directly used for recorder testing purposes")

  // /api/echoed/{uuid}/websocket GET
  val webSocketListEndpoint = endpoint.get
    .in("api" / "echoed" / uuidPath / "websocket")
    .out(jsonBody[List[ApiWebSocket]])
    .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
    .description("Get all websocket attached to this recorder")

  // /api/echoed/{uuid}/websocket POST
  val webSocketRegisterEndpoint = endpoint.post
    .in("api" / "echoed" / uuidPath / "websocket")
    .in(jsonBody[ApiWebSocketInput]) // Use ApiWebSocketInput
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiWebSocket])
    .description("Register a new websocket endpoint to this recorder")

  // /api/echoed/{uuid}/websocket/{wsuuid} GET
  val webSocketGetEndpoint = endpoint.get
    .in("api" / "echoed" / uuidPath / "websocket" / path[UUID]("wsuuid"))
    .out(jsonBody[ApiWebSocket])
    .errorOut(statusCode(StatusCode.NotFound).and(stringBody))
    .description("Get websocket record information")

  // /api/echoed/{uuid}/websocket/{wsuuid} DELETE
  val webSocketDeleteEndpoint = endpoint.delete
    .in("api" / "echoed" / uuidPath / "websocket" / path[UUID]("wsuuid"))
    .out(stringBody)
    .errorOut(statusCode.and(stringBody))
    .description("Unregister a websocket from this recorder")

  // /api/echoed/{uuid}/websocket/{wsuuid}/health GET
  val webSocketHealthEndpoint = endpoint.get
     .in("api" / "echoed" / uuidPath / "websocket" / path[UUID]("wsuuid") / "health")
     .out(stringBody)
     .errorOut(statusCode.and(stringBody))

  // /health
  val healthEndpoint = endpoint.get
    .in("health")
    .out(jsonBody[ApiHealth])
    .description("Service health")
}