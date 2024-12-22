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

import akka.actor.Cancellable
import org.apache.openwhisk.common.TransactionId
import org.apache.openwhisk.core.entity._

/**
 * State kept for each activation slot until completion.
 *
 * @param id id of the activation
 * @param namespaceId namespace that invoked the action
 * @param timeoutHandler times out completion of this activation, should be canceled on good paths
 */
case class DistributedActivationEntry(id: ActivationId,
                                    namespaceId: UUID,
                                    invocationNamespace: String,
                                    revision: DocRevision,
                                    transactionId: TransactionId,
                                    memory: ByteSize,
                                    maxConcurrent: Int,
                                    fullyQualifiedEntityName: FullyQualifiedEntityName,
                                    timeoutHandler: Cancellable,
                                    isBlackbox: Boolean,
                                    isBlocking: Boolean,
                                    controllerName: ControllerInstanceId = ControllerInstanceId("0"),
                                    invoker: Option[InvokerInstanceId] = None) 