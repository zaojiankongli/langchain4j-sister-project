<template>
  <div class="subspace-memory">
    <div class="page-header animate-sitewide-enter">
      <div class="title-group">
        <h3 class="greeting-title">记忆回路与情绪锚点</h3>
      </div>
    </div>

    <div class="filter-bar animate-sitewide-enter delay-100">
      <div class="filter-tabs">
        <span :class="{ active: currentTab === 'anchor' }" @click="currentTab = 'anchor'">情绪锚点</span>
        <span :class="{ active: currentTab === 'journal' }" @click="currentTab = 'journal'">心路日记</span>
      </div>

      <div class="right-controls">
        <div class="filter-wrapper">
          <div class="filter-btn" @click.stop="isFilterOpen = !isFilterOpen">
            <span class="filter-icon">◓</span>
            <span class="filter-label">[ {{ filterText }} ]</span>
          </div>
          <transition name="fade-slide">
            <div v-if="isFilterOpen" class="filter-dropdown">
              <div
                  v-for="opt in filterOptions"
                  :key="opt"
                  @click.stop="selectFilter(opt)"
                  class="filter-item"
                  :class="{ active: filterText === opt }"
              >
                {{ opt }}
              </div>
            </div>
          </transition>
        </div>
        <div class="sort-info">
          <span class="info-icon">✧</span>
          <span class="info-text">
            {{ currentTab === 'journal' ? '已记录 ' + journalList.length + ' 篇' : '已解析 ' + anchorList.length + ' 个核心事件' }}
          </span>
        </div>
      </div>
    </div>

    <div class="content-area scroll-container">
      <transition name="page-switch" mode="out-in">
        <div class="timeline-container" :key="currentTab">
          <div class="timeline-line"></div>

          <transition-group name="memory-list" tag="div" class="memory-wrapper" appear>
            <div
                v-for="(item, index) in displayList"
                :key="item.id"
                class="memory-card"
                @click="openMemory(item)"
                :style="{ '--delay': index * 0.08 + 's' }"
            >
              <div class="memory-date">{{ formatCardDate(item) }}</div>

              <div v-if="currentTab === 'journal'" class="memory-content-mini journal-style">
                <div v-if="item.imageUrl" class="mini-cover" :style="{ backgroundImage: `url(${item.imageUrl})` }"></div>
                <div class="journal-text-area">
                  <div class="mini-quote">{{ item.title || '无主题日记' }}</div>
                  <div class="mini-desc">{{ item.content }}</div>
                  <div class="mini-footer">
                    <span class="mood-badge">{{ item.mood }}</span>
                    <span class="more-hint">阅读日记 ◓</span>
                  </div>
                </div>
              </div>

              <div v-else class="memory-content-mini anchor-style">
                <div class="anchor-header">
                  <span class="anchor-title">[{{ item.endType === 'POSITIVE' ? '正向锚点' : '负向锚点' }}] {{ item.eventTitle }}</span>
                  <span class="anchor-duration">{{ Math.floor(item.durationSeconds / 60) }} min</span>
                </div>

                <div class="data-grid">
                  <div class="data-cell">
                    <span class="label">愉悦度 (Pleasure)</span>
                    <span class="value" :class="getDeltaColor(item.deltaPleasure)">
                      {{ item.deltaPleasure > 0 ? '+' : ''}}{{ item.deltaPleasure }}
                      <span class="peak">(Peak: {{ item.peakPleasure }})</span>
                    </span>
                  </div>
                  <div class="data-cell">
                    <span class="label">唤醒度 (Arousal)</span>
                    <span class="value" :class="getDeltaColor(item.deltaArousal)">
                      {{ item.deltaArousal > 0 ? '+' : ''}}{{ item.deltaArousal }}
                      <span class="peak">(Peak: {{ item.peakArousal }})</span>
                    </span>
                  </div>
                </div>

                <div class="mini-desc">{{ item.summary }}</div>
                <div class="mini-footer">
                  <span class="trait-tags">{{ item.highlightTraits }}</span>
                  <span class="more-hint">解析底层逻辑 ◓</span>
                </div>
              </div>

            </div>
          </transition-group>
        </div>
      </transition>
    </div>

    <teleport to="body">
      <transition name="modal-fade">
        <div v-if="currentMemory" class="memory-modal-overlay" @click.self="closeMemory">
          <div class="memory-modal-window">
            <div class="modal-close-btn" @click="closeMemory">✕</div>

            <div class="modal-header">
              <span class="modal-date">{{ formatModalDate(currentMemory) }}</span>
              <div class="modal-tag" v-if="currentTab === 'anchor'">情绪锚点</div>
              <div class="modal-tag" v-if="currentTab === 'journal'">日记</div>
            </div>

            <div class="modal-body scrollable">

              <template v-if="currentTab === 'journal'">
                <h2 class="modal-quote">{{ currentMemory.title }}</h2>
                <div class="modal-divider"></div>
                <p class="modal-article">{{ currentMemory.content }}</p>
              </template>

              <template v-if="currentTab === 'anchor'">
                <h2 class="modal-quote">{{ currentMemory.eventTitle }}</h2>

                <div class="anchor-modal-metrics">
                  <div class="metric-block">
                    <div class="m-title">Pleasure Shift</div>
                    <div class="m-route">{{ currentMemory.startPleasure }} → {{ currentMemory.endPleasure }}</div>
                    <div class="m-delta" :class="getDeltaColor(currentMemory.deltaPleasure)">Δ {{ currentMemory.deltaPleasure }}</div>
                  </div>
                  <div class="metric-block">
                    <div class="m-title">Arousal Shift</div>
                    <div class="m-route">{{ currentMemory.startArousal }} → {{ currentMemory.endArousal }}</div>
                    <div class="m-delta" :class="getDeltaColor(currentMemory.deltaArousal)">Δ {{ currentMemory.deltaArousal }}</div>
                  </div>
                </div>

                <div class="modal-divider"></div>
                <p class="modal-article">{{ currentMemory.summary }}</p>

                <div class="ai-reflection-box">
                  <div class="box-title">_AI_REFLECTION_LOG</div>
                  <p class="box-content">{{ currentMemory.aiReflection }}</p>
                  <div class="box-meta">
                    触发原因: {{ currentMemory.triggerReason }} <br/>
                    结束逻辑: {{ currentMemory.endReason }}
                  </div>
                </div>
              </template>

            </div>

            <div class="modal-footer">
              <div class="modal-mood">
                <template v-if="currentTab === 'journal'">
                  心情感应: <span>{{ currentMemory.mood }}</span>
                </template>
                <template v-if="currentTab === 'anchor'">
                  特质演算: <span>{{ currentMemory.highlightTraits }}</span>
                </template>
              </div>
            </div>
          </div>
        </div>
      </transition>
    </teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { API } from '@/config/api';
