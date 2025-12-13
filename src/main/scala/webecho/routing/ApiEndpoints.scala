package webecho.routing

import java.net.InetSocketAddress
import java.util.UUID
import sttp.tapir.*
import sttp.tapir.json.jsoniter.*
import sttp.tapir.generic.auto.*
import sttp.model.StatusCode
import sttp.capabilities.pekko.PekkoStreams
import webecho.apimodel.*
import webecho.tools.JsonSupport

import java.nio.charset.StandardCharsets

object ApiEndpoints extends JsonSupport {
  // JsonSupport provides implicit codecs
  // implicitly available codecs will be picked up by jsonBody

  implicit val anySchema: Schema[Any] = Schema(SchemaType.SProduct(Nil))

  // Common parameters
  val recorderId  = path[UUID]("recorderId").description("recorder identifier")
  val websocketId = path[UUID]("websocketId").description("websocket identifier")
  val limitQuery  = query[Option[Int]]("limit").description("Returns this limited number of records")
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
    .in(limitQuery)
    .out(
      streamBody(PekkoStreams)(Schema.binary, CodecFormat.Json())
    ) // TODO how to provide information about the fact we want NDJSON output of ApiOwner ?
    .errorOut(statusCode.and(jsonBody[ApiErrorMessage]))

  val recorderReceiveDataEndpoint = recorderEndpoint
    .summary("Send data to the recorder")
    .description("A recorder always provide this webhook URL which can be used to send data to it.")
    .in(recorderId)
    .put
    .in(jsonBody[Any]) // Use Any for arbitrary JSON
    .in(userAgent)
    .in(clientIp)
    .out(jsonBody[ApiReceiptProof])
    .errorOut(statusCode.and(jsonBody[ApiErrorMessage]))

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
    .in(jsonBody[ApiWebSocketSpec]) // Use ApiWebSocketInput
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