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
package org.apache.rocketmq.client.consumer.store;

import java.util.Map;
import java.util.Set;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/**
 * Offset store interface
 *
 * 消息消费者在消费一批消息后，需要记录该批消息已经消费完毕，否则当消费者重新
 * 启动时又得从消息消费队列的开始消费，这显然是不能接受的。从5.6.l 节也可以看到，一
 * 次消息消费后会从Proce巳Queue 处理队列中移除该批消息，返回ProceeQueue 最小偏移量，
 * 并存入消息进度表中。那消息进度文件存储在哪合适呢？
 * 广播模式： 同一个消费组的所有消息消费者都需要消费主题下的所有消息，也就是同
 * 组内的消费者的消息消费行为是对立的，互相不影响，故消息进度需要独立存储，最理想
 * 的存储地方应该是与消费者绑定。
 * 集群模式：同一个消费组内的所有消息消费者共享消息主题下的所有消息， 同一条消
 * 息（同一个消息消费队列）在同一时间只会被消费组内的一个消费者消费，并且随着消费队
 * 列的动态变化重新负载，所以消费进度需要保存在一个每个消费者都能访问到的地方。
 *
 * rocketMQ 消息消费进度接口
 */
public interface OffsetStore {
    /**
     * Load
     * 从消息进度存储文件加载消息进度到内存。
     */
    void load() throws MQClientException;

    /**
     * Update the offset,store it in memory
     * 更新内存中的消息消费进度。
     * @param mq 消息消费队列
     * @param offset 消息消费偏移量。
     * @param increaseOnly true 表示offset 必须大于内存中当前的消费偏移量才更新。
     */
    void updateOffset(final MessageQueue mq, final long offset, final boolean increaseOnly);

    /**
     * Get offset from local storage
     *
     * 读取消息消费进度。
     * @param type 读取方式， 可选值READ_FROM _ MEMORY ：从内存中；REA D FROM STOR E ： 从磁盘中； ME MORY FIRST THEN STORE ： 先从内存读取，再从磁盘。
     *
     * @return The fetched offset
     */
    long readOffset(final MessageQueue mq, final ReadOffsetType type);

    /**
     * 持久化指定消息队列进度到磁盘。
     * Persist all offsets,may be in local storage or remote name server
     */
    void persistAll(final Set<MessageQueue> mqs);

    /**
     * Persist the offset,may be in local storage or remote name server
     */
    void persist(final MessageQueue mq);

    /**
     * 将消息队列的消息消费进度从内存中移除
     * Remove offset
     */
    void removeOffset(MessageQueue mq);

    /**
     * 克隆该主题下所有消息队列的消息消费进度。
     * @return The cloned offset table of given topic
     */
    Map<MessageQueue, Long> cloneOffsetTable(String topic);

    /**
     * 更新存储在Brokder 端的消息消费进度，使用集群模式。
     * @param mq
     * @param offset
     * @param isOneway
     */
    void updateConsumeOffsetToBroker(MessageQueue mq, long offset, boolean isOneway) throws RemotingException,
        MQBrokerException, InterruptedException, MQClientException;
}