import request from '@/utils/request';

/* ---------------- 基础状态 ---------------- */
const currentTab = ref('journal');
const isFilterOpen = ref(false);
const filterText = ref('最近');
const filterOptions = ['最近', '2026.04', '更早'];

const currentMemory = ref(null);
const openMemory = (item) => { currentMemory.value = item; };
const closeMemory = () => { currentMemory.value = null; };

const journalList = ref([]);
const anchorList = ref([]);
const loading = ref(false);

/* ---------------- 交互 ---------------- */
const selectFilter = (option) => {
  filterText.value = option;
  isFilterOpen.value = false;
  fetchData(); //  恢复筛选触发请求
};

const closeFilter = () => { isFilterOpen.value = false; };

onMounted(() => {
  window.addEventListener('click', closeFilter);
  fetchData(); //  初始化加载
});

onUnmounted(() => {
  window.removeEventListener('click', closeFilter);
});

/* 切换 tab 自动请求 */
watch(currentTab, () => {
  fetchData();
});

/* ---------------- 数据请求 ---------------- */
const fetchData = async () => {
  loading.value = true;

  try {
    const params = {
      page: 1,
      size: 50,
      filter: filterText.value
    };

    let res;

    if (currentTab.value === 'anchor') {
      res = await request.get(API.ANCHOR_LIST, { params });

      anchorList.value = res?.data?.data || res?.data || [];

    } else {
      res = await request.get(API.MEMORY_LIST, { params });

      journalList.value = res?.data?.data || res?.data || [];
    }

  } catch (e) {
    console.error('Failed to fetch memory data:', e);
  } finally {
    loading.value = false;
  }
};

