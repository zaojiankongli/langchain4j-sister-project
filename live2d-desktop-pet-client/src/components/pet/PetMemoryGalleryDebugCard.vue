<script setup lang="ts">
import { computed, shallowRef } from 'vue'
import { get, post } from '../../utils/apiClient'
import type { ApiResult } from '../../types/api'

interface PersistedLink {
  id: number
  memoryId: number
  galleryKey: string
  confidence: number
  primaryLink: boolean
  matchedKeywords: string
  createdAt: string
}

interface PredictedMatch {
  galleryKey: string
  confidence: number
  primary: boolean
  reason: string
  matchedKeywords: string[]
}

interface PredictedResult {
  primaryGalleryKey: string | null
  matches: PredictedMatch[]
}

interface GalleryDebugData {
  memoryId: number
  userId: string
  title: string
  mood: string
  memoryDate: string
  imageUrl: string
  content: string
  persistedLinks: PersistedLink[]
  predictedResult: PredictedResult | null
}

const memoryIdInput = shallowRef('')
const isLoading = shallowRef(false)
const errorMessage = shallowRef('')
const debugData = shallowRef<GalleryDebugData | null>(null)
const previousDebugData = shallowRef<GalleryDebugData | null>(null)

const canQuery = computed(() => memoryIdInput.value.trim().length > 0)

async function fetchDebug(memoryId: string): Promise<void> {
  isLoading.value = true
  errorMessage.value = ''
  try {
    const res = await get<ApiResult<GalleryDebugData>>(`/api/ai/gallery/debug/memory/${encodeURIComponent(memoryId)}`)
    if (res.code !== 200 || !res.data) {
      errorMessage.value = res.message || '加载调试信息失败'
      return
    }
    debugData.value = res.data
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    isLoading.value = false
  }
}

async function inspectMemory(): Promise<void> {
  const memoryId = memoryIdInput.value.trim()
  if (!memoryId) {
    return
  }
  await fetchDebug(memoryId)
}

async function reclassifyMemory(): Promise<void> {
  const memoryId = memoryIdInput.value.trim()
  if (!memoryId) {
    return
  }
  previousDebugData.value = debugData.value
  isLoading.value = true
  errorMessage.value = ''
  try {
    const res = await post<ApiResult<GalleryDebugData>>(`/api/ai/gallery/debug/memory/${encodeURIComponent(memoryId)}/reclassify`)
    if (res.code !== 200 || !res.data) {
      errorMessage.value = res.message || '重跑归档失败'
      return
    }
    debugData.value = res.data
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : String(error)
  } finally {
    isLoading.value = false
  }
}

function formatKeywords(raw: string | string[] | undefined): string {
  if (!raw) return '—'
  if (Array.isArray(raw)) {
    return raw.length > 0 ? raw.join(' · ') : '—'
  }
  return raw.trim().length > 0 ? raw : '—'
}

function comparePrimary(before?: PredictedResult | null, after?: PredictedResult | null): string {
  const beforePrimary = before?.primaryGalleryKey || '—'
  const afterPrimary = after?.primaryGalleryKey || '—'
  return beforePrimary === afterPrimary ? `${afterPrimary}（未变化）` : `${beforePrimary} → ${afterPrimary}`
}

function compareMatchCounts(before?: PredictedResult | null, after?: PredictedResult | null): string {
  const beforeCount = before?.matches?.length ?? 0
  const afterCount = after?.matches?.length ?? 0
  return beforeCount === afterCount ? `${afterCount}（未变化）` : `${beforeCount} → ${afterCount}`
}

function findMatch(result: PredictedResult | null | undefined, galleryKey: string): PredictedMatch | undefined {
  return result?.matches?.find((match) => match.galleryKey === galleryKey)
}

