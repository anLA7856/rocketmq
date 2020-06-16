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
package org.apache.rocketmq.store.index;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.List;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.store.MappedFile;

/**
 * consumequeue是rocketmq专门为订阅构建的索引，提高主题与消息队列检索速度。
 * 另外，rocketmq引入了hash索引机制为消息建立索引。
 *
 * IndexFile总共包括IndexHeader、Hash槽，Hash条目
 * 1. IndexHeader头部，包含该IndexFile的统计信息
 * 2. Hash槽，一个IndexFile包含500万个Hash槽，每个Hash槽存储的落在Hash槽的Hashcode最新Index的索引，每个Hash槽占4个字节
 * 3. Index条目列表，默认一个索引文件包含2000万个条目，每个Index条目结构如下
 *  - hashcode，key的hashcode
 *  - phyoffset，消息对应物理偏移量
 *  - timedif：该消息存储时间与第一条消息的时间戳差值，小于0无效
 *  - preIndexNo：该条目前一条记录的Index索引，当出现冲突时，构建的链表索引
 *
 *
 */
public class IndexFile {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.STORE_LOGGER_NAME);
    private static int hashSlotSize = 4;
    private static int indexSize = 20;
    private static int invalidIndex = 0;
    private final int hashSlotNum;
    private final int indexNum;
    private final MappedFile mappedFile;
    private final FileChannel fileChannel;
    private final MappedByteBuffer mappedByteBuffer;
    private final IndexHeader indexHeader;

    public IndexFile(final String fileName, final int hashSlotNum, final int indexNum,
        final long endPhyOffset, final long endTimestamp) throws IOException {
        int fileTotalSize =
            IndexHeader.INDEX_HEADER_SIZE + (hashSlotNum * hashSlotSize) + (indexNum * indexSize);
        this.mappedFile = new MappedFile(fileName, fileTotalSize);
        this.fileChannel = this.mappedFile.getFileChannel();
        this.mappedByteBuffer = this.mappedFile.getMappedByteBuffer();
        this.hashSlotNum = hashSlotNum;
        this.indexNum = indexNum;

        ByteBuffer byteBuffer = this.mappedByteBuffer.slice();
        this.indexHeader = new IndexHeader(byteBuffer);

        if (endPhyOffset > 0) {
            this.indexHeader.setBeginPhyOffset(endPhyOffset);
            this.indexHeader.setEndPhyOffset(endPhyOffset);
        }

        if (endTimestamp > 0) {
            this.indexHeader.setBeginTimestamp(endTimestamp);
            this.indexHeader.setEndTimestamp(endTimestamp);
        }
    }

    public String getFileName() {
        return this.mappedFile.getFileName();
    }

    public void load() {
        this.indexHeader.load();
    }

    public void flush() {
        long beginTime = System.currentTimeMillis();
        if (this.mappedFile.hold()) {
            this.indexHeader.updateByteBuffer();
            this.mappedByteBuffer.force();
            this.mappedFile.release();
            log.info("flush index file elapsed time(ms) " + (System.currentTimeMillis() - beginTime));
        }
    }

    public boolean isWriteFull() {
        return this.indexHeader.getIndexCount() >= this.indexNum;
    }

    public boolean destroy(final long intervalForcibly) {
        return this.mappedFile.destroy(intervalForcibly);
    }

    /**
     * 如何将 Map<String(消息索引key),long phyOffset(物理偏移量)> 存入索引文件
     * @param key
     * @param phyOffset
     * @param storeTimestamp
     * @return
     */
    public boolean putKey(final String key, final long phyOffset, final long storeTimestamp) {
        if (this.indexHeader.getIndexCount() < this.indexNum) {  // 判断索引文件是否已满
            int keyHash = indexKeyHashMethod(key);  // 根据key算hashcode
            /**
             * 然后keyHash 对
             * hash 槽数量取余定位到hasbcod巳对应的hash 槽下标， hashcode 对应的hash 槽的物理地址
             * 为IndexHeader 头部（40 字节）加上下标乘以每个hash 槽的大小（4 字节） 。
             */
            int slotPos = keyHash % this.hashSlotNum;
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;

            FileLock fileLock = null;

            try {

                // fileLock = this.fileChannel.lock(absSlotPos, hashSlotSize,
                // false);
                /**
                 * 如果hash 槽存储的数据小于0 或大于当前索引文
                 * 件中的索引条目格式， 则将slotValue 设置为0 。
                 */
                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);
                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()) {
                    slotValue = invalidIndex;
                }

                // 计算待存储消息的时间戳与第一条消息时间戳的差值，并转换成秒。
                long timeDiff = storeTimestamp - this.indexHeader.getBeginTimestamp();
                timeDiff = timeDiff / 1000;

                if (this.indexHeader.getBeginTimestamp() <= 0) {
                    timeDiff = 0;
                } else if (timeDiff > Integer.MAX_VALUE) {
                    timeDiff = Integer.MAX_VALUE;
                } else if (timeDiff < 0) {
                    timeDiff = 0;
                }
                // ：将条目信息存储在IndexFile 中
                /**
                 * 计算新添加条目的起始物理偏移量， 等于头部字节长度＋ hash 槽数量＊ 单个hash
                 * 槽大小（4 个字节）＋当前Index 条目个数＊单个Index 条目大小（20 个字节） 。
                 *
                 *
                 *
                 *
                 * 这里是Hash 冲突链式解决方案的关键实现， Hash 槽中存储的是该Hash Code 所对应的
                 * 最新的Index 条目的下标，新的Index 条目的最后4 个字节存储该Hash Code 上一个条目的
                 * Index 下标。如果Hash 槽中存储的值为0 或大于当前lndexFile 最大条目数或小于－ 1 ， 表
                 * 示该Hash 槽当前并没有与之对应的Index 条目。值得关注的是， IndexFile 条目中存储的不
                 * 是消息索引key 而是消息属性key 的HashCode ，在根据key 查找时需要根据消息物理偏移
                 * 量找到消息进而再验证消息key 的值，之所以只存储Hash Code 而不存储具体的key ， 是为
                 * 了将Index 条目设计为定长结构，才能方便地检索与定位条目。
                 */
                int absIndexPos =
                    IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize
                        + this.indexHeader.getIndexCount() * indexSize;
                // hash cod巳、消息物理偏移量、消息存储时间戳与索引文件时间戳、当前
                // Hash 槽的值存入MappedByteBuffer 中。
                this.mappedByteBuffer.putInt(absIndexPos, keyHash);
                this.mappedByteBuffer.putLong(absIndexPos + 4, phyOffset);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8, (int) timeDiff);
                this.mappedByteBuffer.putInt(absIndexPos + 4 + 8 + 4, slotValue);   // hash冲突解决方案

                this.mappedByteBuffer.putInt(absSlotPos, this.indexHeader.getIndexCount());

                // 更新文件索引头信息。如果当前文件只包含一个条目，更新b eginPhyOffset 与
                // beginTimestamp 、更新endP yhOffset 、endTimestamp 、当前文件使用索引条目等信息。

                if (this.indexHeader.getIndexCount() <= 1) {
                    this.indexHeader.setBeginPhyOffset(phyOffset);
                    this.indexHeader.setBeginTimestamp(storeTimestamp);
                }

                this.indexHeader.incHashSlotCount();
                this.indexHeader.incIndexCount();
                this.indexHeader.setEndPhyOffset(phyOffset);
                this.indexHeader.setEndTimestamp(storeTimestamp);

                return true;
            } catch (Exception e) {
                log.error("putKey exception, Key: " + key + " KeyHashCode: " + key.hashCode(), e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }
            }
        } else {
            log.warn("Over index file capacity: index count = " + this.indexHeader.getIndexCount()
                + "; index max num = " + this.indexNum);
        }

        return false;
    }

    public int indexKeyHashMethod(final String key) {
        int keyHash = key.hashCode();
        int keyHashPositive = Math.abs(keyHash);
        if (keyHashPositive < 0)
            keyHashPositive = 0;
        return keyHashPositive;
    }

    public long getBeginTimestamp() {
        return this.indexHeader.getBeginTimestamp();
    }

    public long getEndTimestamp() {
        return this.indexHeader.getEndTimestamp();
    }

    public long getEndPhyOffset() {
        return this.indexHeader.getEndPhyOffset();
    }

    public boolean isTimeMatched(final long begin, final long end) {
        boolean result = begin < this.indexHeader.getBeginTimestamp() && end > this.indexHeader.getEndTimestamp();
        result = result || (begin >= this.indexHeader.getBeginTimestamp() && begin <= this.indexHeader.getEndTimestamp());
        result = result || (end >= this.indexHeader.getBeginTimestamp() && end <= this.indexHeader.getEndTimestamp());
        return result;
    }

    /**
     * 根据索引key 查找消息的实现方法为：
     * @param phyOffsets 查找到的消息物理偏移量。
     * @param key 索引key
     * @param maxNum 本次查找最大消息条数
     * @param begin 开始时间戳
     * @param end 结束时间戳。
     * @param lock
     */
    public void selectPhyOffset(final List<Long> phyOffsets, final String key, final int maxNum,
        final long begin, final long end, boolean lock) {
        if (this.mappedFile.hold()) {
            int keyHash = indexKeyHashMethod(key);  // 算出hashcode
            int slotPos = keyHash % this.hashSlotNum;  // 取余定位到hashcode对应hash槽下表
            int absSlotPos = IndexHeader.INDEX_HEADER_SIZE + slotPos * hashSlotSize;  // 根据 hash槽和 INDEX_HEADER_SIZE 获取hash槽对应物理地址

            FileLock fileLock = null;
            try {
                if (lock) {
                    // fileLock = this.fileChannel.lock(absSlotPos,
                    // hashSlotSize, true);
                }

                int slotValue = this.mappedByteBuffer.getInt(absSlotPos);
                // if (fileLock != null) {
                // fileLock.release();
                // fileLock = null;
                // }

                if (slotValue <= invalidIndex || slotValue > this.indexHeader.getIndexCount()
                    || this.indexHeader.getIndexCount() <= 1) {
                    // 如果对应的Hash 槽中存储的数据小于1 或大于当前索引条目个数则表示该
                    //Hash Code 没有对应的条目， 直接返回
                } else {
                    for (int nextIndexToRead = slotValue; ; ) {
                        if (phyOffsets.size() >= maxNum) {
                            break;
                        }

                        int absIndexPos =
                            IndexHeader.INDEX_HEADER_SIZE + this.hashSlotNum * hashSlotSize
                                + nextIndexToRead * indexSize;

                        int keyHashRead = this.mappedByteBuffer.getInt(absIndexPos);
                        long phyOffsetRead = this.mappedByteBuffer.getLong(absIndexPos + 4);

                        long timeDiff = (long) this.mappedByteBuffer.getInt(absIndexPos + 4 + 8);
                        int prevIndexRead = this.mappedByteBuffer.getInt(absIndexPos + 4 + 8 + 4);

                        if (timeDiff < 0) {
                            break;
                        }

                        timeDiff *= 1000L;

                        long timeRead = this.indexHeader.getBeginTimestamp() + timeDiff;
                        boolean timeMatched = (timeRead >= begin) && (timeRead <= end);

                        if (keyHash == keyHashRead && timeMatched) {
                            phyOffsets.add(phyOffsetRead);
                        }

                        if (prevIndexRead <= invalidIndex
                            || prevIndexRead > this.indexHeader.getIndexCount()
                            || prevIndexRead == nextIndexToRead || timeRead < begin) {
                            break;
                        }

                        nextIndexToRead = prevIndexRead;
                    }
                }
            } catch (Exception e) {
                log.error("selectPhyOffset exception ", e);
            } finally {
                if (fileLock != null) {
                    try {
                        fileLock.release();
                    } catch (IOException e) {
                        log.error("Failed to release the lock", e);
                    }
                }

                this.mappedFile.release();
            }
        }
    }
}
