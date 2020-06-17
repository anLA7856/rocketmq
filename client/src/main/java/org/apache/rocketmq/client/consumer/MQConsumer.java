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

import java.util.Set;
import org.apache.rocketmq.client.MQAdmin;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * 1. 集群模式 主题下的同一条
 * 消息只允许被其中一个消费者消费。
 * 2. 广播模式： 主题下的同一条消息将被集群内的所有消
 * 费者消费一次。
 *
 * 1. 推模式 ： RocketMQ 消息推模式的实现基于拉模式，在拉模式上包装一层，一个拉取任
 * 务完成后开始下一个拉取任务。
 * 2. 拉模式： 是消费端主动发起拉消息请求
 *
 *
 * Message queue consumer interface
 */
public interface MQConsumer extends MQAdmin {

    /**
     * 发送消息ACK 确认
     * If consuming failure,message will be send back to the brokers,and delay consuming some time
     * @param msg 消息
     * @param delayLevel 消息延迟级别
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    @Deprecated
    void sendMessageBack(final MessageExt msg, final int delayLevel) throws RemotingException,
        MQBrokerException, InterruptedException, MQClientException;

    /**
     * If consuming failure,message will be send back to the broker,and delay consuming some time
     * @param msg
     * @param delayLevel
     * @param brokerName 消息服务器名称
     * @throws RemotingException
     * @throws MQBrokerException
     * @throws InterruptedException
     * @throws MQClientException
     */
    void sendMessageBack(final MessageExt msg, final int delayLevel, final String brokerName)
        throws RemotingException, MQBrokerException, InterruptedException, MQClientException;

    /**
     * Fetch message queues from consumer cache according to the topic
     *
     * @param topic message topic
     * @return queue set 获取消费者对主题topic 分配了哪些消息队列。
     */
    Set<MessageQueue> fetchSubscribeMessageQueues(final String topic) throws MQClientException;
}