function diffState(galleryKey: string, column: 'before' | 'after'): 'added' | 'removed' | 'changed' | 'unchanged' {
  const beforeMatch = findMatch(previousDebugData.value?.predictedResult, galleryKey)
  const afterMatch = findMatch(debugData.value?.predictedResult, galleryKey)

  if (column === 'before' && beforeMatch && !afterMatch) return 'removed'
  if (column === 'after' && !beforeMatch && afterMatch) return 'added'
  if (!beforeMatch || !afterMatch) return 'unchanged'

  const confidenceShift = Math.abs(beforeMatch.confidence - afterMatch.confidence)
  if (beforeMatch.primary !== afterMatch.primary || confidenceShift >= 0.08) {
    return 'changed'
  }
  return 'unchanged'
}

function diffStateLabel(galleryKey: string, column: 'before' | 'after'): string {
  const state = diffState(galleryKey, column)
  if (state === 'added') return '新增'
  if (state === 'removed') return '移除'
  if (state === 'changed') return '变化'
  return '稳定'
}

function primaryDiffClass(): string {
  const beforePrimary = previousDebugData.value?.predictedResult?.primaryGalleryKey || ''
  const afterPrimary = debugData.value?.predictedResult?.primaryGalleryKey || ''
  return beforePrimary && afterPrimary && beforePrimary !== afterPrimary ? 'diff-text--changed' : ''
}
</script>

