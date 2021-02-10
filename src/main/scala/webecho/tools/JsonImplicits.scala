package webecho.tools

import org.json4s.{DefaultFormats, Formats}
import org.json4s.ext.{JavaTimeSerializers, JavaTypesSerializers}
import org.json4s.jackson.Serialization

trait JsonImplicits {
  implicit val chosenSerialization: Serialization.type = Serialization
  implicit val chosenFormats: Formats = DefaultFormats.lossless ++ JavaTimeSerializers.all ++ JavaTypesSerializers.all

}
