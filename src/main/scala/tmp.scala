
//class JsonSerializer[T >: Null <: Any : JsonFormat] extends Serializer[T] {
//  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()
//
//  override def serialize(topic: String, data: T): Array[Byte] = {
//    data.toJson.compactPrint.getBytes
//  }
//
//  override def close(): Unit = ()
//}
//
//class JsonDeserializer[T >: Null <: Any : JsonFormat] extends Deserializer[T] {
//  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()
//
//  override def close(): Unit = ()
//
//  override def deserialize(topic: String, data: Array[Byte]): T = {
//    ByteString(data).utf8String.parseJson.convertTo[T]
//  }
//}
//
//class JsonSerde[T >: Null <: Any : JsonFormat] extends JSerde[T] {
//  override def deserializer(): Deserializer[T] = new JsonDeserializer[T]
//
//  override def serializer(): Serializer[T] = new JsonSerializer[T]
//
//  override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = ()
//
//  override def close(): Unit = ()
//}

//object TopicCreator {
//  def createPrivateTopic(host: String): Unit = {
//    val config: Properties = new Properties
//    config.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
//
//    val newTopic = new NewTopic("sandbox_akka_responses_" + host, 1, 1.toShort)
//
//    val client = Admin.create(config)
//    client.createTopics(List(newTopic).asJavaCollection).values()
//  }
//}