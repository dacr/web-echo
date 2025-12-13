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

object ApiEndpoints extends JsonImplicits {
  // JsonImplicits provides implicit formats and serialization
  // implicitly available chosenFormats and chosenSerialization will be picked up by jsonBody

  // Common parameters
  val recorderId  = path[UUID]("recorderId").description("recorder identifier")
  val websocketId = path[UUID]("websocketId").description("websocket identifier")
  val countQuery  = query[Option[Int]]("count").description("Returns a limited number of records")
  val userAgent   = header[Option[String]]("User-Agent").schema(_.hidden(true))
  val clientIp    = extractFromRequest(req => req.connectionInfo.remote.map(_.getAddress.getHostAddress)).schema(_.hidden(true))

  val serviceEndpoint  = endpoint
  val recorderEndpoint = serviceEndpoint.in("recorder").tag("recordings")
  val systemEndpoint   = serviceEndpoint.in("system").tag("system")

  val recorderCreateEndpoint = recorderEndpoint
    .summary("Create a recorder")
    .post
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiRecorder])

  val recorderGetEndpoint = recorderEndpoint
    .summary("Get a recorder")
    .in(recorderId)
    .get
    .out(jsonBody[ApiRecorder])
    .errorOut(statusCode(StatusCode.Forbidden).and(jsonBody[ApiErrorMessage]))

  val recorderGetRecordsEndpoint = recorderEndpoint
    .summary("Get the data stored by the recorder")
    .in(recorderId / "records")
    .get
    .in(countQuery)
    .out(
      streamBody(PekkoStreams)(Schema.binary, CodecFormat.Json())
    ) // TODO how to provide information about the fact we want NDJSON output of ApiOwner ?
    .errorOut(statusCode.and(jsonBody[ApiErrorMessage]))

  val recorderReceiveDataEndpoint = recorderEndpoint
    .summary("Send data to the recorder")
    .description("A recorder always provide this webhook URL which can be used to send data to it.")
    .in(recorderId)
    .put
    .in(jsonBody[JValue]) // JValue is a domain model, not an API model, so keep JValue here
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiPostEchoResult])
    .errorOut(statusCode(StatusCode.Forbidden).and(jsonBody[ApiErrorMessage]))

  val recorderListAttachedWebsocketsEndpoint = recorderEndpoint
    .summary("List websocket attached to this recorder")
    .in(recorderId / "websocket")
    .get
    .out(jsonBody[List[ApiWebSocket]])
    .errorOut(statusCode(StatusCode.NotFound).and(stringBody))

  val recorderRegisterWebsocketEndpoint = recorderEndpoint
    .summary("Register a new websocket endpoint to this recorder")
    .in(recorderId / "websocket")
    .post
    .in(jsonBody[ApiWebSocketInput]) // Use ApiWebSocketInput
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiWebSocket])

  val recorderGetWebsocketInfoEndpoint = recorderEndpoint
    .summary("Get websocket record information")
    .in(recorderId / "websocket" / websocketId)
    .get
    .out(jsonBody[ApiWebSocket])
    .errorOut(statusCode(StatusCode.NotFound).and(stringBody))

  val recorderUnregisterWebsocketEndpoint = recorderEndpoint
    .summary("Unregister a websocket from this recorder")
    .in(recorderId / "websocket" / websocketId)
    .delete
    .out(stringBody)
    .errorOut(statusCode.and(stringBody))

  val recorderCheckWebsocketStateEndpoint = recorderEndpoint
    .summary("Check websocket health state")
    .in(recorderId / "websocket" / websocketId / "health")
    .get
    .out(stringBody)
    .errorOut(statusCode.and(stringBody))

  val systemHealthEndpoint = systemEndpoint
    .summary("Service health")
    .in("health")
    .get
    .out(jsonBody[ApiHealth])

  val systemServiceInfoEndpoint = systemEndpoint
    .summary("Information about the service")
    .in("info")
    .get
    .out(jsonBody[ApiServiceInfo])
    .errorOut(statusCode(StatusCode.PreconditionFailed).and(jsonBody[ApiErrorMessage]))

}