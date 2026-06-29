<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import GlassPanel from '../glass/GlassPanel.vue'
import type { MemoryEntry } from '../../composables/usePetMemory'
import type { MemoryDateFilter } from '../../composables/usePetMemory'
import type { MemoryCategory, MemoryGalleryItem, MemoryGalleryTab } from '../../types/memory'
import { getMoodEmoji } from '../../utils/moodEmoji'
import { formatDateCN } from '../../utils/formatTime'

const props = defineProps<{
  entries: MemoryEntry[]
  isLoading: boolean
  isLoadingMore: boolean
  hasMore: boolean
  isOpen: boolean
  searchResults: string[]
  isSearching: boolean
  searchQuery: string
  activeFilter: MemoryDateFilter
  galleryItems: MemoryGalleryItem[]
  unlockedGalleryCount: number
  totalGalleryCount: number
}>()

const emit = defineEmits<{
  close: []
  loadMore: []
  search: [query: string]
  clearSearch: []
  filterChange: [filter: MemoryDateFilter]
  gallerySelect: [galleryKey: string]
}>()

const localQuery = ref('')
const activeTab = ref<MemoryGalleryTab>('gallery')
const activeGalleryCategory = ref<'all' | MemoryCategory>('all')
const selectedGalleryMemoryId = ref<string | null>(null)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const isSearchingActive = computed(() => props.searchQuery.trim().length > 0)

const sortedEntries = computed(() => {
  return [...props.entries].sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  )
})

const filterOptions: { label: string; value: MemoryDateFilter }[] = [
  { label: '全部', value: '' },
  { label: '最近', value: '最近' },
  { label: '更早', value: '更早' },
]

const panelTabs: { label: string; value: MemoryGalleryTab }[] = [
  { label: '回忆图鉴', value: 'gallery' },
  { label: '记忆记录', value: 'records' },
]

const galleryCategoryOptions: { label: string; value: 'all' | MemoryCategory }[] = [
  { label: '全部', value: 'all' },
  { label: '日常', value: 'daily' },
  { label: '情绪', value: 'emotion' },
  { label: '剧情', value: 'story' },
  { label: 'CG', value: 'cg' },
]

const filteredGalleryItems = computed(() => {
  if (activeGalleryCategory.value === 'all') {
    return props.galleryItems
  }
  return props.galleryItems.filter((item) => item.definition.category === activeGalleryCategory.value)
})

const selectedGalleryMemory = computed(() => {
  if (!selectedGalleryMemoryId.value) {
    return filteredGalleryItems.value[0] ?? props.galleryItems[0] ?? null
  }
  return props.galleryItems.find((item) => item.definition.id === selectedGalleryMemoryId.value) ?? null
})

const galleryProgressLabel = computed(() => {
  return `${props.unlockedGalleryCount} / ${props.totalGalleryCount}`
})

function normalizeText(value?: string): string {
  return (value ?? '')
    .toLowerCase()
    .replace(/[\s，。、“”‘’！？!?,.:;；：·…（）()\-_/\\|\[\]{}]+/g, '')
}

function buildBigrams(value?: string): Set<string> {
  const normalized = normalizeText(value)
  const grams = new Set<string>()
  for (let index = 0; index < normalized.length - 1; index += 1) {
    grams.add(normalized.slice(index, index + 2))
  }
  if (normalized.length === 1) {
    grams.add(normalized)
  }
  return grams
}

function overlapScore(source: Set<string>, target: Set<string>): number {
  if (source.size === 0 || target.size === 0) {
    return 0
  }

  let matches = 0
  for (const token of source) {
    if (target.has(token)) {
      matches += 1
    }
  }
  return matches
}

function dayDiff(left: string, right: string): number | null {
  const leftTime = new Date(left).getTime()
  const rightTime = new Date(right).getTime()
  if (Number.isNaN(leftTime) || Number.isNaN(rightTime)) {
    return null
  }
  return Math.floor(Math.abs(leftTime - rightTime) / 86_400_000)
}

