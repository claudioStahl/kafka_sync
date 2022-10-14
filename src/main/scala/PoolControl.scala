package claudiostahl

import java.util.concurrent.atomic.AtomicInteger
import java.net.InetSocketAddress
import com.loopfor.zookeeper._

object PoolControl {
  var atomicIndex = new AtomicInteger(-1)
  val acl = ACL("world:anyone=*")
  val rootDir = "/sandbox_akka"
  val poolDir = rootDir + "/pool"
  val lockDir = rootDir + "/_lock"

  def init(poolSize: Int, host: String): Unit = {
    val inet = new InetSocketAddress("localhost", 2181)

    val config = Configuration {
      inet :: Nil
    } withWatcher { (event, session) =>
      println("event=", event)
      println("session=", session, session.state == ConnectedState)
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
    zk.sync.exists(rootDir) match {
      case Some(_) => 0
      case None => zk.sync.create(rootDir, null, Seq(acl), Persistent)
    }

    zk.sync.exists(poolDir) match {
      case Some(_) => 0
      case None => zk.sync.create(poolDir, null, Seq(acl), Persistent)
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

    println("currentIndex=", currentIndex)
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
    println("currentNode=", currentNode)
    val currentName = currentNode.split("/").last
    val currentNumber = currentName.toInt
    val lastName = "%010d".format(currentNumber - 1)
    val lastNode = lockDir + "/" + lastName
    println("lastNode=", lastNode)

    val lockChildren = zk.sync.children(lockDir).sorted
    println("lockChildren=", lockChildren)

    if (lockChildren.head == currentName) {
      println("lockStatus=", true)

      exec(zk)
      zk.sync.delete(currentNode, None)

    } else {
      println("lockStatus=", false)

      val existLast = zk.sync watch { e =>
        println("waitLock=", e)
        doExecWithLock(zk, currentNode)(exec)
      } exists (lastNode)

      existLast match {
        case Some(_) => 0
        case None => doExecWithLock(zk, currentNode)(exec)
      }
    }
  }
}