/* ---------------- 数据绑定 ---------------- */
const displayList = computed(() =>
    currentTab.value === 'journal'
        ? journalList.value
        : anchorList.value
);

/* ---------------- 工具函数 ---------------- */
const formatCardDate = (item) => {
  if (item.memory_date) return item.memory_date;
  if (item.start_time) return item.start_time.split(' ')[0];
  return '';
};

const formatModalDate = (item) => {
  if (item.memory_date) return item.memory_date;

  if (item.start_time && item.end_time) {
    return `${item.start_time} to ${item.end_time.split(' ')[1]}`;
  }

  return '';
};

const getDeltaColor = (val) => {
  if (val > 0) return 'text-positive';
  if (val < 0) return 'text-negative';
  return 'text-neutral';
};
</script>

<style scoped>
.subspace-memory {
  height: 100%; display: flex; flex-direction: column;
  padding: 20px 40px 0 40px; color: #fff; overflow: hidden;
}

/* 基础 UI */
.page-header { margin-bottom: 10px; flex-shrink: 0; }
.greeting-title { font-size: 20px; font-weight: 600; margin: 0; }
.sync-info { font-size: 12px; opacity: 0.5; margin-top: 4px; }

.filter-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 12px 0; border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  flex-shrink: 0; position: relative; z-index: 100;
}
.filter-tabs { display: flex; gap: 24px; }
.filter-tabs span { font-size: 14px; color: rgba(255, 255, 255, 0.4); cursor: pointer; padding: 8px 0; position: relative; transition: 0.3s; }
.filter-tabs span.active { color: #fff; }
.filter-tabs span::after { content: ''; position: absolute; bottom: -1px; left: 0; width: 100%; height: 2px; background: #fff; transform: scaleX(0); transition: 0.3s cubic-bezier(0.4, 0, 0.2, 1); }
.filter-tabs span.active::after { transform: scaleX(1); }

.right-controls { display: flex; align-items: center; gap: 16px; }
.filter-wrapper { position: relative; }
.filter-btn { font-size: 12px; color: #7c9cff; cursor: pointer; display: flex; align-items: center; gap: 6px; transition: 0.3s; }
.filter-btn:hover { text-shadow: 0 0 10px rgba(124, 156, 255, 0.6); }

.filter-dropdown { position: absolute; top: calc(100% + 10px); right: 0; background: rgba(30, 32, 45, 0.9); backdrop-filter: blur(15px); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 8px; padding: 6px; min-width: 110px; z-index: 1000; }
.filter-item { padding: 10px 14px; font-size: 12px; color: rgba(255, 255, 255, 0.5); cursor: pointer; text-align: right; border-radius: 4px; }
.filter-item:hover { color: #fff; background: rgba(255, 255, 255, 0.08); }
.filter-item.active { color: #7c9cff; background: rgba(124, 156, 255, 0.1); }

.sort-info { font-size: 11px; color: rgba(255, 255, 255, 0.4); background: rgba(255, 255, 255, 0.05); padding: 4px 14px; border-radius: 20px; }

/* 滚动区域与时间轴 */
.content-area { flex: 1; overflow-y: auto; position: relative; padding: 20px 0 120px 0; }
.content-area::-webkit-scrollbar { width: 4px; }
.content-area::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.1); border-radius: 10px; }

.timeline-container { position: relative; padding-left: 30px; }
.timeline-line { position: absolute; left: 0; top: 0; bottom: 0; width: 1px; background: linear-gradient(to bottom, transparent, rgba(255, 255, 255, 0.1), transparent); }

/* 卡片通用 */
.memory-card { position: relative; margin-bottom: 28px; cursor: pointer; }
.memory-card::before { content: ''; position: absolute; left: -34px; top: 8px; width: 8px; height: 8px; border-radius: 50%; background: #fff; box-shadow: 0 0 8px rgba(255, 255, 255, 0.5); z-index: 1; }
.memory-date { font-size: 12px; opacity: 0.7; margin-bottom: 10px; font-family: monospace; }
.memory-content-mini { background: rgba(255, 255, 255, 0.02); border-radius: 12px; border: 1px solid rgba(255, 255, 255, 0.05); transition: all 0.3s ease; overflow: hidden; }
.memory-card:hover .memory-content-mini { background: rgba(255, 255, 255, 0.05); transform: translateX(5px); }

/* --- 日记卡片 16:9 缩略图 --- */
.mini-cover {
  margin: -1px -1px 0 -1px; /* 抵消 border */
  aspect-ratio: 16 / 9; /* 强制 16:9 */
  background-size: cover;
  background-position: center;
  border-bottom: 1px solid rgba(255,255,255,0.05);
}
.journal-text-area { padding: 18px; }
.mini-quote { font-size: 15px; color: #fff; font-weight: 500; margin-bottom: 10px; }
.mini-desc { font-size: 13px; color: rgba(255, 255, 255, 0.6); line-height: 1.6; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
.mood-badge { font-size: 10px; padding: 2px 8px; border-radius: 4px; border: 1px solid rgba(255,255,255,0.1); color: rgba(255,255,255,0.6); }
.mini-footer { margin-top: 15px; display: flex; justify-content: space-between; align-items: center; }
.more-hint { font-size: 11px; color: #7c9cff; opacity: 0.8; letter-spacing: 1px; }

/* 情绪锚点卡片 */
.anchor-style { padding: 18px; border-left: 2px solid #7c9cff; border-radius: 4px 12px 12px 4px; }
.anchor-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; border-bottom: 1px dashed rgba(255,255,255,0.1); padding-bottom: 10px; }
.anchor-title { font-size: 14px; font-weight: 600; color: #fff; }
.anchor-duration { font-size: 11px; font-family: monospace; color: rgba(255,255,255,0.4); }
.data-grid { display: flex; gap: 20px; margin-bottom: 15px; background: rgba(0,0,0,0.2); padding: 10px; border-radius: 6px; }
.data-cell { display: flex; flex-direction: column; }
.data-cell .label { font-size: 10px; color: rgba(255,255,255,0.4); margin-bottom: 4px; text-transform: uppercase; }
.data-cell .value { font-size: 14px; font-family: monospace; font-weight: 600; }
.data-cell .peak { font-size: 10px; opacity: 0.5; margin-left: 4px; font-weight: normal; }
.text-positive { color: #4ade80; } .text-negative { color: #f87171; } .text-neutral { color: #94a3b8; }
.trait-tags { font-size: 11px; color: #a5b9ff; background: rgba(124, 156, 255, 0.1); padding: 2px 8px; border-radius: 4px; }

/* --- 统一高级灰弹窗设计 --- */
.memory-modal-overlay {
  position: fixed; top: 0; left: 0; width: 100vw; height: 100vh;
  background: rgba(10, 10, 12, 0.5); /* 整体背景稍微压暗 */
  backdrop-filter: blur(15px);
  display: flex; align-items: center; justify-content: center; z-index: 9999;
}

.memory-modal-window {
  width: 620px; max-width: 90vw; max-height: 85vh;
  /* 统一为贴合背景的高级灰色玻璃态 */
  background: rgba(35, 38, 45, 0.95);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 20px; display: flex; flex-direction: column; padding: 40px;
  box-shadow: 0 40px 100px rgba(0, 0, 0, 0.5); overflow: hidden;
}

.modal-close-btn { position: absolute; top: 20px; right: 25px; color: rgba(255, 255, 255, 0.4); cursor: pointer; font-size: 18px; z-index: 10; transition: 0.3s; }
.modal-close-btn:hover { color: #fff; transform: rotate(90deg); }
.modal-date { color: #9ab4ff; font-family: monospace; font-size: 14px; margin-bottom: 10px; display: block; }
.modal-tag { display: inline-block; font-size: 10px; padding: 2px 8px; background: rgba(124, 156, 255, 0.15); color: #7c9cff; border-radius: 4px; margin-bottom: 20px; font-family: monospace; }

/* --- 弹窗 16:9 无损大图 --- */
.modal-hero-image {
  width: calc(100% + 80px); /* 抵消 padding */
  margin: -40px -40px 20px -40px;
  aspect-ratio: 16 / 9; /* 强制 16:9 无损比例 */
  object-fit: cover;
  display: block;
}

.modal-quote { font-size: 22px; line-height: 1.4; margin-bottom: 20px; font-weight: 600; color: #fff; }
.modal-divider { width: 45px; height: 3px; background: #7c9cff; margin-bottom: 30px; border-radius: 2px; }
.modal-article { font-size: 15px; line-height: 1.9; color: rgba(255, 255, 255, 0.85); white-space: pre-line; }

.anchor-modal-metrics { display: flex; gap: 30px; margin-bottom: 30px; padding: 20px; background: rgba(0,0,0,0.2); border-radius: 12px; }
.metric-block .m-title { font-size: 11px; color: rgba(255,255,255,0.4); text-transform: uppercase; margin-bottom: 5px; }
.metric-block .m-route { font-size: 14px; font-family: monospace; color: #fff; margin-bottom: 5px; }
.metric-block .m-delta { font-size: 16px; font-family: monospace; font-weight: bold; }

.ai-reflection-box { margin-top: 30px; border-left: 3px solid #7c9cff; padding-left: 20px; }
.box-title { font-family: monospace; font-size: 12px; color: #7c9cff; margin-bottom: 10px; letter-spacing: 1px; }
.box-content { font-size: 14px; line-height: 1.8; color: rgba(255, 255, 255, 0.7); font-style: italic; white-space: pre-line; }
.box-meta { margin-top: 15px; font-size: 11px; color: rgba(255,255,255,0.3); line-height: 1.6; }

.modal-footer { margin-top: 40px; padding-top: 20px; border-top: 1px solid rgba(255, 255, 255, 0.05); display: flex; justify-content: space-between; font-size: 12px; color: rgba(255, 255, 255, 0.4); }
.modal-mood span { color: #7c9cff; font-weight: 600; }

.scrollable { overflow-y: auto; flex: 1; padding-right: 10px; }
.scrollable::-webkit-scrollbar { width: 4px; }
.scrollable::-webkit-scrollbar-thumb { background: rgba(255, 255, 255, 0.2); border-radius: 10px; }

/* 动画库 */
.fade-slide-enter-active, .fade-slide-leave-active { transition: all 0.3s; }
.fade-slide-enter-from, .fade-slide-leave-to { opacity: 0; transform: translateY(-10px); }
.memory-list-enter-active { transition: all 0.6s ease; transition-delay: var(--delay); }
.memory-list-enter-from { opacity: 0; transform: translateX(-15px); }
.page-switch-enter-active, .page-switch-leave-active { transition: all 0.4s ease; }
.page-switch-enter-from { opacity: 0; transform: translateY(5px); }
.page-switch-leave-to { opacity: 0; transform: translateY(-5px); }
.modal-fade-enter-active, .modal-fade-leave-active { transition: all 0.4s cubic-bezier(0.16, 1, 0.3, 1); }
.modal-fade-enter-from, .modal-fade-leave-to { opacity: 0; }
.modal-fade-enter-from .memory-modal-window { transform: scale(0.96) translateY(20px); }
.animate-sitewide-enter { animation: fadeInDown 0.8s ease forwards; }
@keyframes fadeInDown { from { opacity: 0; transform: translateY(-10px); } to { opacity: 1; transform: translateY(0); } }
</style>