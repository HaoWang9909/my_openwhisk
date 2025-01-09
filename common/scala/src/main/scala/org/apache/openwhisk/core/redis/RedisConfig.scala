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

package org.apache.openwhisk.core.redis

import scala.concurrent.duration._

/**
 * Redis 配置类
 * 用于统一管理 Redis 相关配置
 */
case class RedisConfig(
  // 基础连接配置
  host: String = "149.165.159.154",
  port: Int = 6379,
  password: Option[String] = Some("openwhisk"),
  database: Int = 0,
  connectTimeout: FiniteDuration = 2.seconds,
  
  // 连接池配置
  maxTotal: Int = 8,
  maxIdle: Int = 8,
  minIdle: Int = 0,
  maxWaitMillis: Long = -1L,
  
  // 连接测试配置
  testOnBorrow: Boolean = true,
  testOnReturn: Boolean = false,
  testWhileIdle: Boolean = true,
  testOnCreate: Boolean = false,
  
  // 空闲连接管理
  timeBetweenEvictionRunsMillis: Long = 30000,
  numTestsPerEvictionRun: Int = 3,
  minEvictableIdleTimeMillis: Long = 60000,
  
  // 连接池行为配置
  blockWhenExhausted: Boolean = true,
  
  // 重试配置
  retryAttempts: Int = 3,
  retryInterval: FiniteDuration = 1.second
) 