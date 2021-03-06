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

package org.apache.rocketmq.common.protocol.header;

import org.apache.rocketmq.remoting.CommandCustomHeader;
import org.apache.rocketmq.remoting.annotation.CFNotNull;
import org.apache.rocketmq.remoting.annotation.CFNullable;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;

public class ConsumerSendMsgBackRequestHeader implements CommandCustomHeader {
    @CFNotNull
    private Long offset;  // 消息物理偏移量。
    @CFNotNull
    private String group;  // 消费组名。
    /**
     * 延迟级别， RcketMQ 不支持精确的定时消息调度，而是提供几个延时
     * 级别， Messages toreConfig# messageD巳layLevel = ” ls Ss 10s 30s lm 2m 3m 4m Sm 6m 7m 8m
     * 9m lOm 20m 30m lh 2h”，如果delayLevel= I 表示延迟缸，delayLevel=2 则表示延迟1 Os 。
     */
    @CFNotNull
    private Integer delayLevel;
    private String originMsgId;  // 消息ID 。
    private String originTopic;  // 消息主题。
    @CFNullable
    private boolean unitMode = false;
    private Integer maxReconsumeTimes;  // 最大重新消费次数，默认为16 次。

    @Override
    public void checkFields() throws RemotingCommandException {

    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public Integer getDelayLevel() {
        return delayLevel;
    }

    public void setDelayLevel(Integer delayLevel) {
        this.delayLevel = delayLevel;
    }

    public String getOriginMsgId() {
        return originMsgId;
    }

    public void setOriginMsgId(String originMsgId) {
        this.originMsgId = originMsgId;
    }

    public String getOriginTopic() {
        return originTopic;
    }

    public void setOriginTopic(String originTopic) {
        this.originTopic = originTopic;
    }

    public boolean isUnitMode() {
        return unitMode;
    }

    public void setUnitMode(boolean unitMode) {
        this.unitMode = unitMode;
    }

    public Integer getMaxReconsumeTimes() {
        return maxReconsumeTimes;
    }

    public void setMaxReconsumeTimes(final Integer maxReconsumeTimes) {
        this.maxReconsumeTimes = maxReconsumeTimes;
    }

    @Override
    public String toString() {
        return "ConsumerSendMsgBackRequestHeader [group=" + group + ", originTopic=" + originTopic + ", originMsgId=" + originMsgId
            + ", delayLevel=" + delayLevel + ", unitMode=" + unitMode + ", maxReconsumeTimes=" + maxReconsumeTimes + "]";
    }
}