function galleryEntryScore(item: MemoryGalleryItem, entry: MemoryEntry): number {
  let score = 0
  const entryText = `${entry.title ?? ''}${entry.contentPreview ?? ''}${entry.content ?? ''}`
  const entryTextNormalized = normalizeText(entryText)
  const titleBigrams = buildBigrams(item.definition.title)
  const quoteBigrams = buildBigrams(item.definition.detailQuote)
  const excerptBigrams = buildBigrams(item.relatedExcerpt)
  const keywordBigrams = buildBigrams(item.definition.matchKeywords?.join(''))
  const entryBigrams = buildBigrams(entryText)

  const categoryWeights: Record<MemoryCategory, { text: number; keyword: number; mood: number; date: number; image: number }> = {
    daily: { text: 1.1, keyword: 1.2, mood: 0.7, date: 0.9, image: 0.6 },
    emotion: { text: 0.9, keyword: 1.1, mood: 1.6, date: 0.8, image: 0.8 },
    story: { text: 1.2, keyword: 1.4, mood: 0.8, date: 1, image: 1 },
    cg: { text: 1.3, keyword: 1.5, mood: 0.9, date: 1.1, image: 1.4 },
  }
  const weights = categoryWeights[item.definition.category]

  score += Math.min(10, overlapScore(titleBigrams, entryBigrams) * 2 * weights.text)
  score += Math.min(8, overlapScore(quoteBigrams, entryBigrams) * weights.text)
  score += Math.min(10, overlapScore(excerptBigrams, entryBigrams) * 2 * weights.text)
  score += Math.min(10, overlapScore(keywordBigrams, entryBigrams) * 1.5 * weights.keyword)

  const titleNormalized = normalizeText(item.definition.title)
  if (titleNormalized && entryTextNormalized.includes(titleNormalized)) {
    score += 8 * weights.text
  }

  const quoteNormalized = normalizeText(item.definition.detailQuote)
  if (quoteNormalized && entryTextNormalized.includes(quoteNormalized.slice(0, Math.min(quoteNormalized.length, 8)))) {
    score += 4 * weights.text
  }

  const excerptNormalized = normalizeText(item.relatedExcerpt)
  if (excerptNormalized && entryTextNormalized.includes(excerptNormalized.slice(0, Math.min(excerptNormalized.length, 10)))) {
    score += 6 * weights.text
  }

  for (const keyword of item.definition.matchKeywords ?? []) {
    const normalizedKeyword = normalizeText(keyword)
    if (normalizedKeyword && entryTextNormalized.includes(normalizedKeyword)) {
      score += 3 * weights.keyword
    }
  }

  if (item.relatedMood && entry.moodLabel && normalizeText(item.relatedMood) === normalizeText(entry.moodLabel)) {
    score += 5 * weights.mood
  }

  if (item.unlockedAt) {
    const diff = dayDiff(item.unlockedAt, entry.date)
    if (diff === 0) score += 8 * weights.date
    else if (diff === 1) score += 5 * weights.date
    else if (diff !== null && diff <= 3) score += 2 * weights.date
  }

  if (!!entry.imageUrl) {
    score += 2 * weights.image
  }

  return score
}


const galleryImageMap = computed<Record<string, string>>(() => {
  const unlockedItems = props.galleryItems
    .filter((item) => item.unlocked && !item.sourceImageUrl)
    .sort((a, b) => {
      const timeA = a.unlockedAt ? new Date(a.unlockedAt).getTime() : 0
      const timeB = b.unlockedAt ? new Date(b.unlockedAt).getTime() : 0
      return timeB - timeA
    })

  const imageEntries = [...props.entries]
    .filter((entry) => !!entry.imageUrl)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime())

  const mapped: Record<string, string> = {}
  const usedEntryIds = new Set<number>()

  for (const item of unlockedItems) {
    const rankedEntries = imageEntries
      .filter((entry) => !usedEntryIds.has(entry.id))
      .map((entry) => ({ entry, score: galleryEntryScore(item, entry) }))
      .sort((left, right) => right.score - left.score)

    const bestMatch = rankedEntries[0]?.entry
    const bestScore = rankedEntries[0]?.score ?? 0

    if (bestMatch?.imageUrl && bestScore > 0) {
      mapped[item.definition.id] = bestMatch.imageUrl
      usedEntryIds.add(bestMatch.id)
      continue
    }

    const fallbackEntry = imageEntries.find((entry) => !usedEntryIds.has(entry.id))
    if (fallbackEntry?.imageUrl) {
      mapped[item.definition.id] = fallbackEntry.imageUrl
      usedEntryIds.add(fallbackEntry.id)
    }
  }

  return mapped
})

function formatDate(isoString: string): string {
  return formatDateCN(isoString)
}

function formatPreview(text?: string): string {
  if (!text) return ''
  return text.length > 120 ? text.slice(0, 120) + '...' : text
}

function moodIcon(label?: string): string {
  if (!label) return ''
  return getMoodEmoji(label.toLowerCase())
}

function onSearchInput(): void {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    const q = localQuery.value.trim()
    if (q.length >= 2) {
      emit('search', q)
    } else if (q.length === 0) {
      emit('clearSearch')
    }
  }, 400)
}

