package kafka_sync

case class InputMetadata(host: String, poolIndex: Int)
case class WrapperMetadata(metadata: InputMetadata)
case class FakerProcessorResponse(id: String, is_fraud: Boolean, metadata: InputMetadata)
case class ErrorResponse(error: String, message: Option[String] = None)
