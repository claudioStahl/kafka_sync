package sandbox_akka

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

final case class MessageMetadata(host: String, poolIndex: Int)
final case class ValidationInput(id: String, amount: Int)
final case class ValidationInputWithMetadata(id: String, amount: Int, metadata: MessageMetadata)
final case class ValidationResponse(id: String, is_fraud: Boolean)
final case class ValidationResponseWithMetadata(id: String, is_fraud: Boolean, metadata: MessageMetadata)
final case class PoolControlInput(host: String)
final case class PoolControlIndex(host: String, index: Int)
final case class KeyValueStringInt(key: String, value: Int)

trait JsonSupport extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val messageMetadataFormat = jsonFormat2(MessageMetadata)
  implicit val validationInputFormat = jsonFormat2(ValidationInput)
  implicit val validationResponseFormat = jsonFormat2(ValidationResponse)
  implicit val validationInputWithMetadataFormat = jsonFormat3(ValidationInputWithMetadata)
  implicit val validationResponseWithMetadataFormat = jsonFormat3(ValidationResponseWithMetadata)
  implicit val poolControlInputFormat = jsonFormat1(PoolControlInput)
  implicit val poolControlIndexFormat = jsonFormat2(PoolControlIndex)
  implicit val keyValueStringIntFormat = jsonFormat2(KeyValueStringInt)
}
