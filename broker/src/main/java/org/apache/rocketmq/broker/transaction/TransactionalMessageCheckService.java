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
package org.apache.rocketmq.broker.transaction;

import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;

/**
 * 本节将深入学习事
 * 务状态、消息回查，事务消息存储在消息服务器时主题被替换为肌Q_SYS_ TRANS_ HALF_
 * TOPIC ，执行完本地事务返回本地事务状态为UN KNOW 时，结束事务时将不做任何处理，
 * 而是通过事务状态定时回查以期得到发送端明确的事务操作（提交事务或回滚事务） 。
 * RocketMQ 通过TransactionalMessageCheckService 线程定时去检测RMQ_SYS_ TRANS_
 * HALF TOPIC 主题中的消息，回查消息的事务状态。TransactionalMessageCheckService 的检
 * 测频率默认为l 分钟，可通过在broker.conf 文件中设置transactionChecklnterval 来改变默认
 * 值，单位为毫秒。
 */
public class TransactionalMessageCheckService extends ServiceThread {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.TRANSACTION_LOGGER_NAME);

    private BrokerController brokerController;

    public TransactionalMessageCheckService(BrokerController brokerController) {
        this.brokerController = brokerController;
    }

    @Override
    public String getServiceName() {
        return TransactionalMessageCheckService.class.getSimpleName();
    }

    @Override
    public void run() {
        log.info("Start transaction check service thread!");
        long checkInterval = brokerController.getBrokerConfig().getTransactionCheckInterval();
        while (!this.isStopped()) {
            this.waitForRunning(checkInterval);
        }
        log.info("End transaction check service thread!");
    }

    /**
     * transactionTimeOut ：事务的过期时间只有当消息的存储时间加上过期时间大于系统
     * 当前时间时，才对消息执行事务状态回查，否则在下一次周期中执行事务回查操作。
     * transactionCheckMax ： 事务回查最大检测次数，如果超过最大检测次数还是无法获知
     * 消息的事务状态， RocketMQ 将不会继续对消息进行事务状态回查，而是直接丢弃即相当于
     * 回滚事务
     */
    @Override
    protected void onWaitEnd() {
        long timeout = brokerController.getBrokerConfig().getTransactionTimeOut();
        int checkMax = brokerController.getBrokerConfig().getTransactionCheckMax();
        long begin = System.currentTimeMillis();
        log.info("Begin to check prepare message, begin time:{}", begin);
        this.brokerController.getTransactionalMessageService().check(timeout, checkMax, this.brokerController.getTransactionalMessageCheckListener());
        log.info("End to check prepare message, consumed time:{}", System.currentTimeMillis() - begin);
    }

}
