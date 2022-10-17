package sandbox_akka

import java.util.concurrent.atomic.AtomicInteger
import java.net.InetSocketAddress
import com.loopfor.zookeeper._
import com.typesafe.scalalogging.Logger

object PoolControl {
  var atomicIndex = new AtomicInteger(-1)
  val acl = ACL("world:anyone=*")
  val rootDir = sys.env("ZOOKEEPER_ROOT_DIR")
  val poolDir = rootDir + "/pool"
  val lockDir = rootDir + "/_lock"
  val logger = Logger(getClass.getName)

  def init(poolSize: Int, host: String): Unit = {
    val config = Configuration {
      sys.env("ZOOKEEPER_SERVERS").split(",").map { item =>
        val Array(host, port) = item.split(":").map(_.trim)
        new InetSocketAddress(host, port.toInt)
      }
    } withWatcher { (event, session) =>
      logger.info("event: " + event)
      logger.info("session: " + session)
    }

    val zk = Zookeeper(config)

    zk.sync.sync

    setupBase(zk)

    execWithLock(zk) { zk =>
      setupPool(zk, poolSize)
      connectToPool(zk, poolSize, host)
    }

    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run(): Unit = {
        zk.close()
      }
    })
  }

  private def setupBase(zk: Zookeeper): Unit = {
    if (zk.sync.exists(rootDir).isEmpty) {
      zk.sync.create(rootDir, null, Seq(acl), Persistent)
    }

    if (zk.sync.exists(poolDir).isEmpty) {
      zk.sync.create(poolDir, null, Seq(acl), Persistent)
    }
  }

  private def setupPool(zk: Zookeeper, poolSize: Int): Unit = {
    (1 to poolSize).foreach { i =>
      val poolIndexNode = poolDir + "/" + i
      zk.sync.exists(poolIndexNode) match {
        case Some(_) => 0
        case None => zk.sync.create(poolIndexNode, null, Seq(acl), Persistent)
      }
    }
  }

  private def connectToPool(zk: Zookeeper, poolSize: Int, host: String): Unit = {
    val total = collection.mutable.Map[Int, Int]()

    zk.sync.sync

    (1 to poolSize).foreach { i =>
      val poolIndexNode = poolDir + "/" + i
      val indexChildren = zk.sync.children(poolIndexNode)

      total.put(i, indexChildren.length)
    }

    val currentIndex = total.minBy(_._2)._1
    val currentNode = poolDir + "/" + currentIndex + "/" + host
    zk.sync.create(currentNode, null, Seq(acl), Ephemeral)
    atomicIndex.set(currentIndex)

    logger.info("currentIndex: " + currentIndex)

    Consumer.launchConsumer(host, currentIndex)
  }

  private def execWithLock(zk: Zookeeper)(exec: (Zookeeper) => Unit): Unit = {
    zk.sync.exists(lockDir) match {
      case Some(_) => 0
      case None => zk.sync.create(lockDir, null, Seq(acl), Container)
    }

    val currentNode = zk.sync.create(lockDir + "/", null, Seq(acl), EphemeralSequential)
    doExecWithLock(zk, currentNode)(exec)
  }

  private def doExecWithLock(zk: Zookeeper, currentNode: String)(exec: (Zookeeper) => Unit): Unit = {
    val currentName = currentNode.split("/").last
    val currentNumber = currentName.toInt
    val lastName = "%010d".format(currentNumber - 1)
    val lastNode = lockDir + "/" + lastName

    val lockChildren = zk.sync.children(lockDir).sorted

    if (lockChildren.head == currentName) {
      logger.info("receive lock")

      exec(zk)
      zk.sync.delete(currentNode, None)

    } else {
      logger.info("waiting lock")

      val existLast = zk.sync watch { e =>
        doExecWithLock(zk, currentNode)(exec)
      } exists (lastNode)

      if (existLast.isEmpty) doExecWithLock(zk, currentNode)(exec)
    }
  }
}
