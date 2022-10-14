package sandbox_akka

import org.apache.kafka.common.serialization.{Deserializer, Serde, Serializer}
import spray.json._
import akka.util.ByteString

class JSONSerializer[T >: Null <: Any : JsonFormat] extends Serializer[T] {
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def serialize(topic: String, data: T): Array[Byte] = {
//    println("serialize=", data)
//    println("serialize=", data.toJson)
//    println("serialize=", data.toJson.compactPrint)
//    println("serialize=", data.toJson.compactPrint.getBytes)
    data.toJson.compactPrint.getBytes
  }

  override def close(): Unit = ()
}

class JSONDeserializer[T >: Null <: Any : JsonFormat] extends Deserializer[T] {
  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def close(): Unit = ()

  override def deserialize(topic: String, data: Array[Byte]): T = {
//    println("deserialize=", data)
//    println("deserialize=", ByteString(data))
//    println("deserialize=", ByteString(data).utf8String)
//    println("deserialize=", ByteString(data).utf8String.parseJson)
//    println("deserialize=", ByteString(data).utf8String.parseJson.convertTo[T])
    ByteString(data).utf8String.parseJson.convertTo[T]
  }
}

class JSONSerde[T >: Null <: Any : JsonFormat] extends Serde[T] {
  override def deserializer(): Deserializer[T] = new JSONDeserializer[T]

  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()

  override def close(): Unit = ()

  override def serializer(): Serializer[T] = new JSONSerializer[T]
}
