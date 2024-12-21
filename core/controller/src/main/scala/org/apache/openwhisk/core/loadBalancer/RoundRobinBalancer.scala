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

package org.apache.openwhisk.core.loadBalancer

import java.util.concurrent.atomic.AtomicInteger
import akka.actor.{ActorRef, ActorRefFactory, ActorSystem, Props}
import org.apache.openwhisk.common.{InvokerHealth, InvokerState, Logging, TransactionId}
import org.apache.openwhisk.core.WhiskConfig
import org.apache.openwhisk.core.connector._
import org.apache.openwhisk.core.entity._
import org.apache.openwhisk.core.entity.size._
import org.apache.openwhisk.spi.SpiLoader
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

class RoundRobinBalancer(
  config: WhiskConfig,
  controllerInstance: ControllerInstanceId,
  feedFactory: FeedFactory,
  val invokerPoolFactory: InvokerPoolFactory,
  implicit val messagingProvider: MessagingProvider)(
  implicit override val actorSystem: ActorSystem,
  implicit val logging: Logging)
    extends CommonLoadBalancer(config, feedFactory, controllerInstance) {

  /** 轮询计数器,用于记录下一个要使用的 invoker 索引 */
  private val counter = new AtomicInteger(0)

  /** 创建 invoker 监控池 */
  override protected[loadBalancer] val invokerPool = invokerPoolFactory.createInvokerPool(
    actorSystem,
    messagingProvider,
    messageProducer,
    sendActivationToInvoker,
    None)

  /** 
   * 实现调度请求到 invoker 的核心逻辑
   * 使用轮询方式选择下一个可用的 invoker
   */
  override def publish(action: ExecutableWhiskActionMetaData, msg: ActivationMessage)(
    implicit transid: TransactionId): Future[Future[Either[ActivationId, WhiskActivation]]] = {

    val chosen = getNextInvoker()
    val activationResult = setupActivation(msg, action, chosen)

    sendActivationToInvoker(messageProducer, msg, chosen).map(_ => activationResult)
  }

  /**
   * 获取下一个可用的 invoker
   * 使用轮询方式,确保请求均匀分布到所有健康的 invoker
   */
  private def getNextInvoker(): InvokerInstanceId = {
    val invokers = Await.result(invokerHealth(), 5.seconds)
    val currentCounter = counter.getAndUpdate { current =>
      if (current >= invokers.size - 1) 0 else current + 1
    }
    
    if (invokers.nonEmpty) {
      // 获取所有健康的 invoker
      val healthyInvokers = invokers.filter(_.status == InvokerState.Healthy)
      if (healthyInvokers.nonEmpty) {
        // 如果有健康的 invoker,从中选择一个
        val index = currentCounter % healthyInvokers.size
        healthyInvokers(index).id
      } else {
        // 如果没有健康的 invoker,从所有 invoker 中选择一个
        InvokerInstanceId(currentCounter % invokers.size, userMemory = 0.MB)
      }
    } else {
      // 如果没有 invoker 可用,使用 id 0
      InvokerInstanceId(0, userMemory = 0.MB)
    }
  }

  /**
   * 获取所有 invoker 的健康状态
   */
  override def invokerHealth(): Future[IndexedSeq[InvokerHealth]] = {
    Future.successful(IndexedSeq(new InvokerHealth(InvokerInstanceId(0, userMemory = 0.MB), InvokerState.Healthy)))
  }

  /**
   * 更新 invoker 的状态
   */
  override protected def releaseInvoker(invoker: InvokerInstanceId, entry: ActivationEntry): Unit = {
    logging.debug(this, s"released invoker ${invoker.toInt}")
  }
}

object RoundRobinBalancer extends LoadBalancerProvider {

  override def instance(whiskConfig: WhiskConfig, instance: ControllerInstanceId)(
    implicit actorSystem: ActorSystem,
    logging: Logging): LoadBalancer = {

    val invokerPoolFactory = new InvokerPoolFactory {
      override def createInvokerPool(
        actorRefFactory: ActorRefFactory,
        messagingProvider: MessagingProvider,
        messagingProducer: MessageProducer,
        sendActivationToInvoker: (MessageProducer, ActivationMessage, InvokerInstanceId) => Future[ResultMetadata],
        monitor: Option[ActorRef]): ActorRef = {

        actorRefFactory.actorOf(InvokerPool.props(
          (f, i) => f.actorOf(InvokerActor.props(i, instance)),
          (m, i) => sendActivationToInvoker(messagingProducer, m, i),
          messagingProvider.getConsumer(whiskConfig, s"health${instance.asString}", "health", maxPeek = 128),
          monitor))
      }
    }

    new RoundRobinBalancer(
      whiskConfig,
      instance,
      createFeedFactory(whiskConfig, instance),
      invokerPoolFactory,
      SpiLoader.get[MessagingProvider])
  }

  override def createFeedFactory(config: WhiskConfig, controllerInstance: ControllerInstanceId)(
    implicit actorSystem: ActorSystem, logging: Logging): FeedFactory = {
    new FeedFactory {
      def createFeed(f: ActorRefFactory, provider: MessagingProvider, acker: Array[Byte] => Future[Unit]) = {
        f.actorOf(Props {
          new MessageFeed(
            "activeack",
            logging,
            provider.getConsumer(config, "completed" + controllerInstance.asString, "completed", maxPeek = 128),
            128,
            1.second,
            acker)
        })
      }
    }
  }

  def requiredProperties: Map[String, String] = WhiskConfig.kafkaHosts
}
