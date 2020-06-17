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
package org.apache.rocketmq.client.impl.consumer;

import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.client.log.ClientLogger;
import org.apache.rocketmq.common.ServiceThread;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.common.utils.ThreadUtils;


/**
 * 消息消费有两种模式：广播模式与
 * 集群模式，广播模式比较简单，每一个消费者需要去拉取订阅主题下所有消费队列的消息，
 * 本节主要基于集群模式。在集群模式下，同一个消费组内有多个消息消费者，同一个主题
 * 存在多个消费队列，那么消费者如何进行消息队列负载呢？从上文启动流程也知道，每一
 * 个消费组内维护一个线程池来消费消息，那么这些线程又是如何分工合作的呢？
 * 消息队列负载，通常的做法是一个消息队列在同一时间只允许被一个消息消费者消费，
 * 一个消息消费者可以同时消费多个消息队列，那么RocketMQ 是如何实现的呢？带着上述
 * 问题，我们开始RocketMQ 消息消费机制的探讨。
 * 从MQClientlnstance 的启动流程中可以看出， RocketMQ 使用一个单独的线程
 * PullMessageService 来负责消息的拉取。
 */
public class PullMessageService extends ServiceThread {
    private final InternalLogger log = ClientLogger.getLog();
    private final LinkedBlockingQueue<PullRequest> pullRequestQueue = new LinkedBlockingQueue<PullRequest>();
    private final MQClientInstance mQClientFactory;
    private final ScheduledExecutorService scheduledExecutorService = Executors
        .newSingleThreadScheduledExecutor(new ThreadFactory() {
            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "PullMessageServiceScheduledThread");
            }
        });

    public PullMessageService(MQClientInstance mQClientFactory) {
        this.mQClientFactory = mQClientFactory;
    }

    /**
     * Pu !IMessageService 提供延迟添加与立即添加2 种方式将PullRequest 放入到 pullRequestQueue 中。
     *
     *
     * 通过跟踪发现，主要有两个地方会调用， 一个是在RocketMQ 根据P ul!R巳quest 拉取任
     * 务执行完一次消息拉取任务后，又将Pu llRequest 对象放入到pullReques tQueue ，第二个是
     * 在Rebalanccelmpl 中创建。Rebalanc巳Imp ！就是下节重点要介绍的消息队列负载机制，也就
     * 是PullRequest 对象真正创建的地方。
     *
     * PullMessageS 巳rvice 只有在拿到P ullRequest 对象时才会执行拉取任
     * 务，
     *
     * @param pullRequest
     * @param timeDelay
     */
    public void executePullRequestLater(final PullRequest pullRequest, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    PullMessageService.this.executePullRequestImmediately(pullRequest);
                }
            }, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    public void executePullRequestImmediately(final PullRequest pullRequest) {
        try {
            this.pullRequestQueue.put(pullRequest);
        } catch (InterruptedException e) {
            log.error("executePullRequestImmediately pullRequestQueue.put", e);
        }
    }

    public void executeTaskLater(final Runnable r, final long timeDelay) {
        if (!isStopped()) {
            this.scheduledExecutorService.schedule(r, timeDelay, TimeUnit.MILLISECONDS);
        } else {
            log.warn("PullMessageServiceScheduledThread has shutdown");
        }
    }

    public ScheduledExecutorService getScheduledExecutorService() {
        return scheduledExecutorService;
    }


    /**
     * 根据消费组名从MQClientlnstance 中获取消费者内部实现类MQConsumerlnner ，令人
     * 意外的是这里将consumer 强制转换为DefaultMQPushConsumerlmpl ，也就是PullMessageService
     * ，该线程只为PUSH 模式服务， 那拉模式如何拉取消息呢？其实细想也不难理解，
     * PULL 模式， RocketMQ 只需要提供拉取消息API 即可， 具体由应用程序显示调用拉取API 。
     * @param pullRequest
     */
    private void pullMessage(final PullRequest pullRequest) {
        final MQConsumerInner consumer = this.mQClientFactory.selectConsumer(pullRequest.getConsumerGroup());
        if (consumer != null) {
            DefaultMQPushConsumerImpl impl = (DefaultMQPushConsumerImpl) consumer;
            impl.pullMessage(pullRequest);
        } else {
            log.warn("No matched consumer for the PullRequest {}, drop it", pullRequest);
        }
    }

    /**
     * 消息拉取服务线程， run 方法是其核心逻辑
     */
    @Override
    public void run() {
        log.info(this.getServiceName() + " service started");


        // while ( !this . isStopped （） ）这是一种通用的设计技巧， stopped 声明为v olatil巳， 每执
        //行一次业务逻辑检测一下其运行状态，可以通过其他线程将stopped 设置为true 从而停止该
        //线程。
        while (!this.isStopped()) {
            try {
                // 从pullRequestQueue 中获取一个Pul!R巳quest 消息拉取任务，如果pul!RequestQueue
                //为空，则线程将阻塞，直到有拉取任务被放入。
                PullRequest pullRequest = this.pullRequestQueue.take();
                // 调用pullMessage 方法进行消息拉取。
                this.pullMessage(pullRequest);
            } catch (InterruptedException ignored) {
            } catch (Exception e) {
                log.error("Pull Message Service Run Method exception", e);
            }
        }

        log.info(this.getServiceName() + " service end");
    }

    @Override
    public void shutdown(boolean interrupt) {
        super.shutdown(interrupt);
        ThreadUtils.shutdownGracefully(this.scheduledExecutorService, 1000, TimeUnit.MILLISECONDS);
    }

    @Override
    public String getServiceName() {
        return PullMessageService.class.getSimpleName();
    }

}
