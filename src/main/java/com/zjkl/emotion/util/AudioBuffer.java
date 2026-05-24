package com.zjkl.emotion.util;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 音频缓冲管理器
 */
@Slf4j
public class AudioBuffer {
    
    /**
     * 音频分片队列
     */
    private final BlockingQueue<byte[]> audioQueue = new LinkedBlockingQueue<>();
    
    /**
     * 缓冲阈值（毫秒）
     * 缓存够这么多毫秒的音频后开始播放
     */
    private final int bufferThresholdMs;
    
    /**
     * 是否已开始播放
     */
    private final AtomicBoolean playbackStarted = new AtomicBoolean(false);

    /**
     * 合成是否完成
     */
    private final AtomicBoolean synthesisCompleted = new AtomicBoolean(false);

    /**
     * 累计音频大小（字节）- 使用原子变量保证线程安全
     */
    private final AtomicLong totalBytes = new AtomicLong(0);
    
    /**
     * 锁和条件变量
     */
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition canPlay = lock.newCondition();
    
    /**
     * 音频采样率
     */
    private static final int SAMPLE_RATE = 48000;  // 48kHz (与 TTS 输出格式 PCM_48000HZ_MONO_16BIT 一致)
    private static final int BITS_PER_SAMPLE = 16;  // 16bit
    private static final int CHANNELS = 1;  // 单声道
    
    /**
     * 构造函数
     * 
     * @param bufferThresholdMs 缓冲阈值（毫秒），建议 300-800ms
     */
    public AudioBuffer(int bufferThresholdMs) {
        this.bufferThresholdMs = bufferThresholdMs;
        log.info("音频缓冲初始化：阈值={}ms", bufferThresholdMs);
    }
    
    /**
     * 默认构造函数（500ms 缓冲）
     */
    public AudioBuffer() {
        this(500);
    }
    
    /**
     * 添加音频分片
     * 
     * @param audioData 音频数据（PCM 格式）
     */
    public void addAudio(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return;
        }
        
        try {
            audioQueue.offer(audioData);
            totalBytes.addAndGet(audioData.length);
            log.debug("音频分片入队：size={}, 累计={} bytes", audioData.length, totalBytes.get());
        } catch (Exception e) {
            log.error("音频分片入队失败", e);
        }
    }
    
    /**
     * 检查是否可以开始播放
     *
     */
    public boolean shouldStartPlayback() {
        if (playbackStarted.get()) {
            return false;  // 已经开始了
        }
        
        // 估算当前缓存的音频时长（毫秒）
        int bufferedMs = estimateDurationMs();
        
        if (bufferedMs >= bufferThresholdMs) {
            log.info("缓冲达标：{}ms >= {}ms，可以开始播放", bufferedMs, bufferThresholdMs);
            playbackStarted.set(true);
            return true;
        }
        
        // 如果合成已完成，直接开始（有多少播多少）
        if (synthesisCompleted.get() && bufferedMs > 0) {
            log.info("合成完成，缓冲{}ms，开始播放", bufferedMs);
            playbackStarted.set(true);
            return true;
        }
        
        return false;
    }
    
    /**
     * 等待缓冲达标
     *
     */
    public void awaitPlaybackReady() throws InterruptedException {
        lock.lock();
        try {
            while (!shouldStartPlayback()) {
                if (synthesisCompleted.get()) {
                    break;  // 合成完成，不再等待
                }
                canPlay.await(50, TimeUnit.MILLISECONDS);  // 等待最多 50ms
            }
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * 获取下一个音频分片（阻塞）
     *
     */
    public byte[] getNextAudio(long timeoutMs) {
        try {
            byte[] audio = audioQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (audio != null) {
                log.debug("音频分片出队：size={}", audio.length);
            }
            return audio;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("音频分片出队被中断");
            return null;
        }
    }
    
    /**
     * 获取下一个音频分片（非阻塞）
     *
     */
    public byte[] pollAudio() {
        byte[] audio = audioQueue.poll();
        if (audio != null) {
            log.debug("音频分片出队（非阻塞）：size={}", audio.length);
        }
        return audio;
    }
    
    /**
     * 标记合成完成
     */
    public void markSynthesisCompleted() {
        synthesisCompleted.set(true);
        // 唤醒等待中的线程
        lock.lock();
        try {
            canPlay.signal();
        } finally {
            lock.unlock();
        }
        log.info("TTS 合成完成，总音频大小={} bytes", totalBytes.get());
    }
    
    /**
     * 检查是否还有音频数据
     *
     */
    public boolean hasMoreAudio() {
        return !audioQueue.isEmpty() || !synthesisCompleted.get();
    }
    
    /**
     * 估算当前缓存的音频时长（毫秒）
     */
    public int estimateDurationMs() {
        long bytesPerMs = (long) SAMPLE_RATE * BITS_PER_SAMPLE * CHANNELS / 8 / 1000;
        if (bytesPerMs == 0) return 0;
        return (int)(totalBytes.get() / bytesPerMs);
    }
    
    /**
     * 获取队列中剩余的音频分片数量
     * 
     * @return 数量
     */
    public int getQueueSize() {
        return audioQueue.size();
    }
    
    /**
     * 清空缓冲区
     */
    public void clear() {
        audioQueue.clear();
        totalBytes.set(0);
        playbackStarted.set(false);
        synthesisCompleted.set(false);
        lock.lock();
        try {
            canPlay.signalAll();
        } finally {
            lock.unlock();
        }
        log.info("音频缓冲区已清空");
    }
    
    /**
     * 是否已开始播放
     * 
     * @return true=已开始
     */
    public boolean isPlaybackStarted() {
        return playbackStarted.get();
    }
    
    /**
     * 合成是否完成
     * 
     * @return true=已完成
     */
    public boolean isSynthesisCompleted() {
        return synthesisCompleted.get();
    }
}