function onFilterClick(filter: MemoryDateFilter): void {
  localQuery.value = ''
  emit('clearSearch')
  emit('filterChange', filter)
}

function onTabChange(tab: MemoryGalleryTab): void {
  activeTab.value = tab
}

function onGalleryCategoryChange(category: 'all' | MemoryCategory): void {
  activeGalleryCategory.value = category
  const first = (category === 'all'
    ? props.galleryItems[0]
    : props.galleryItems.find((item) => item.definition.category === category)) ?? null
  selectedGalleryMemoryId.value = first?.definition.id ?? null
}

function selectGalleryMemory(memoryId: string): void {
  selectedGalleryMemoryId.value = memoryId
  emit('gallerySelect', memoryId)
}

function rarityLabel(rarity: MemoryGalleryItem['definition']['rarity']): string {
  if (rarity === 'epic') return '珍藏'
  if (rarity === 'rare') return '稀有'
  return '日常'
}

function categoryLabel(category: MemoryCategory): string {
  if (category === 'emotion') return '情绪'
  if (category === 'story') return '剧情'
  if (category === 'cg') return 'CG'
  return '日常'
}

function coverThemeClass(item: MemoryGalleryItem): string {
  return `gallery-card-cover--${item.definition.coverTheme ?? 'dream'}`
}

function completionPercent(item: MemoryGalleryItem): string {
  return `${Math.round(item.completionRatio * 100)}%`
}

function formatUnlockedAt(value?: string): string {
  return value ? formatDateCN(value) : '尚未解锁'
}


function galleryImageSrc(item: MemoryGalleryItem): string {
  return item.sourceImageUrl || galleryImageMap.value[item.definition.id] || ''
}

// Clear search when panel closes
watch(() => props.isOpen, (open) => {
  if (!open) {
    localQuery.value = ''
    if (searchTimer) clearTimeout(searchTimer)
    return
  }

  if (!selectedGalleryMemoryId.value && props.galleryItems.length > 0) {
    selectedGalleryMemoryId.value = props.galleryItems[0].definition.id
    emit('gallerySelect', selectedGalleryMemoryId.value)
  }
})

watch(() => props.galleryItems, (items) => {
  if (items.length === 0) {
    selectedGalleryMemoryId.value = null
    return
  }

  if (!selectedGalleryMemoryId.value || !items.some((item) => item.definition.id === selectedGalleryMemoryId.value)) {
    selectedGalleryMemoryId.value = items[0].definition.id
    emit('gallerySelect', selectedGalleryMemoryId.value)
  }
})

/** Parse a search result string "yyyy.MM.dd — text" into structured parts. */
function parseSearchResult(raw: string): { date: string; text: string } {
  const dashIdx = raw.indexOf('—')
  if (dashIdx > 0 && dashIdx < 14) {
    return { date: raw.slice(0, dashIdx).trim(), text: raw.slice(dashIdx + 1).trim() }
  }
  return { date: '', text: raw }
}
</script>

