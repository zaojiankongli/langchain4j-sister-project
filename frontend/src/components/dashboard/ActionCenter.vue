<template>
  <div class="action-center-container">
    <div class="page-header">
      <div class="title-group">
        <h3 class="greeting-title" :class="greetingClass">{{ greeting }}</h3>
      </div>
      <div class="header-actions">
        <button class="refresh-btn" @click="loadRecommendations" :disabled="loading">
          <span class="refresh-icon" :class="{ spinning: loading }">⟳</span>
          刷新
        </button>
      </div>
    </div>

    <div class="category-bar">
      <div
          v-for="cat in categories"
          :key="cat.id"
          class="cat-item"
          :class="{ active: currentCat === cat.id }"
          @click="currentCat = cat.id"
      >
        {{ cat.name }}
      </div>

      <div class="sort-info">
        <span class="info-icon">✦</span>
        <span class="info-text">今日匹配度优先</span>
      </div>
    </div>

    <div v-if="loading" class="state-container">
      <div class="cyber-loading-bar"></div>
      <p>正在检索记忆深处的资源...</p>
    </div>

    <div v-else-if="error" class="state-container error">
      <div class="error-icon">⚠</div>
      <p class="error-text">{{ error }}</p>
      <button class="action-btn" @click="loadRecommendations">重新连接</button>
    </div>

    <div v-else-if="filteredResources.length === 0" class="state-container empty">
      <div class="empty-icon">📭</div>
      <p class="empty-text">当前分类暂无内容</p>
    </div>

    <div v-else class="resource-grid">
      <div
          v-for="(item, index) in filteredResources"
          :key="item.id"
          class="resource-card"
          :style="{ animationDelay: `${index * 0.05}s` }"
      >
        <div class="card-glass-glow"></div>

        <div class="card-cover">
          <img v-if="item.cover" :src="item.cover" :alt="item.title">
          <div v-else class="cover-placeholder" :class="item.resourceType || item.type">
            <span class="placeholder-icon">{{ getPlaceholderIcon(item.resourceType || item.type) }}</span>
          </div>

          <div class="type-tag">{{ getResourceTypeLabel(item.resourceType || item.type) }}</div>

          <div v-if="item.relevanceScore" class="relevance-badge">
            匹配度 {{ (item.relevanceScore * 100).toFixed(0) }}%
          </div>
        </div>

        <div class="card-info">
          <h3 class="res-title">{{ item.title }}</h3>
          <p class="res-desc">{{ item.description }}</p>
          <div class="card-footer">
            <div class="meta-info">
              <span class="source-tag" v-if="item.source">{{ item.source }}</span>
            </div>

            <a v-if="item.url"
               :href="item.url"
               target="_blank"
               rel="noopener noreferrer"
               class="action-btn"
               :class="{ 'is-clicked': item.isClicked }"
               @click="handleResourceClick(item)">
              {{ item.isClicked ? '✓ 已阅' : '查看详情' }}
            </a>
            <button v-else
                    class="action-btn"
                    :class="{ 'is-clicked': item.isClicked }"
                    @click="handleResourceClick(item)">
              {{ item.isClicked ? '✓ 已阅' : '查看详情' }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import request from '@/utils/request';
import { API } from '@/config/api';

// --- 状态与分类 ---
const loading = ref(false);
const error = ref(null);
const resources = ref([]);
const currentCat = ref('all');

// 融合新版标签样式与旧版分类逻辑
const categories = [
  { id: 'all', name: '全部推送' },
  { id: 'document', name: '档案资料' },
  { id: 'video', name: '影像记录' },
  { id: 'article', name: '深度阅读' }
];

// --- 问候语逻辑 ---
const greetingData = computed(() => {
  const hour = new Date().getHours();
  if (hour >= 5 && hour < 12) {
    return { text: '早安，开始新的一天', class: 'greeting-morning' };
  } else if (hour >= 12 && hour < 14) {
    return { text: '午安，稍作休息吧', class: 'greeting-noon' };
  } else if (hour >= 14 && hour < 18) {
    return { text: '下午好，继续保持专注', class: 'greeting-afternoon' };
  } else if (hour >= 18 && hour < 22) {
    return { text: '晚上好，沉淀今日的思绪', class: 'greeting-evening' };
  } else {
    return { text: '夜深了，注意休息哦', class: 'greeting-night' };
  }
});

const greeting = computed(() => greetingData.value.text);
const greetingClass = computed(() => greetingData.value.class);

// --- 核心业务逻辑  ---
async function loadRecommendations() {
  loading.value = true;
  error.value = null;
  try {
    const result = await request({
      url: API.AI_RECOM,
      method: 'get'
    });
    if (result.code === 200) {
      const data = result.data;
      resources.value = Array.isArray(data) ? data : (data?.list || data?.records || []);
    } else {
      error.value = result.message || '数据同步未完成';
    }
  } catch (e) {
    console.error('加载资源推荐失败:', e);
    error.value = '神经链接断开，请检查网络';
  } finally {
    loading.value = false;
  }
}

async function handleResourceClick(resource) {
  try {
    await request({
      url: API.AI_RECOM_CLICK,
      method: 'post',
      params: { id: resource.id }
    });
    resource.isClicked = true;
  } catch (e) {
    console.error('记录点击失败:', e);
  }
}

// --- 视觉映射工具函数 ---
function getResourceTypeLabel(type) {
  switch (type) {
    case 'video': return '影像';
    case 'article': return '长文';
    case 'document': return '档案';
    default: return '未知';
  }
}

function getPlaceholderIcon(type) {
  switch (type) {
    case 'video': return '▶';
    case 'article': return '✎';
    case 'document': return '🗂';
    default: return '📄';
  }
}

// --- 计算属性：过滤资源 ---
const filteredResources = computed(() => {
  if (!resources.value || resources.value.length === 0) return [];
  if (currentCat.value === 'all') return resources.value;

  return resources.value.filter(item => {
    const itemType = item?.resourceType || item?.type;
    return itemType === currentCat.value;
  });
});

onMounted(() => {
  loadRecommendations();
});
</script>

<style scoped>
/* 容器基础配置 */
.action-center-container {
  padding: 20px 40px;
  color: #fff;
  height: 100%;
  overflow-y: auto;
  box-sizing: border-box;
}
.action-center-container::-webkit-scrollbar { width: 0; }

/* --- 头部及问候 (融合旧版) --- */
.page-header {
  margin-bottom: 30px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.greeting-title {
  margin: 0;
  font-size: 22px;
  font-weight: 500;
  letter-spacing: 1px;
}
.greeting-morning { color: #ffb7b2; }
.greeting-noon { color: #ffc99e; }
.greeting-afternoon { color: #e0c3c3; }
.greeting-evening { color: #dda5dd; }
.greeting-night { color: #b8a8a8; }

.refresh-btn {
  background: rgba(255, 255, 255, 0.05);
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.2);
  padding: 8px 18px;
  border-radius: 20px;
  font-size: 13px;
  cursor: pointer;
  transition: all 0.3s ease;
  display: flex;
  align-items: center;
  gap: 6px;
}
.refresh-btn:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.15);
  border-color: rgba(255, 255, 255, 0.4);
}
.refresh-btn:disabled { opacity: 0.5; cursor: not-allowed; }
.refresh-icon.spinning { animation: spin 1s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }

/* --- 分类栏 (新版设计) --- */
.category-bar {
  display: flex;
  align-items: center;
  gap: 25px;
  margin-bottom: 30px;
  position: relative;
}
.cat-item {
  font-size: 15px;
  opacity: 0.5;
  cursor: pointer;
  transition: all 0.3s ease;
  position: relative;
  padding-bottom: 5px;
  font-weight: 400;
}
.cat-item::after {
  content: '';
  position: absolute;
  bottom: 0; left: 0; width: 0; height: 2px;
  background: #fff;
  transition: width 0.3s cubic-bezier(0.165, 0.84, 0.44, 1);
  border-radius: 2px;
}
.cat-item:hover, .cat-item.active { opacity: 1; }
.cat-item.active::after { width: 100%; }

.sort-info {
  margin-left: auto;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.5);
  display: flex;
  align-items: center;
  gap: 5px;
}

/* --- 状态组件 (融合版) --- */
.state-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 40vh;
  gap: 20px;
  color: rgba(255, 255, 255, 0.6);
  font-size: 14px;
}
.cyber-loading-bar {
  width: 50px; height: 50px;
  border: 2px solid rgba(255, 255, 255, 0.1);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 1s cubic-bezier(0.68, -0.55, 0.265, 1.55) infinite;
}
.error-icon, .empty-icon { font-size: 42px; opacity: 0.8; }
.error-text { color: #ffb7b2; }

/* --- 瀑布流与卡片 (新版设计) --- */
.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 25px;
  padding-bottom: 80px;
}
.resource-card {
  position: relative;
  border-radius: 14px;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.08);
  transition: all 0.4s cubic-bezier(0.165, 0.84, 0.44, 1);
  opacity: 0;
  animation: cardFadeIn 0.6s forwards;
}
.resource-card:hover {
  transform: translateY(-8px);
  background: rgba(255, 255, 255, 0.1);
  border-color: rgba(255, 255, 255, 0.2);
  box-shadow: 0 15px 35px rgba(0, 0, 0, 0.2);
}

/* 卡片封面与占位 */
.card-cover {
  position: relative;
  width: 100%;
  height: 160px;
  overflow: hidden;
}
.card-cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.8s ease;
}
.resource-card:hover .card-cover img { transform: scale(1.08); }

