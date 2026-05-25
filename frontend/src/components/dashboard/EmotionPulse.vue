<template>
  <div class="subspace-pulse">
    <div class="core-personality-display stagger-1">
      <div class="meta-label">EMOTION_HISTORY</div>
      <h1 class="personality-text">
        情绪记录 <span class="connector">·</span> 近24小时
      </h1>
      <div class="pulse-line-horizontal"></div>
    </div>

    <!-- 加载状态 -->
    <div v-if="loading" class="evolution-loading">
      <div class="loading-pulse"><div class="loading-dot"></div><div class="loading-dot"></div><div class="loading-dot"></div></div>
      <p class="loading-text">正在同步情绪数据...</p>
    </div>
    <div v-else-if="error" class="evolution-error">
      <p class="error-icon">⚠</p>
      <p class="error-text">{{ error }}</p>
      <button class="retry-btn" @click="fetchHistory">重新加载</button>
    </div>
    <div v-else-if="emotionRecords.length === 0" class="evolution-empty">
      <p class="empty-icon">~</p>
      <p class="empty-text">还没有情绪记录呢<br/>去和你的AI陪伴聊聊天吧</p>
    </div>
    <div v-else class="evolution-flow">
      <div class="flow-guide-line"></div>

      <div v-for="(record, index) in emotionRecords"
           :key="record.id"
           class="flow-node"
           :class="'stagger-' + ((index % 4) + 2)"
           @click="handleNodeClick(record)">

        <div class="trigger-side">
          <div class="status-monitor-box">
            <div class="stat-row">
              <div class="stat-info">
                <span class="stat-label">P.Pleasure</span>
                <span class="stat-num">{{ record.pleasure }}</span>
              </div>
              <div class="stat-track bi-directional">
                <div class="center-mark"></div>
                <div class="stat-fill" :style="getBiDirectionalStyle(record.pleasure, 'P')"></div>
              </div>
            </div>

            <div class="stat-row">
              <div class="stat-info">
                <span class="stat-label">A.Arousal</span>
                <span class="stat-num">{{ record.arousal }}</span>
              </div>
              <div class="stat-track">
                <div class="stat-fill arousal-glow" :style="{ width: (record.arousal * 100) + '%' }"></div>
              </div>
            </div>

            <div class="stat-row">
              <div class="stat-info">
                <span class="stat-label">D.Dominance</span>
                <span class="stat-num">{{ record.dominance }}</span>
              </div>
              <div class="stat-track bi-directional">
                <div class="center-mark"></div>
                <div class="stat-fill" :style="getBiDirectionalStyle(record.dominance, 'D')"></div>
              </div>
            </div>
          </div>
        </div>

        <div class="node-center">
          <div class="node-dot"></div>
          <div class="node-ripple"></div>
        </div>

        <div class="result-side">
          <div class="result-tag">MOOD_STATE</div>
          <p class="result-desc">{{ record.mood_description }}</p>
          <span class="timestamp">{{ formatTime(record.created_at) }}</span>
        </div>
      </div>
    </div>

    <div class="ambient-note stagger-5">
      <p>"她正在感知你的频率，并调整自己的振幅。"</p>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'
import { API } from '@/config/api'

const emotionRecords = ref([])
const loading = ref(false)
const error = ref('')

// 获取历史数据
const fetchHistory = async () => {
  const userId = getUserId()
  if (!userId) return
  loading.value = true
  error.value = ''
  try {
    const res = await request.get(API.EMOTION_HISTORY(userId))
    if (res.code === 200 && res.data) {
      emotionRecords.value = res.data
    } else {
      error.value = '情绪数据格式异常'
    }
  } catch (e) {
    error.value = e?.response?.data?.message || e?.message || '加载情绪数据失败'
  } finally {
    loading.value = false
  }
}

/**
 * 计算双向进度条样式 (P 和 D 专用)
 * 逻辑：数值为正向右长，数值为负向左长，以 50% 为中心。
 */
const getBiDirectionalStyle = (val, type) => {
  const num = parseFloat(val) || 0;
  const absVal = Math.abs(num);
  const isPositive = num >= 0;

  // 颜色映射：P 正向偏暖蓝，D 正向偏青绿，负向统一偏淡红
  let activeColor = isPositive ? 'rgba(124, 156, 255, 0.8)' : 'rgba(248, 113, 113, 0.8)';
  if (type === 'D' && isPositive) activeColor = 'rgba(110, 220, 190, 0.8)';

  return {
    width: `${(absVal / 1.0) * 50}%`, // 相对于半轴的比例
    left: isPositive ? '50%' : 'auto',
    right: !isPositive ? '50%' : 'auto',
    background: isPositive
        ? `linear-gradient(90deg, rgba(255,255,255,0.1), ${activeColor})`
        : `linear-gradient(-90deg, rgba(255,255,255,0.1), ${activeColor})`,
    boxShadow: `0 0 8px ${activeColor.replace('0.8', '0.3')}`
  };
};

const formatTime = (timeStr) => {
  if (!timeStr) return '';
  return timeStr.split(' ')[1] || timeStr; // 仅展示时间部分增加简洁感
};

const handleNodeClick = (record) => {
  // Record detail click — reserved for future detail modal
};

onMounted(() => {
  fetchHistory()
})
</script>