<template>
  <Transition name="panel-slide">
    <GlassPanel
      v-if="isOpen"
      class="memory-panel"
      tag="aside"
      side="left"
      @backdrop-click="emit('close')"
      role="dialog"
      aria-label="记忆面板"
      tabindex="-1"
      @keydown.escape="emit('close')"
    >
      <div class="memory-panel-header">
        <h2 class="memory-panel-title">记忆</h2>
        <button
          class="memory-panel-close"
          type="button"
          aria-label="关闭"
          @click="emit('close')"
        >
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none" aria-hidden="true">
            <path d="M4 4L14 14M14 4L4 14" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </button>
      </div>

      <div class="memory-tabs" role="tablist" aria-label="记忆视图切换">
        <button
          v-for="tab in panelTabs"
          :key="tab.value"
          class="memory-tab"
          :class="{ 'memory-tab--active': activeTab === tab.value }"
          type="button"
          role="tab"
          :aria-selected="activeTab === tab.value"
          @click="onTabChange(tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>

      <template v-if="activeTab === 'records'">
      <!-- Search bar -->
      <div class="memory-search">
        <div class="memory-search-input-wrap">
          <svg class="memory-search-icon" width="14" height="14" viewBox="0 0 14 14" fill="none" aria-hidden="true">
            <circle cx="6" cy="6" r="4.5" stroke="currentColor" stroke-width="1.3" />
            <path d="M9.5 9.5L13 13" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
          </svg>
          <input
            v-model="localQuery"
            class="memory-search-input"
            type="text"
            placeholder="搜索记忆..."
            aria-label="搜索记忆"
            @input="onSearchInput"
          />
          <button
            v-if="localQuery"
            class="memory-search-clear"
            type="button"
            aria-label="清除搜索"
            @click="localQuery = ''; emit('clearSearch')"
          >
            <svg width="12" height="12" viewBox="0 0 12 12" fill="none" aria-hidden="true">
              <path d="M3 3l6 6M9 3l-6 6" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" />
            </svg>
          </button>
        </div>
      </div>

      <!-- Date filter chips (hidden during search) -->
      <div v-if="!isSearchingActive" class="memory-filters">
        <button
          v-for="opt in filterOptions"
          :key="opt.value"
          class="memory-filter-chip"
          :class="{ 'memory-filter-chip--active': activeFilter === opt.value }"
          type="button"
          @click="onFilterClick(opt.value)"
        >
          {{ opt.label }}
        </button>
      </div>
      </template>

      <div class="memory-panel-body">
        <template v-if="activeTab === 'gallery'">
          <div class="gallery-summary">
            <div>
              <p class="gallery-summary-label">已收集回忆</p>
              <p class="gallery-summary-value">{{ galleryProgressLabel }}</p>
            </div>
            <p class="gallery-summary-hint">把聊天、情绪、陪伴和小事件慢慢收进图鉴。</p>
          </div>

          <div class="gallery-filters">
            <button
              v-for="option in galleryCategoryOptions"
              :key="option.value"
              class="memory-filter-chip"
              :class="{ 'memory-filter-chip--active': activeGalleryCategory === option.value }"
              type="button"
              @click="onGalleryCategoryChange(option.value)"
            >
              {{ option.label }}
            </button>
          </div>

          <div v-if="filteredGalleryItems.length === 0" class="memory-empty">
            <p class="memory-empty-text">这一类回忆还没有条目</p>
            <p class="memory-empty-hint">换个分类看看，或者继续陪陪她。</p>
          </div>

          <template v-else>
            <div class="gallery-list">
              <button
                v-for="item in filteredGalleryItems"
                :key="item.definition.id"
                class="gallery-card"
                :class="{
                  'gallery-card--locked': !item.unlocked,
                  'gallery-card--selected': selectedGalleryMemory?.definition.id === item.definition.id,
                }"
                type="button"
                @click="selectGalleryMemory(item.definition.id)"
              >
                <div class="gallery-card-cover" :class="[coverThemeClass(item), `gallery-card-cover--${item.definition.rarity}`]">
                  <img
                    v-if="galleryImageSrc(item)"
                    :src="galleryImageSrc(item)"
                    :alt="item.unlocked ? `${item.definition.title} 收藏图` : '未解锁回忆封面'"
                    class="gallery-card-cover-image"
                    loading="lazy"
                  />
                  <span class="gallery-card-cover-badge">{{ item.definition.collectionLabel ?? categoryLabel(item.definition.category) }}</span>
                  <span class="gallery-card-cover-icon">{{ item.unlocked ? (item.definition.coverEmoji ?? '✦') : '？' }}</span>
                </div>
                <div class="gallery-card-body">
                  <div class="gallery-card-meta">
                    <span class="gallery-card-category">{{ categoryLabel(item.definition.category) }} · {{ rarityLabel(item.definition.rarity) }}</span>
                    <span class="gallery-card-progress">{{ completionPercent(item) }}</span>
                  </div>
                  <h3 class="gallery-card-title">{{ item.unlocked ? item.definition.title : '未解锁回忆' }}</h3>
                  <p class="gallery-card-text">{{ item.unlocked ? item.definition.description : item.definition.hint }}</p>
                </div>
              </button>
            </div>

            <section v-if="selectedGalleryMemory" class="gallery-detail" aria-label="回忆详情">
              <div class="gallery-detail-cover" :class="coverThemeClass(selectedGalleryMemory)">
                <img
                  v-if="galleryImageSrc(selectedGalleryMemory)"
                  :src="galleryImageSrc(selectedGalleryMemory)"
                  :alt="selectedGalleryMemory.unlocked ? `${selectedGalleryMemory.definition.title} 回忆照片` : '未解锁回忆照片占位'"
                  class="gallery-detail-cover-image"
                  loading="lazy"
                />
                <span class="gallery-detail-cover-badge">{{ selectedGalleryMemory.definition.collectionLabel ?? categoryLabel(selectedGalleryMemory.definition.category) }}</span>
                <span class="gallery-detail-cover-icon">
                  {{ selectedGalleryMemory.unlocked ? (selectedGalleryMemory.definition.coverEmoji ?? '✦') : '？' }}
                </span>
              </div>

              <div class="gallery-detail-header">
                <div>
                  <p class="gallery-detail-kicker">{{ rarityLabel(selectedGalleryMemory.definition.rarity) }}</p>
                  <h3 class="gallery-detail-title">
                    {{ selectedGalleryMemory.unlocked ? selectedGalleryMemory.definition.title : '未解锁回忆' }}
                  </h3>
                </div>
                <span class="gallery-detail-date">{{ formatUnlockedAt(selectedGalleryMemory.unlockedAt) }}</span>
              </div>

              <p class="gallery-detail-description">
                {{ selectedGalleryMemory.unlocked ? selectedGalleryMemory.definition.description : selectedGalleryMemory.definition.hint }}
              </p>

              <p v-if="selectedGalleryMemory.definition.detailQuote" class="gallery-detail-quote">
                {{ selectedGalleryMemory.definition.detailQuote }}
              </p>

              <p v-if="selectedGalleryMemory.relatedExcerpt" class="gallery-detail-excerpt">
                “{{ selectedGalleryMemory.relatedExcerpt }}”
              </p>

              <div v-if="selectedGalleryMemory.primaryConfidence !== null || (selectedGalleryMemory.matchedKeywords?.length ?? 0) > 0" class="gallery-detail-meta">
                <p v-if="selectedGalleryMemory.primaryConfidence !== null" class="gallery-detail-meta-line">
                  归档置信度：{{ Math.round((selectedGalleryMemory.primaryConfidence || 0) * 100) }}%
                </p>
                <p v-if="(selectedGalleryMemory.matchedKeywords?.length ?? 0) > 0" class="gallery-detail-meta-line">
                  命中关键词：{{ selectedGalleryMemory.matchedKeywords?.join(' · ') }}
                </p>
              </div>

              <div v-if="selectedGalleryMemory.sourceMemoryTitle || selectedGalleryMemory.sourceMemoryDate || selectedGalleryMemory.sourceMemoryContent" class="gallery-source-memory">
                <p class="gallery-source-memory-title">
                  {{ selectedGalleryMemory.sourceMemoryTitle || '来源记忆' }}
                  <span v-if="selectedGalleryMemory.sourceMemoryDate" class="gallery-source-memory-date">{{ selectedGalleryMemory.sourceMemoryDate }}</span>
                </p>
                <p v-if="selectedGalleryMemory.sourceMemoryContent" class="gallery-source-memory-content">
                  {{ selectedGalleryMemory.sourceMemoryContent }}
                </p>
              </div>

              <ul class="gallery-condition-list">
                <li
                  v-for="condition in selectedGalleryMemory.conditions"
                  :key="condition.label"
                  class="gallery-condition-item"
                  :class="{ 'gallery-condition-item--fulfilled': condition.fulfilled }"
                >
                  <span>{{ condition.label }}</span>
                  <strong>{{ Math.min(condition.current, condition.target) }}/{{ condition.target }}</strong>
                </li>
              </ul>
            </section>
          </template>
        </template>

        <template v-else>
        <!-- Searching state -->
        <template v-if="isSearchingActive">
          <div v-if="isSearching" class="memory-loading">
            <span class="spinner" aria-hidden="true" />
            <span>搜索中...</span>
          </div>

          <div v-else-if="searchResults.length === 0" class="memory-empty">
            <p class="memory-empty-text">没有找到相关记忆</p>
            <p class="memory-empty-hint">试试换个关键词?</p>
          </div>

          <div v-else class="memory-search-results">
            <p class="memory-search-hint">找到 {{ searchResults.length }} 条相关记忆</p>
            <div
              v-for="(raw, idx) in searchResults"
              :key="idx"
              class="memory-card memory-card--search"
            >
              <div class="memory-card-header">
                <span class="memory-date">{{ parseSearchResult(raw).date }}</span>
              </div>
              <p class="memory-preview">{{ parseSearchResult(raw).text }}</p>
            </div>
          </div>
        </template>

        <!-- Normal browsing state -->
        <template v-else>
          <!-- Loading -->
          <div v-if="isLoading" class="memory-loading">
            <span class="spinner" aria-hidden="true" />
            <span>加载中...</span>
          </div>

          <template v-else>
            <!-- Empty state -->
            <div v-if="entries.length === 0" class="memory-empty">
              <p class="memory-empty-text">暂无记忆</p>
              <p class="memory-empty-hint">记忆会在每日总结时自动创建</p>
            </div>

            <!-- Memory entries -->
            <div v-else class="memory-list">
              <div
                v-for="entry in sortedEntries"
                :key="entry.id"
                class="memory-card"
              >
                <div class="memory-card-header">
                  <span class="memory-date">{{ formatDate(entry.date) }}</span>
                  <span
                    v-if="entry.moodLabel"
                    class="memory-mood"
                    :title="entry.moodLabel"
                  >{{ moodIcon(entry.moodLabel) }}</span>
                </div>
                <h3 v-if="entry.title" class="memory-title">{{ entry.title }}</h3>
                <p v-if="entry.contentPreview || entry.content" class="memory-preview">
                  {{ formatPreview(entry.contentPreview ?? entry.content) }}
                </p>
                <img
                  v-if="entry.imageUrl"
                  :src="entry.imageUrl"
                  :alt="entry.title || '记忆图片'"
                  class="memory-image"
                  loading="lazy"
                />
              </div>

              <!-- Load more -->
              <div v-if="hasMore" class="memory-load-more">
                <button
                  class="load-more-btn"
                  type="button"
                  :disabled="isLoadingMore"
                  @click="emit('loadMore')"
                >
                  <template v-if="isLoadingMore">
                    <span class="spinner" aria-hidden="true" />
                    加载中...
                  </template>
                  <template v-else>
                    加载更多
                  </template>
                </button>
              </div>
            </div>
          </template>
        </template>
        </template>
      </div>
    </GlassPanel>
  </Transition>