/* 温和自然的渐变占位符，避免色彩刺眼 */
.cover-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  font-size: 40px; color: rgba(255,255,255,0.2);
  transition: transform 0.8s ease;
}
.cover-placeholder.document { background: linear-gradient(135deg, #2c3e50, #3498db); }
.cover-placeholder.video { background: linear-gradient(135deg, #512b48, #e74c3c); }
.cover-placeholder.article { background: linear-gradient(135deg, #1e3c40, #1abc9c); }
.resource-card:hover .cover-placeholder { transform: scale(1.05); }

/* 悬浮标签 */
.type-tag {
  position: absolute;
  top: 12px; right: 12px;
  padding: 4px 10px;
  background: rgba(0, 0, 0, 0.4);
  backdrop-filter: blur(8px);
  border-radius: 6px;
  font-size: 11px;
  color: #fff;
}
.relevance-badge {
  position: absolute;
  bottom: 12px; left: 12px;
  padding: 4px 8px;
  background: rgba(255, 255, 255, 0.85);
  color: #333;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
  box-shadow: 0 2px 10px rgba(0,0,0,0.2);
}

/* 卡片文本与操作 */
.card-info { padding: 18px; }
.res-title {
  font-size: 16px;
  font-weight: 500;
  margin: 0 0 8px 0;
  line-height: 1.4;
}
.res-desc {
  font-size: 13px;
  opacity: 0.6;
  line-height: 1.6;
  height: 42px;
  margin: 0;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}
.card-footer {
  margin-top: 18px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.source-tag {
  font-size: 11px;
  color: rgba(255, 255, 255, 0.4);
  background: rgba(255, 255, 255, 0.05);
  padding: 3px 8px;
  border-radius: 4px;
}

.action-btn {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #fff;
  padding: 6px 14px;
  border-radius: 20px;
  font-size: 12px;
  cursor: pointer;
  text-decoration: none;
  transition: all 0.3s ease;
  display: inline-block;
}
.action-btn:hover {
  background: #fff;
  color: #222;
  border-color: #fff;
}
.action-btn.is-clicked {
  border-color: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.4);
}
.action-btn.is-clicked:hover {
  background: transparent;
  color: #fff;
}

@keyframes cardFadeIn {
  from { opacity: 0; transform: translateY(20px); }
  to { opacity: 1; transform: translateY(0); }
}

.page-header {
  animation: slideDownFade 0.6s cubic-bezier(0.165, 0.84, 0.44, 1) forwards;
}

.category-bar {
  opacity: 0;
  animation: slideDownFade 0.6s cubic-bezier(0.165, 0.84, 0.44, 1) 0.1s forwards;
}

@keyframes slideDownFade {
  from { opacity: 0; transform: translateY(-15px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>