<style scoped>
/* 保持原有布局结构 */
.subspace-pulse {
  padding: 60px 40px;
  color: #fff;
  height: 100%;
  display: flex;
  flex-direction: column;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 隐藏滚动条但保留功能 */
.subspace-pulse::-webkit-scrollbar { width: 0; }

.core-personality-display {
  margin-bottom: 60px;
  text-align: center;
  opacity: 0;
}

.personality-text {
  font-size: 32px;
  font-weight: 200;
  letter-spacing: 4px;
  margin: 10px 0;
  filter: drop-shadow(0 0 10px rgba(255,255,255,0.3));
}

.pulse-line-horizontal {
  width: 100px;
  height: 1px;
  background: linear-gradient(90deg, transparent, #fff, transparent);
  margin: 20px auto;
}

.evolution-flow {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20px 0;
}

.flow-guide-line {
  position: absolute;
  top: 0; left: 50%;
  width: 1px;
  background: linear-gradient(to bottom, rgba(255,255,255,0.5), rgba(255,255,255,0));
  transform: translateX(-0.5px);
  animation: line-extend 2s ease-in-out forwards;
}

.flow-node {
  display: grid;
  grid-template-columns: 1fr 100px 1fr; /* 扩宽中心区域方便 PAD 展示 */
  align-items: center;
  width: 100%;
  max-width: 900px;
  margin-bottom: 80px;
  opacity: 0;
  cursor: pointer;
}

/* --- 核心优化：PAD 监测仪样式 --- */
.trigger-side {
  display: flex;
  justify-content: flex-end;
  padding-right: 20px;
}

.status-monitor-box {
  width: 180px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.stat-row {
  display: flex;
  flex-direction: column;
  gap: 5px;
}

.stat-info {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.stat-label {
  font-size: 9px;
  font-family: 'JetBrains Mono', monospace;
  color: rgba(255, 255, 255, 0.4);
  letter-spacing: 1px;
}

.stat-num {
  font-size: 10px;
  font-family: monospace;
  color: rgba(255, 255, 255, 0.8);
}

.stat-track {
  height: 2px;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 1px;
  position: relative;
}

.center-mark {
  position: absolute;
  left: 50%; top: -2px;
  width: 1px; height: 6px;
  background: rgba(255, 255, 255, 0.3);
  z-index: 2;
}

.stat-fill {
  position: absolute;
  height: 100%;
  transition: all 1.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.arousal-glow {
  background: linear-gradient(90deg, rgba(255,255,255,0.1), #fff);
  box-shadow: 0 0 10px rgba(255, 255, 255, 0.4);
}

/* --- 节点中心与右侧 --- */
.node-center { display: flex; justify-content: center; position: relative; }
.node-dot { width: 6px; height: 6px; background: #fff; border-radius: 50%; box-shadow: 0 0 10px #fff; z-index: 3; }
.node-ripple {
  position: absolute;
  width: 24px; height: 24px;
  border: 1px solid rgba(255,255,255,0.4);
  border-radius: 50%;
  animation: ripple-out 2s infinite;
}

.result-side { padding-left: 20px; }
.result-tag { font-size: 10px; color: #7c9cff; margin-bottom: 6px; letter-spacing: 2px; }
.result-desc { font-size: 15px; font-weight: 300; line-height: 1.5; color: rgba(255,255,255,0.9); }
.timestamp { font-size: 10px; opacity: 0.4; display: block; margin-top: 8px; font-family: monospace; }

/* ── 加载 / 错误 / 空状态 ── */
.evolution-loading, .evolution-error, .evolution-empty {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  min-height: 200px; color: rgba(255,255,255,0.5);
}
.loading-pulse { display: flex; gap: 8px; margin-bottom: 16px; }
.loading-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: rgba(255,255,255,0.3);
  animation: dot-pulse 1.4s ease-in-out infinite;
}
.loading-dot:nth-child(2) { animation-delay: 0.2s; }
.loading-dot:nth-child(3) { animation-delay: 0.4s; }
.loading-text { font-size: 13px; letter-spacing: 1px; }
.error-icon { font-size: 28px; margin-bottom: 12px; }
.error-text { font-size: 13px; color: rgba(248, 113, 113, 0.8); margin-bottom: 16px; }
.retry-btn {
  background: rgba(255,255,255,0.08); border: 1px solid rgba(255,255,255,0.15);
  color: rgba(255,255,255,0.7); padding: 6px 20px; border-radius: 20px;
  cursor: pointer; font-size: 12px; transition: all 0.3s;
}
.retry-btn:hover { background: rgba(255,255,255,0.15); color: #fff; }
.empty-icon { font-size: 32px; margin-bottom: 12px; opacity: 0.4; }
.empty-text { font-size: 13px; text-align: center; line-height: 1.8; }

@keyframes dot-pulse {
  0%, 80%, 100% { transform: scale(0.6); opacity: 0.4; }
  40% { transform: scale(1); opacity: 1; }
}

/* 动画部分 */
@keyframes grow-in {
  0% { opacity: 0; filter: blur(10px); transform: translateY(20px) scale(0.95); }
  100% { opacity: 1; filter: blur(0); transform: translateY(0) scale(1); }
}

@keyframes line-extend {
  0% { height: 0; opacity: 0; }
  100% { height: 100%; opacity: 1; }
}

@keyframes ripple-out {
  0% { transform: scale(1); opacity: 0.8; }
  100% { transform: scale(3.5); opacity: 0; }
}

.stagger-1 { animation: grow-in 1s ease-out forwards; animation-delay: 0.2s; }
.stagger-2 { animation: grow-in 1s ease-out forwards; animation-delay: 0.5s; }
.stagger-3 { animation: grow-in 1s ease-out forwards; animation-delay: 0.8s; }
.stagger-4 { animation: grow-in 1s ease-out forwards; animation-delay: 1.1s; }
.stagger-5 { animation: grow-in 1s ease-out forwards; animation-delay: 1.4s; }

.ambient-note {
  margin-top: 40px;
  text-align: center;
  font-style: italic;
  font-size: 13px;
  opacity: 0;
  padding-bottom: 40px;
}
</style>