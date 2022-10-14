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

  def setupBase(zk: Zookeeper): Unit = {
    zk.sync.exists(rootDir) match {
      case Some(_) => 0
      case None => zk.sync.create(rootDir, null, Seq(acl), Persistent)
    }

    zk.sync.exists(poolDir) match {
      case Some(_) => 0
      case None => zk.sync.create(poolDir, null, Seq(acl), Persistent)
    }
  }

  def setupPool(zk: Zookeeper, poolSize: Int): Unit = {
    (1 to poolSize).foreach { i =>
      val poolPathIndex = poolDir + "/" + i
      zk.sync.exists(poolPathIndex) match {
        case Some(_) => 0
        case None => zk.sync.create(poolPathIndex, null, Seq(acl), Persistent)
      }
    }
  }

  def connectToPool(zk: Zookeeper, poolSize: Int, host: String): Unit = {
    val total = collection.mutable.Map[Int, Int]()

    (1 to poolSize).foreach { i =>
      val poolPathIndex = poolDir + "/" + i
      val indexChildren = zk.sync.children(poolPathIndex)

      total.put(i, indexChildren.length)
    }

    val result = total.min
    val currentIndex = result._1
    val currentPath = poolDir + "/" + currentIndex + "/" + host
    zk.sync.create(currentPath, null, Seq(acl), Ephemeral)
    atomicIndex.set(currentIndex)
  }

  def execWithLock(zk: Zookeeper)(exec: (Zookeeper) => Unit): Unit = {
    zk.sync.exists(lockDir) match {
      case Some(_) => 0
      case None => zk.sync.create(lockDir, null, Seq(acl), Container)
    }

    val currentNode = zk.sync.create(lockDir + "/lock-", null, Seq(acl), EphemeralSequential)
    println("currentNode=", currentNode)

    doExecWithLock(zk, currentNode)(exec)
  }

  def doExecWithLock(zk: Zookeeper, currentNode: String)(exec: (Zookeeper) => Unit): Unit = {
    val currentName = currentNode.split("/").last
    val currentNumber = currentName.split("-").last.toInt
    val lastNumber = "%010d".format(currentNumber - 1)
    val lastNode = lockDir + "/lock-" + lastNumber
    println("lastNode=", lastNode)

    val lockChildren = zk.sync.children(lockDir)
    println("lockChildren=", lockChildren)

    if (lockChildren.head == currentName) {
      println("lockStatus=", true)
      exec(zk)
      zk.sync.delete(currentNode, None)

    } else {
      println("lockStatus=", false)

      val existLast = zk.sync watch { e =>
        println("watch.exists=", e)
      } exists (currentNode)

      existLast match {
        case Some(_) => 0
        case None => doExecWithLock(zk, currentNode)(exec)
      }
    }
  }
}