</template>

<style scoped>
/* ── Panel container ── */

.memory-panel {
  position: fixed;
  left: 0;
  top: 0;
  width: 360px;
  height: 100%;
  display: flex;
  flex-direction: column;
  z-index: 101;
  overflow: hidden;
}

/* ── Header ── */

.memory-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-4) var(--space-5);
  border-bottom: 1px solid var(--color-border);
  flex-shrink: 0;
}

.memory-tabs {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4) var(--space-3);
}

.memory-tab {
  padding: var(--space-2) var(--space-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-text-muted);
  font: inherit;
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    background var(--duration-fast) ease;
}

.memory-tab--active {
  color: var(--color-action-text);
  background: var(--color-accent);
  border-color: var(--color-accent);
}

.memory-panel-title {
  margin: 0;
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  font-weight: 600;
  color: var(--color-heading);
  line-height: var(--line-height-tight);
  letter-spacing: var(--letter-spacing-tight);
}

.memory-panel-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 2rem;
  height: 2rem;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    background var(--duration-fast) ease;
}

.memory-panel-close:hover {
  color: var(--color-accent);
  background: var(--color-accent-soft);
}

.memory-panel-close:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

/* ── Search bar ── */

.memory-search {
  padding: var(--space-2) var(--space-4);
  flex-shrink: 0;
}

