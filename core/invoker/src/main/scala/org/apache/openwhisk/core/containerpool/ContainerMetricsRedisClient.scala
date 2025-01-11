package org.apache.openwhisk.core.containerpool

import org.apache.openwhisk.core.redis.RedisConfig
import redis.clients.jedis.{Jedis, JedisPool, JedisPoolConfig}
import spray.json.JsObject
import scala.util.{Try, Success, Failure}
import org.apache.openwhisk.common.Logging
import java.time.Duration

object ContainerMetricsRedisClient {
  private var pool: JedisPool = _
  private val redisConfig = RedisConfig()
  
  def init(): Unit = {
    if (pool == null) {
      val poolConfig = new JedisPoolConfig()
      poolConfig.setMaxTotal(redisConfig.maxTotal)
      poolConfig.setMaxIdle(redisConfig.maxIdle)
      poolConfig.setMinIdle(redisConfig.minIdle)
      poolConfig.setMaxWait(Duration.ofMillis(redisConfig.maxWaitMillis))
      poolConfig.setTestOnBorrow(redisConfig.testOnBorrow)
      poolConfig.setTestOnReturn(redisConfig.testOnReturn)
      poolConfig.setTestWhileIdle(redisConfig.testWhileIdle)
      
      pool = new JedisPool(
        poolConfig,
        redisConfig.host,
        redisConfig.port,
        redisConfig.connectTimeout.toMillis.toInt,
        redisConfig.password.orNull,
        redisConfig.database
      )
    }
  }
  
  def withJedis[T](f: Jedis => T)(implicit logging: Logging): Try[T] = {
    Try {
      val jedis = pool.getResource
      try {
        f(jedis)
      } finally {
        jedis.close()
      }
    }.recoverWith { case e: Exception =>
      logging.error(this, s"Redis operation failed: ${e.getMessage}")
      Failure(e)
    }
  }
  
  def storeMetrics(containerId: String, metrics: JsObject)(implicit logging: Logging): Unit = {
    // 使用时间序列格式存储
    // Key格式: container:{containerId}:metrics:{timestamp}
    val timestamp = metrics.fields("timestamp").toString
    val key = s"container:$containerId:metrics:$timestamp"
    
    withJedis { jedis =>
      // 存储当前指标
      jedis.set(key, metrics.compactPrint)
      // 设置过期时间为7天
      jedis.expire(key, 7 * 24 * 60 * 60)
      
      // 将时间戳添加到容器的时间戳集合中,用于按时间查询
      val timestampSetKey = s"container:$containerId:timestamps"
      jedis.zadd(timestampSetKey, timestamp.toDouble, timestamp)
      
      // 清理7天前的数据
      val oneWeekAgo = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
      jedis.zremrangeByScore(timestampSetKey, 0, oneWeekAgo)
    } match {
      case Success(_) => logging.debug(this, s"Successfully stored metrics for container $containerId")
      case Failure(e) => logging.error(this, s"Failed to store metrics for container $containerId: ${e.getMessage}")
    }
  }
  
  // 查询指定时间范围内的监控数据
  def queryMetrics(containerId: String, startTime: Long, endTime: Long)(implicit logging: Logging): Seq[JsObject] = {
    withJedis { jedis =>
      val timestampSetKey = s"container:$containerId:timestamps"
      val timestamps = jedis.zrangeByScore(timestampSetKey, startTime, endTime)
      
      import scala.collection.JavaConverters._
      timestamps.asScala.flatMap { timestamp =>
        val key = s"container:$containerId:metrics:$timestamp"
        Option(jedis.get(key)).map(spray.json.JsonParser(_).asJsObject)
      }.toSeq
    }.getOrElse(Seq.empty)
  }
} 