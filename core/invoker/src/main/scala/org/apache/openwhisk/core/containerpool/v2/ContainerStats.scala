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

import java.time.Instant
import spray.json._


case class ContainerStats(
  timestamp: Instant,
  cpuUsage: Double, // CPU使用率百分比
  memoryUsage: Long, // 内存使用字节数
  containerId: String)

object ContainerStats extends DefaultJsonProtocol {
  implicit val instantFormat = new JsonFormat[Instant] {
    def write(instant: Instant) = JsNumber(instant.toEpochMilli)
    def read(value: JsValue) = value match {
      case JsNumber(time) => Instant.ofEpochMilli(time.toLong)
      case _             => throw new DeserializationException("Instant expected")
    }
  }

  implicit val containerStatsFormat = jsonFormat4(ContainerStats.apply)
} 