.memory-search-input-wrap {
  position: relative;
  display: flex;
  align-items: center;
}

.memory-search-icon {
  position: absolute;
  left: var(--space-3);
  color: var(--color-text-muted);
  pointer-events: none;
}

.memory-search-input {
  width: 100%;
  padding: var(--space-2) var(--space-3) var(--space-2) var(--space-7);
  font-size: var(--font-size-small);
  font-family: var(--font-body);
  color: var(--color-text);
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  outline: none;
  transition: border-color var(--duration-fast) ease;
}

.memory-search-input::placeholder {
  color: var(--color-text-muted);
  opacity: 0.7;
}

.memory-search-input:focus {
  border-color: var(--color-accent);
}

.memory-search-clear {
  position: absolute;
  right: var(--space-2);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 1.25rem;
  height: 1.25rem;
  padding: 0;
  background: transparent;
  border: none;
  border-radius: var(--radius-sm);
  color: var(--color-text-muted);
  cursor: pointer;
}

.memory-search-clear:hover {
  color: var(--color-accent);
}

/* ── Filter chips ── */

.memory-filters {
  display: flex;
  gap: var(--space-2);
  padding: 0 var(--space-4) var(--space-2);
  flex-shrink: 0;
}

.memory-filter-chip {
  padding: var(--space-1) var(--space-3);
  font-size: var(--font-size-caption);
  font-family: var(--font-body);
  color: var(--color-text-muted);
  background: transparent;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-pill);
  cursor: pointer;
  transition:
    color var(--duration-fast) ease,
    border-color var(--duration-fast) ease,
    background var(--duration-fast) ease;
}

.memory-filter-chip:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
}

.memory-filter-chip--active {
  color: var(--color-action-text);
  background: var(--color-accent);
  border-color: var(--color-accent);
}

/* ── Scrollable body ── */

.memory-panel-body {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-3);
}

.gallery-summary {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-4);
  border: 1px solid rgba(255, 143, 179, 0.16);
  border-radius: var(--radius-lg);
  background: linear-gradient(135deg, rgba(255, 248, 252, 0.88), rgba(255, 236, 244, 0.72));
  margin-bottom: var(--space-3);
}

