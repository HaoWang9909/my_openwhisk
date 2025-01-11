package org.apache.openwhisk.core.containerpool

import akka.actor.{Actor, Cancellable, Props}
import org.apache.openwhisk.common.{AkkaLogging, TransactionId}
import org.apache.openwhisk.core.entity.ByteSize
import spray.json.{JsNumber, JsObject, JsString}
import scala.concurrent.duration._
import java.time.Instant
import scala.io.Source

object ContainerResourceMonitor {
  def props(containerId: String, 
           container: Container,
           memoryLimit: ByteSize) = 
    Props(new ContainerResourceMonitor(containerId, container, memoryLimit))
    
  case object CollectMetrics
  case object StopMonitoring
}

class ContainerResourceMonitor(
  containerId: String,
  container: Container, 
  memoryLimit: ByteSize) extends Actor {
  
  import ContainerResourceMonitor._
  
  implicit val logging = new AkkaLogging(context.system.log)
  implicit val ec = context.system.dispatcher
  
  private var lastCpuUsage: Option[Long] = None
  private var lastTimestamp: Option[Instant] = None
  private var scheduledCollection: Option[Cancellable] = None
  
  override def preStart(): Unit = {
    ContainerMetricsRedisClient.init()
    scheduledCollection = Some(
      context.system.scheduler.scheduleAtFixedRate(
        1.seconds,
        1.seconds,
        self,
        CollectMetrics
      )
    )
  }
  
  override def postStop(): Unit = {
    scheduledCollection.foreach(_.cancel())
  }
  
  def receive: Receive = {
    case CollectMetrics => 
      collectMetrics()
      
    case StopMonitoring =>
      context.stop(self)
  }
  
  private def collectMetrics(): Unit = {
    implicit val tid = TransactionId.invokerNanny
    
    try {
      val cpuUsage = readCpuUsage()
      val memUsage = readMemoryUsage()
      
      val cpuPercent = calculateCpuPercentage(cpuUsage)
      val memPercent = (memUsage.toDouble / memoryLimit.toBytes) * 100
      
      val metrics = JsObject(
        "timestamp" -> JsNumber(System.currentTimeMillis()),
        "containerId" -> JsString(containerId),
        "cpuPercent" -> JsNumber(cpuPercent.getOrElse(0.0)),
        "memoryPercent" -> JsNumber(memPercent.toDouble),
        "cpuUsage" -> JsNumber(cpuUsage.toDouble),
        "memoryUsage" -> JsNumber(memUsage.toDouble),
        "cpuCores" -> JsNumber(Runtime.getRuntime.availableProcessors().toDouble)
      )
      
      ContainerMetricsRedisClient.storeMetrics(containerId, metrics)
      
      lastCpuUsage = Some(cpuUsage)
      lastTimestamp = Some(Instant.now)
      
    } catch {
      case e: Exception =>
        logging.error(this, s"Failed to collect metrics: ${e.getMessage}")
    }
  }
  
  private def readCpuUsage(): Long = {
    val cpuPath = s"/sys/fs/cgroup/docker.slice/docker-${containerId}.scope/cpu.stat"
    val source = Source.fromFile(cpuPath)
    try {
      val cpuStats = source.getLines().map { line =>
        val parts = line.split(" ")
        parts(0) -> parts(1).toLong
      }.toMap
      cpuStats.getOrElse("usage_usec", 0L) * 1000
    } finally {
      source.close()
    }
  }
  
  private def readMemoryUsage(): Long = {
    val memPath = s"/sys/fs/cgroup/docker.slice/docker-${containerId}.scope/memory.current"
    val source = Source.fromFile(memPath)
    try {
      source.getLines().next().toLong
    } finally {
      source.close()
    }
  }
  
  private def calculateCpuPercentage(currentUsage: Long): Option[Double] = {
    (lastCpuUsage, lastTimestamp) match {
      case (Some(last), Some(timestamp)) =>
        val usageDiff = currentUsage - last
        val timeDiff = java.time.Duration.between(timestamp, Instant.now).toNanos
        val numCpus = Runtime.getRuntime.availableProcessors()
        Some((usageDiff.toDouble / timeDiff.toDouble) * 100 / numCpus)
      case _ => None
    }
  }
} 