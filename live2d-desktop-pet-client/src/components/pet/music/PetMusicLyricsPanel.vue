<script setup lang="ts">
import { computed, nextTick, onMounted, ref, useTemplateRef, watch } from 'vue'

interface LyricLine {
  time: number
  text: string
}

const props = defineProps<{
  title: string
  subtitle: string
  lines: ReadonlyArray<LyricLine>
  currentIndex: number
}>()

const scrollRef = useTemplateRef<HTMLDivElement>('lyricsScroll')
const prefersReducedMotion = ref(false)

const hasLyrics = computed(() => props.lines.length > 0)

function scrollActiveLine(): void {
  if (!hasLyrics.value || props.currentIndex < 0) {
    return
  }

  const currentLine = scrollRef.value?.querySelector<HTMLElement>('[data-current="true"]')
  currentLine?.scrollIntoView({
    block: 'center',
    behavior: prefersReducedMotion.value ? 'auto' : 'smooth',
  })
}

watch(
  () => [props.currentIndex, props.lines.length] as const,
  () => {
    void nextTick(() => scrollActiveLine())
  },
  { immediate: true },
)

onMounted(() => {
  prefersReducedMotion.value = window.matchMedia('(prefers-reduced-motion: reduce)').matches
})
</script>

<template>
  <section class="lyrics-panel" :class="{ 'lyrics-panel--empty': !hasLyrics }">
    <header class="lyrics-header">
      <div class="lyrics-header-copy">
        <p class="lyrics-kicker">歌词</p>
        <h2 class="lyrics-title">{{ title }}</h2>
        <p class="lyrics-subtitle">{{ subtitle }}</p>
      </div>
      <div class="lyrics-status" :class="{ 'lyrics-status--active': hasLyrics }">
        {{ hasLyrics ? '同步播放' : '暂无歌词' }}
      </div>
    </header>

    <div ref="lyricsScroll" class="lyrics-scroll">
      <template v-if="hasLyrics">
        <p
          v-for="(line, index) in lines"
          :key="`${line.time}-${index}`"
          class="lyrics-line"
          :class="{
            'lyrics-line--active': index === currentIndex,
            'lyrics-line--past': index < currentIndex,
          }"
          :data-current="index === currentIndex"
        >
          {{ line.text }}
        </p>
      </template>
      <div v-else class="lyrics-empty">
        <p class="lyrics-empty-title">还没有歌词文件</p>
        <p class="lyrics-empty-body">
          把同名 <span translate="no">.lrc</span> 或 <span translate="no">.txt</span> 放到歌曲旁边，播放器会自动识别并同步显示。
        </p>
      </div>
    </div>
  </section>
</template>

<style scoped>
.lyrics-panel {
  display: grid;
  gap: 0.9rem;
  width: min(100%, 760px);
  padding: 1.05rem 1.1rem 1rem;
  border-radius: 1rem;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.06);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  box-shadow:
    0 1rem 2rem rgba(0, 0, 0, 0.18),
    inset 0 1px 0 rgba(255, 255, 255, 0.03);
  box-sizing: border-box;
}

.lyrics-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
}

.lyrics-header-copy {
  min-width: 0;
  display: grid;
  gap: 0.2rem;
}

.lyrics-kicker {
  margin: 0;
  color: rgba(74, 222, 128, 0.9);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.lyrics-title {
  margin: 0;
  color: #fff7fb;
  font-size: 0.92rem;
  font-weight: 600;
  letter-spacing: 0.02em;
}

.lyrics-subtitle {
  margin: 0;
  color: rgba(220, 229, 244, 0.46);
  font-size: 0.72rem;
}

.lyrics-status {
  flex-shrink: 0;
  align-self: center;
  padding: 0.22rem 0.55rem;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  color: rgba(220, 229, 244, 0.44);
  background: rgba(255, 255, 255, 0.04);
  font-size: 0.66rem;
  letter-spacing: 0.04em;
}

.lyrics-status--active {
  color: rgba(74, 222, 128, 0.95);
  border-color: rgba(74, 222, 128, 0.2);
  background: rgba(74, 222, 128, 0.06);
}

.lyrics-scroll {
  display: grid;
  gap: 0.4rem;
  max-height: 19rem;
  overflow-y: auto;
  padding-right: 0.25rem;
  scroll-behavior: smooth;
}

.lyrics-line {
  margin: 0;
  padding: 0.48rem 0.7rem;
  border-radius: 0.7rem;
  color: rgba(244, 247, 255, 0.54);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-word;
  transition: background-color 140ms ease, color 140ms ease, transform 140ms ease, opacity 140ms ease;
}

.lyrics-line--past {
  opacity: 0.72;
}

.lyrics-line--active {
  color: #fff7fb;
  background: rgba(255, 255, 255, 0.08);
  transform: translateX(2px);
}

.lyrics-empty {
  min-height: 13rem;
  display: grid;
  place-items: center;
  gap: 0.35rem;
  padding: 1.1rem 0.5rem;
  text-align: center;
}

.lyrics-empty-title {
  margin: 0;
  color: rgba(244, 247, 255, 0.8);
  font-size: 0.86rem;
}

.lyrics-empty-body {
  margin: 0;
  color: rgba(220, 229, 244, 0.42);
  font-size: 0.74rem;
  line-height: 1.6;
}

@media (prefers-reduced-motion: reduce) {
  .lyrics-scroll {
    scroll-behavior: auto;
  }

  .lyrics-line {
    transition: none;
  }
}
</style>