<template>
  <section class="debug-card" aria-labelledby="gallery-debug-title">
    <div class="card-heading">
      <div>
        <p class="eyebrow">Memory Gallery</p>
        <h2 id="gallery-debug-title">Gallery debug and reclassify</h2>
      </div>
    </div>

    <label class="field">
      <span>Memory ID</span>
      <input
        v-model="memoryIdInput"
        class="field-input"
        type="text"
        placeholder="例如 12"
      />
    </label>

    <div class="actions" aria-label="Gallery debug actions">
      <button class="action" type="button" :disabled="!canQuery || isLoading" @click="inspectMemory">
        查看归档
      </button>
      <button class="action action-secondary" type="button" :disabled="!canQuery || isLoading" @click="reclassifyMemory">
        重跑归档
      </button>
    </div>

    <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>

    <div v-if="isLoading" class="loading-state">
      <span class="spinner" aria-hidden="true" />
      <span>加载中...</span>
    </div>

    <template v-else-if="debugData">
      <dl class="summary-list">
        <div>
          <dt>Memory</dt>
          <dd>#{{ debugData.memoryId }} · {{ debugData.memoryDate || '—' }}</dd>
        </div>
        <div>
          <dt>Title</dt>
          <dd>{{ debugData.title || '—' }}</dd>
        </div>
        <div>
          <dt>Mood</dt>
          <dd>{{ debugData.mood || '—' }}</dd>
        </div>
        <div>
          <dt>Image</dt>
          <dd>{{ debugData.imageUrl || '—' }}</dd>
        </div>
      </dl>

      <div class="content-block">
        <p class="block-label">Source content</p>
        <p class="block-text">{{ debugData.content || '—' }}</p>
      </div>

      <div class="image-block">
        <p class="block-label">Source image preview</p>
        <div v-if="debugData.imageUrl" class="image-preview-wrap">
          <img :src="debugData.imageUrl" alt="Memory source preview" class="image-preview" loading="lazy" />
          <p class="image-url">{{ debugData.imageUrl }}</p>
        </div>
        <p v-else class="empty-hint">这条记忆当前没有图片。</p>
      </div>

      <div class="result-block">
        <p class="block-label">Predicted result</p>
        <p class="primary-line">
          Primary: {{ debugData.predictedResult?.primaryGalleryKey || '—' }}
        </p>
        <div v-if="debugData.predictedResult?.matches?.length" class="match-list">
          <div v-for="match in debugData.predictedResult.matches" :key="`${match.galleryKey}-${match.confidence}`" class="match-item">
            <div class="match-header">
              <strong>{{ match.galleryKey }}</strong>
              <span>{{ Math.round(match.confidence * 100) }}%</span>
            </div>
            <p class="match-reason">{{ match.reason || '—' }}</p>
            <p class="match-keywords">关键词：{{ formatKeywords(match.matchedKeywords) }}</p>
          </div>
        </div>
      </div>

      <div class="result-block">
        <p class="block-label">Persisted links</p>
        <div v-if="debugData.persistedLinks?.length" class="match-list">
          <div v-for="link in debugData.persistedLinks" :key="link.id" class="match-item">
            <div class="match-header">
              <strong>{{ link.galleryKey }}</strong>
              <span>{{ Math.round(link.confidence * 100) }}%</span>
            </div>
            <p class="match-reason">Primary: {{ link.primaryLink ? 'yes' : 'no' }}</p>
            <p class="match-keywords">关键词：{{ formatKeywords(link.matchedKeywords) }}</p>
          </div>
        </div>
        <p v-else class="empty-hint">还没有已落库的 links。</p>
      </div>

      <div v-if="previousDebugData" class="result-block">
        <p class="block-label">Reclassify diff</p>
        <dl class="summary-list diff-summary">
          <div>
            <dt>Primary</dt>
            <dd :class="primaryDiffClass()">{{ comparePrimary(previousDebugData.predictedResult, debugData.predictedResult) }}</dd>
          </div>
          <div>
            <dt>Matches</dt>
            <dd>{{ compareMatchCounts(previousDebugData.predictedResult, debugData.predictedResult) }}</dd>
          </div>
          <div>
            <dt>Persisted links</dt>
            <dd>{{ previousDebugData.persistedLinks.length }} → {{ debugData.persistedLinks.length }}</dd>
          </div>
        </dl>

        <div class="diff-columns">
          <div class="diff-column">
            <p class="block-label">Before</p>
            <div class="match-list" v-if="previousDebugData.predictedResult?.matches?.length">
              <div
                v-for="match in previousDebugData.predictedResult.matches"
                :key="`before-${match.galleryKey}-${match.confidence}`"
                class="match-item"
                :class="`match-item--${diffState(match.galleryKey, 'before')}`"
              >
                <div class="match-header">
                  <strong>{{ match.galleryKey }}</strong>
                  <span>{{ Math.round(match.confidence * 100) }}%</span>
                </div>
                <p class="diff-badge">{{ diffStateLabel(match.galleryKey, 'before') }}</p>
                <p class="match-reason">{{ match.reason || '—' }}</p>
                <p class="match-keywords">关键词：{{ formatKeywords(match.matchedKeywords) }}</p>
              </div>
            </div>
            <p v-else class="empty-hint">没有重跑前预测结果。</p>
          </div>

          <div class="diff-column">
            <p class="block-label">After</p>
            <div class="match-list" v-if="debugData.predictedResult?.matches?.length">
              <div
                v-for="match in debugData.predictedResult.matches"
                :key="`after-${match.galleryKey}-${match.confidence}`"
                class="match-item"
                :class="`match-item--${diffState(match.galleryKey, 'after')}`"
              >
                <div class="match-header">
                  <strong>{{ match.galleryKey }}</strong>
                  <span>{{ Math.round(match.confidence * 100) }}%</span>
                </div>
                <p class="diff-badge">{{ diffStateLabel(match.galleryKey, 'after') }}</p>
                <p class="match-reason">{{ match.reason || '—' }}</p>
                <p class="match-keywords">关键词：{{ formatKeywords(match.matchedKeywords) }}</p>
              </div>
            </div>
            <p v-else class="empty-hint">没有重跑后预测结果。</p>
          </div>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.debug-card {
  padding: var(--space-6);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-panel);
}

.card-heading {
  display: flex;
  justify-content: space-between;
  gap: var(--space-4);
  align-items: start;
}

.eyebrow {
  margin: 0 0 var(--space-3);
  font-size: var(--font-size-caption);
  letter-spacing: var(--letter-spacing-wide);
  text-transform: uppercase;
  color: var(--color-accent);
}

