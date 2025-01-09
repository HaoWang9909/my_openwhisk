/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.openwhisk.core.containerpool.v2

import akka.actor.{Actor, ActorRef, Props}
import org.apache.openwhisk.common.{Logging, TransactionId}
import org.apache.openwhisk.core.containerpool.Container
import org.apache.openwhisk.core.entity.InstanceId

import scala.concurrent.duration._
import scala.io.Source
import java.time.Instant
import java.nio.file.{Files, Paths}

object ContainerMonitor {
  def props(container: Container,
            redisClient: ActorRef,
            instanceId: InstanceId,
            logging: Logging): Props =
    Props(new ContainerMonitor(container, redisClient, instanceId, logging))

  // 监控消息
  case object CollectStats
  case object StopMonitoring
}

class ContainerMonitor(container: Container,
                      redisClient: ActorRef,
                      instanceId: InstanceId,
                      logging: Logging) extends Actor {
  
  import ContainerMonitor._
  
  implicit val system = context.system
  implicit val ec = context.dispatcher
  
  // 上次CPU使用量
  private var lastCpuUsage: Option[Long] = None
  private var lastCpuTime: Option[Instant] = None
  
  // 启动定时采集
  val collectInterval = 1.second
  system.scheduler.scheduleWithFixedDelay(0.seconds, collectInterval, self, CollectStats)
  
  def receive = {
    case CollectStats =>
      collectContainerStats()
      
    case StopMonitoring =>
      context.stop(self)
  }
  
  private def collectContainerStats(): Unit = {
    implicit val tid = TransactionId.invokerNanny
    
    try {
      val containerId = container.containerId.asString
      val currentTime = Instant.now
      
      // cgroup v2 路径
      val cgroupV2BasePath = "/sys/fs/cgroup/docker.slice"
      val containerCgroupPath = s"$cgroupV2BasePath/docker-${containerId}.scope"
      
      // 读取 CPU 使用量
      val cpuPath = s"$containerCgroupPath/cpu.stat"
      val cpuStats = readCgroupV2CpuStats(cpuPath)
      val currentCpuUsage = cpuStats.getOrElse(0L)
      
      // 计算 CPU 使用率
      val cpuUsagePercent = (lastCpuUsage, lastCpuTime) match {
        case (Some(last), Some(lastTime)) =>
          val cpuDiff = currentCpuUsage - last
          val timeDiff = currentTime.toEpochMilli - lastTime.toEpochMilli
          if (timeDiff > 0) {
            val usage = (cpuDiff.toDouble / timeDiff.toDouble) * 100
            logging.debug(this, s"CPU usage calculation: diff=$cpuDiff, timeDiff=$timeDiff, usage=$usage%")
            usage
          } else 0.0
        case _ => 
          logging.debug(this, "First CPU usage measurement, setting to 0")
          0.0
      }
      
      lastCpuUsage = Some(currentCpuUsage)
      lastCpuTime = Some(currentTime)
      
      // 读取内存使用量
      val memoryPath = s"$containerCgroupPath/memory.current"
      val memoryUsage = readLongFromFile(memoryPath)
      
      // 构建监控数据
      val stats = ContainerStats(
        timestamp = currentTime,
        cpuUsage = cpuUsagePercent,
        memoryUsage = memoryUsage,
        containerId = containerId)
        
      // 发送到 Redis
      redisClient ! SaveContainerStats(stats)
      
      logging.info(this, s"Container stats collected: CPU=${cpuUsagePercent}%, Memory=${memoryUsage}bytes, Path=$containerCgroupPath")
      
    } catch {
      case e: Exception =>
        logging.error(this, s"Failed to collect container stats: ${e.getMessage}")
        e.printStackTrace()
    }
  }
  
  private def readCgroupV2CpuStats(path: String): Option[Long] = {
    if (Files.exists(Paths.get(path))) {
      val source = Source.fromFile(path)
      try {
        val lines = source.getLines().toList
        logging.debug(this, s"CPU stat file contents: ${lines.mkString("\n")}")
        
        // 从 cpu.stat 中读取 usage_usec
        val usageOpt = lines.find(_.startsWith("usage_usec")).map(line => {
          val value = line.split(" ")(1).trim.toLong
          // 转换微秒为纳秒以保持与旧版本兼容
          value * 1000
        })
        
        if(usageOpt.isEmpty) {
          logging.warn(this, s"Could not find usage_usec in $path")
        }
        
        usageOpt
      } catch {
        case e: Exception =>
          logging.error(this, s"Error reading CPU stats from $path: ${e.getMessage}")
          None
      } finally {
        source.close()
      }
    } else {
      logging.warn(this, s"CPU stat file does not exist: $path")
      None
    }
  }
  
  private def readLongFromFile(path: String): Long = {
    if (Files.exists(Paths.get(path))) {
      val source = Source.fromFile(path)
      try {
        val value = source.getLines().next().trim.toLong
        logging.debug(this, s"Read value $value from $path")
        value
      } catch {
        case e: Exception =>
          logging.error(this, s"Error reading from $path: ${e.getMessage}")
          0L
      } finally {
        source.close()
      }
    } else {
      logging.warn(this, s"File does not exist: $path")
      0L
    }
  }
}

// Redis保存消息
case class SaveContainerStats(stats: ContainerStats) 