.gallery-summary-label,
.gallery-summary-value,
.gallery-summary-hint,
.gallery-detail-kicker,
.gallery-detail-title,
.gallery-detail-description,
.gallery-detail-excerpt,
.gallery-card-title,
.gallery-card-text,
.gallery-card-category,
.gallery-card-progress,
.gallery-detail-date {
  margin: 0;
}

.gallery-summary-label,
.gallery-detail-kicker,
.gallery-card-category,
.gallery-card-progress,
.gallery-detail-date {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

.gallery-summary-value {
  font-family: var(--font-display);
  font-size: 1.45rem;
  color: var(--color-heading);
}

.gallery-summary-hint {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
}

.gallery-filters {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  margin-bottom: var(--space-3);
}

.gallery-list {
  display: grid;
  gap: var(--space-3);
}

.gallery-card {
  display: grid;
  grid-template-columns: 4rem minmax(0, 1fr);
  gap: var(--space-3);
  padding: var(--space-3);
  width: 100%;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-subtle);
  text-align: left;
  cursor: pointer;
  transition:
    border-color var(--duration-fast) ease,
    transform var(--duration-fast) ease,
    box-shadow var(--duration-fast) ease;
}

.gallery-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-1px);
}

.gallery-card--selected {
  border-color: var(--color-accent);
  box-shadow: 0 10px 28px rgba(255, 143, 179, 0.16);
}

.gallery-card--locked {
  opacity: 0.82;
}

.gallery-card-cover {
  display: flex;
  position: relative;
  align-items: center;
  justify-content: center;
  border-radius: var(--radius-md);
  min-height: 4rem;
  color: #fff;
  background: linear-gradient(135deg, #f6a5c0, #e879f9);
  overflow: hidden;
}

.gallery-card-cover-image,
.gallery-detail-cover-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.gallery-card-cover::after,
.gallery-detail-cover::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(180deg, rgba(15, 23, 42, 0.08), rgba(15, 23, 42, 0.38));
}

.gallery-card-cover--rare {
  background: linear-gradient(135deg, #8b5cf6, #ec4899);
}

.gallery-card-cover--epic {
  background: linear-gradient(135deg, #f59e0b, #ec4899);
}

.gallery-card-cover--sunrise {
  background: linear-gradient(135deg, rgba(255, 194, 168, 0.95), rgba(255, 143, 179, 0.85));
}

.gallery-card-cover--midnight {
  background: linear-gradient(135deg, rgba(67, 56, 202, 0.95), rgba(30, 41, 59, 0.95));
}

.gallery-card-cover--letter {
  background: linear-gradient(135deg, rgba(250, 245, 235, 0.94), rgba(226, 232, 240, 0.88));
  color: #7c5f4e;
}

.gallery-card-cover--rain {
  background: linear-gradient(135deg, rgba(71, 85, 105, 0.95), rgba(125, 211, 252, 0.72));
}

.gallery-card-cover--dream {
  background: linear-gradient(135deg, rgba(139, 92, 246, 0.95), rgba(244, 114, 182, 0.88));
}

.gallery-card-cover--song {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.92), rgba(16, 185, 129, 0.82));
}

.gallery-card-cover--blush {
  background: linear-gradient(135deg, rgba(251, 207, 232, 0.98), rgba(244, 114, 182, 0.82));
}

.gallery-card-cover-icon {
  font-size: 1.35rem;
  position: relative;
  z-index: 1;
}

.gallery-card-cover-badge,
.gallery-detail-cover-badge {
  position: absolute;
  left: 0.55rem;
  top: 0.45rem;
  padding: 0.18rem 0.42rem;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 0.62rem;
  letter-spacing: 0.04em;
  backdrop-filter: blur(6px);
  z-index: 1;
}

.gallery-detail-cover {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 8.5rem;
  border-radius: var(--radius-lg);
  color: #fff;
  overflow: hidden;
}

.gallery-detail-cover-icon {
  font-size: 2rem;
  position: relative;
  z-index: 1;
}

.gallery-card-body {
  min-width: 0;
  display: grid;
  gap: var(--space-1);
}

.gallery-card-meta {
  display: flex;
  justify-content: space-between;
  gap: var(--space-2);
}

.gallery-card-title,
.gallery-detail-title {
  font-size: var(--font-size-subtitle);
  color: var(--color-heading);
  line-height: var(--line-height-tight);
}

.gallery-card-text,
.gallery-detail-description,
.gallery-detail-excerpt {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
}

.gallery-detail {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding: var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: rgba(255, 255, 255, 0.04);
}

.gallery-detail-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: var(--space-3);
}

.gallery-detail-excerpt {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-heading);
}

