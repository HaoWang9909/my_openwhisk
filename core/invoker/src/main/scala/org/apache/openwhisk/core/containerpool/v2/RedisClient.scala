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

import akka.actor.{Actor, Props}
import org.apache.openwhisk.common.{Logging, TransactionId}
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import spray.json._
import org.apache.openwhisk.core.ConfigKeys
import org.apache.openwhisk.core.redis.RedisConfig
import pureconfig._
import pureconfig.generic.auto._
import java.time.Duration
//import scala.concurrent.duration._
//import scala.util.{Try, Success, Failure}

object RedisClient {
  def props(logging: Logging): Props = {
    val config = loadConfigOrThrow[RedisConfig](ConfigKeys.redis)
    Props(new RedisClient(config, logging))
  }
}

class RedisClient(config: RedisConfig, logging: Logging) extends Actor {
  implicit val tid = TransactionId.invokerNanny
  
  private val poolConfig = {
    val conf = new JedisPoolConfig()
    conf.setMaxTotal(config.maxTotal)
    conf.setMaxIdle(config.maxIdle)
    conf.setMinIdle(config.minIdle)
    conf.setMaxWait(Duration.ofMillis(config.maxWaitMillis))
    conf.setTestOnBorrow(config.testOnBorrow)
    conf.setTestOnReturn(config.testOnReturn)
    conf.setTestWhileIdle(config.testWhileIdle)
    conf.setTimeBetweenEvictionRuns(Duration.ofMillis(config.timeBetweenEvictionRunsMillis))
    conf.setNumTestsPerEvictionRun(config.numTestsPerEvictionRun)
    conf.setMinEvictableIdleTime(Duration.ofMillis(config.minEvictableIdleTimeMillis))
    conf.setTestOnCreate(config.testOnCreate)
    conf.setBlockWhenExhausted(config.blockWhenExhausted)
    conf
  }
  
  private val pool = new JedisPool(
    poolConfig,
    config.host,
    config.port,
    config.connectTimeout.toMillis.toInt,
    config.password.orNull,
    config.database)
    
  // 初始化时进行连接测试
  checkConnection()
  
  def receive = {
    case SaveContainerStats(stats) =>
      saveStats(stats)
  }
  
  private def saveStats(stats: ContainerStats): Unit = {
    var attempt = 1
    var success = false
    var lastError: Throwable = null
    
    while (attempt <= config.retryAttempts && !success) {
      var jedis: Jedis = null
      try {
        jedis = pool.getResource
        
        // 使用Hash结构存储
        val key = s"container:${stats.containerId}:stats"
        val field = stats.timestamp.toEpochMilli.toString
        val value = stats.toJson.compactPrint
        
        jedis.hset(key, field, value)
        
        // 设置过期时间(7天)
        jedis.expire(key, 7 * 24 * 60 * 60)
        
        success = true
        
      } catch {
        case e: Exception =>
          lastError = e
          logging.error(this, s"Failed to save stats to Redis (attempt $attempt/${config.retryAttempts}): ${e.getMessage}")
          if (attempt < config.retryAttempts) {
            Thread.sleep(config.retryInterval.toMillis)
          }
          attempt += 1
      } finally {
        if (jedis != null) {
          jedis.close()
        }
      }
    }
    
    if (!success) {
      logging.error(this, s"All attempts to save stats to Redis failed. Last error: $lastError")
    }
  }
  
  private def checkConnection(): Unit = {
    var jedis: Jedis = null
    try {
      jedis = pool.getResource
      val pong = jedis.ping()
      if (pong == "PONG") {
        logging.info(this, "Successfully connected to Redis")
      } else {
        logging.error(this, s"Redis ping returned unexpected response: $pong")
      }
    } catch {
      case e: Exception =>
        logging.error(this, s"Failed to connect to Redis: ${e.getMessage}")
    } finally {
      if (jedis != null) {
        jedis.close()
      }
    }
  }
  
  override def postStop(): Unit = {
    if (pool != null) {
      pool.close()
    }
  }
} 