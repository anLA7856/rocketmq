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
package org.apache.rocketmq.client.consumer;

import java.util.List;
import org.apache.rocketmq.common.message.MessageQueue;

/**
 * Strategy Algorithm for message allocating between consumers
 *
 * RocketMQ 默认提供5 种分配算法。 p156
 * 1 ) AllocateMessageQueueAveragely ：平均分配，推荐指数为5 颗星。
 * 举例来说，如果现在有8 个消息消费队列ql , q2 , q3 ， 俐， q5 ， 币，q7 ，币，有3 个消费者
 * cl,c2 , c3 ，那么根据该负载算法，消息队列分配如下：
 * c1: q 1 ,q2,q3
 * c2:q4 ,q5,q6
 * c3:q7 ,q8
 * 2. AllocateMessageQueueAveragelyByCircle ：平均轮询分配，推荐指数为5 颗星
 * 3 ) AllocateMessageQueueConsistentHash ： 一致性hash 。不推荐使用，因为消息队列负载信息不容易跟踪。
 * 4 ) AllocateMessageQueueByConfig ：根据配置，为每一个消费者配置固定的消息队列。
 *5 ) AllocateMessageQueueByMachineRoom ：根据Broker 部署机房名，对每个消费者负责不同的Broker 上的队列。
 *
 */
public interface AllocateMessageQueueStrategy {

    /**
     * Allocating by consumer id
     *
     * @param consumerGroup current consumer group
     * @param currentCID current consumer id
     * @param mqAll message queue set in current topic
     * @param cidAll consumer set in current consumer group
     * @return The allocate result of given strategy
     */
    List<MessageQueue> allocate(
        final String consumerGroup,
        final String currentCID,
        final List<MessageQueue> mqAll,
        final List<String> cidAll
    );

    /**
     * Algorithm name
     *
     * @return The strategy name
     */
    String getName();
}
