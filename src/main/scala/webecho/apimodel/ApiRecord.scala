package webecho.apimodel

import sttp.tapir.Schema
import sttp.tapir.Schema.SName
import sttp.tapir.SchemaType

case class ApiRecord(
  data: Any,
  addedOn: String,
  addedByRemoteHostAddress: Option[String],
  addedByUserAgent: Option[String]
)

object ApiRecord {
  // We need to ensure Any has a schema, usually it's already handled in ApiEndpoints or JsonSupport,
  // but let's assume it is available in scope where ApiRecord schema is derived.
  
  // Since Any is tricky with auto derivation without implicit schema in scope, 
  // we might need to import the one from ApiEndpoints or define it here if possible,
  // or just rely on the one in ApiEndpoints if it's imported.
  // However, simpler is to define the schema manually or use derived if Any schema is present.
  
  implicit lazy val schema: Schema[ApiRecord] = {
    implicit val anySchema: Schema[Any] = Schema(SchemaType.SProduct(Nil), Some(SName("AnyJson")))
    Schema.derived[ApiRecord]
      .name(SName("ApiRecord"))
      .description("A record containing the data and its metadata")
  }
}