.gallery-detail-meta,
.gallery-source-memory {
  display: grid;
  gap: var(--space-2);
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
}

.gallery-detail-meta-line,
.gallery-source-memory-title,
.gallery-source-memory-content,
.gallery-source-memory-date {
  margin: 0;
}

.gallery-detail-meta-line,
.gallery-source-memory-content {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
}

.gallery-source-memory-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-2);
  font-size: var(--font-size-small);
  color: var(--color-heading);
}

.gallery-source-memory-date {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

.gallery-detail-quote {
  padding: var(--space-3);
  border-left: 3px solid rgba(255, 143, 179, 0.45);
  border-radius: 0 var(--radius-md) var(--radius-md) 0;
  background: rgba(255, 255, 255, 0.03);
  color: var(--color-heading);
  font-size: var(--font-size-small);
  line-height: var(--line-height-body);
}

.gallery-condition-list {
  display: grid;
  gap: var(--space-2);
  padding: 0;
  margin: 0;
  list-style: none;
}

.gallery-condition-item {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  padding: var(--space-2) var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

.gallery-condition-item--fulfilled {
  color: var(--color-heading);
  border: 1px solid rgba(74, 222, 128, 0.25);
}

.memory-panel-body::-webkit-scrollbar {
  width: 4px;
}

.memory-panel-body::-webkit-scrollbar-track {
  background: transparent;
}

.memory-panel-body::-webkit-scrollbar-thumb {
  background: var(--color-border);
  border-radius: 2px;
}

/* ── Loading ── */

.memory-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  min-height: 160px;
  color: var(--color-text-muted);
  font-size: var(--font-size-small);
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: memory-spin 0.6s linear infinite;
  display: inline-block;
  box-sizing: border-box;
}

@keyframes memory-spin {
  to {
    transform: rotate(360deg);
  }
}

/* ── Empty state ── */

.memory-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 200px;
  gap: var(--space-2);
  padding: var(--space-4);
  text-align: center;
}

.memory-empty-text {
  margin: 0;
  font-size: var(--font-size-body);
  color: var(--color-text-muted);
}

.memory-empty-hint {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  opacity: 0.7;
}

/* ── Search results ── */

.memory-search-hint {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

.memory-card--search {
  border-color: var(--color-accent);
  box-shadow: inset 3px 0 0 var(--color-accent);
}

/* ── Memory cards ── */

.memory-list {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.memory-card {
  padding: var(--space-3) var(--space-4);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
  transition: border-color var(--duration-fast) ease;
}

.memory-card:hover {
  border-color: var(--color-border-strong);
}

.memory-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: var(--space-2);
}

.memory-date {
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
  font-variant-numeric: tabular-nums;
}

.memory-mood {
  font-size: var(--font-size-small);
  line-height: 1;
}

.memory-title {
  margin: 0 0 var(--space-1);
  font-size: var(--font-size-subtitle);
  color: var(--color-heading);
  line-height: var(--line-height-tight);
  font-weight: 500;
}

.memory-preview {
  margin: 0;
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
  line-height: var(--line-height-body);
  word-break: break-word;
}

.memory-image {
  display: block;
  width: 100%;
  margin-top: var(--space-2);
  border-radius: var(--radius-sm);
  object-fit: cover;
  max-height: 180px;
}

/* ── Load more ── */

.memory-load-more {
  display: flex;
  justify-content: center;
  padding: var(--space-3) 0;
}

.load-more-btn {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  padding: var(--space-2) var(--space-4);
  font-size: var(--font-size-small);
  font-family: var(--font-body);
  color: var(--color-accent);
  background: transparent;
  border: 1px solid var(--color-accent);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition:
    background var(--duration-fast) ease,
    color var(--duration-fast) ease;
}

.load-more-btn:hover:not(:disabled) {
  background: var(--color-accent-soft);
}

.load-more-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.load-more-btn:focus-visible {
  outline: var(--focus-width) solid var(--color-focus);
  outline-offset: var(--focus-offset);
}

/* ── Slide transition ── */

.panel-slide-enter-active,
.panel-slide-leave-active {
  transition: transform 250ms ease;
}

.panel-slide-enter-from,
.panel-slide-leave-to {
  transform: translateX(-100%);
}

/* ── Reduced motion ── */

@media (prefers-reduced-motion: reduce) {
  .panel-slide-enter-active,
  .panel-slide-leave-active {
    transition: none;
  }

  .memory-card,
  .memory-filter-chip,
  .memory-tab,
  .gallery-card,
  .memory-search-input,
  .memory-panel-close {
    transition: none;
  }

  .spinner {
    animation: none;
    opacity: 0.6;
  }
}
</style>