h2,
.inline-error,
.empty-hint,
.block-label,
.block-text,
.match-reason,
.match-keywords,
.primary-line,
.loading-state span,
.summary-list dt,
.summary-list dd {
  margin: 0;
}

h2 {
  font-family: var(--font-display);
  font-size: var(--font-size-title);
  line-height: var(--line-height-tight);
  color: var(--color-heading);
}

.field {
  display: grid;
  gap: var(--space-2);
  margin-top: var(--space-4);
}

.field span,
.block-label,
.summary-list dt,
.match-keywords,
.empty-hint {
  font-size: var(--font-size-small);
  color: var(--color-text-muted);
}

.field-input {
  width: 100%;
  padding: var(--space-3);
  border: var(--border-width) solid var(--color-border);
  border-radius: var(--radius-md);
  color: var(--color-text);
  background: var(--color-field-bg);
  font: inherit;
  box-sizing: border-box;
}

.field-input:focus {
  border-color: var(--color-focus);
  outline: none;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
  margin-top: var(--space-5);
}

.action {
  border: var(--border-width) solid var(--color-accent);
  border-radius: var(--radius-pill);
  padding: var(--space-2) var(--space-4);
  color: var(--color-action-text);
  background: var(--color-accent);
  font: inherit;
  cursor: pointer;
}

.action:disabled {
  cursor: not-allowed;
  opacity: 0.45;
}

.action-secondary {
  color: var(--color-accent);
  background: transparent;
}

.inline-error {
  margin-top: var(--space-3);
  color: var(--color-danger);
  font-size: var(--font-size-small);
}

.loading-state {
  display: flex;
  align-items: center;
  gap: var(--space-2);
  margin-top: var(--space-4);
  color: var(--color-text-muted);
}

.spinner {
  width: 1rem;
  height: 1rem;
  border: 2px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: gallery-debug-spin 0.6s linear infinite;
  display: inline-block;
  box-sizing: border-box;
}

@keyframes gallery-debug-spin {
  to { transform: rotate(360deg); }
}

.summary-list,
.image-block,
.content-block,
.result-block {
  margin-top: var(--space-4);
}

.summary-list {
  display: grid;
  gap: var(--space-2);
}

.summary-list div,
.match-item,
.image-block,
.content-block,
.result-block {
  padding: var(--space-3);
  border-radius: var(--radius-md);
  background: var(--color-surface-subtle);
}

.summary-list div {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  gap: var(--space-3);
}

.summary-list dd,
.block-text,
.match-reason,
.primary-line,
.image-url {
  color: var(--color-heading);
  font-size: var(--font-size-small);
  line-height: var(--line-height-body);
  overflow-wrap: anywhere;
}

.image-preview-wrap {
  display: grid;
  gap: var(--space-3);
}

.image-preview {
  width: 100%;
  max-height: 14rem;
  object-fit: cover;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.06);
}

.match-list {
  display: grid;
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.match-item--added {
  border: 1px solid rgba(74, 222, 128, 0.4);
}

.match-item--removed {
  border: 1px solid rgba(248, 113, 113, 0.35);
}

.match-item--changed {
  border: 1px solid rgba(251, 191, 36, 0.35);
}

.diff-badge {
  margin: var(--space-2) 0 0;
  font-size: var(--font-size-caption);
  color: var(--color-text-muted);
}

.diff-text--changed {
  color: var(--color-accent);
}

.diff-columns {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-3);
  margin-top: var(--space-3);
}

.diff-column {
  min-width: 0;
}

.diff-summary {
  margin-top: var(--space-3);
}

.match-header {
  display: flex;
  justify-content: space-between;
  gap: var(--space-3);
  color: var(--color-heading);
  font-size: var(--font-size-small);
}

@media (prefers-reduced-motion: reduce) {
  .spinner {
    animation: none;
    opacity: 0.6;
  }
}

@media (max-width: 640px) {
  .diff-columns {
    grid-template-columns: 1fr;
  }
}
</style>
