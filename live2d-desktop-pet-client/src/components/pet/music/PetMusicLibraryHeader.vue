<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string
  sectionLabel: string
  searchQuery: string
}>()

const emit = defineEmits<{
  'update:searchQuery': [value: string]
  chooseDirectory: []
  chooseFiles: []
}>()
</script>

<template>
  <header class="library-header">
    <div class="library-header-top">
      <h2 class="library-title">{{ title }}</h2>
      <p class="library-subtitle">{{ subtitle }}</p>
    </div>
    <div class="library-header-act">
      <span class="library-act-label">{{ sectionLabel }}</span>
      <div class="library-scan-actions">
        <button type="button" class="library-scan-btn" @click="emit('chooseDirectory')">扫文件夹</button>
        <button type="button" class="library-scan-btn" @click="emit('chooseFiles')">扫文件</button>
      </div>
    </div>
    <input
      :value="searchQuery"
      class="library-search"
      type="search"
      aria-label="搜索本地音乐"
      placeholder="搜索歌曲 / 歌手 / 专辑"
      @input="emit('update:searchQuery', ($event.target as HTMLInputElement).value)"
    >
  </header>
</template>

<style scoped>
.library-header {
  display: grid;
  gap: 0.55rem;
}

.library-header-top {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
}

.library-title {
  margin: 0;
  font-size: 1.2rem;
  font-weight: 500;
  color: #fff7fb;
  letter-spacing: 0.03em;
}

.library-subtitle {
  margin: 0;
  font-size: 0.68rem;
  color: rgba(220, 229, 244, 0.5);
  letter-spacing: 0.04em;
}

.library-header-act {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.library-act-label {
  display: inline-block;
  font-size: 0.78rem;
  font-weight: 500;
  color: #4ade80;
  padding-bottom: 2px;
  border-bottom: 2px solid #4ade80;
  letter-spacing: 0.05em;
}

.library-scan-actions {
  display: flex;
  gap: 0.35rem;
}

.library-scan-btn {
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 999px;
  padding: 0.18rem 0.5rem;
  color: rgba(244, 247, 255, 0.68);
  background: rgba(255, 255, 255, 0.05);
  font: inherit;
  font-size: 0.66rem;
  cursor: pointer;
  transition:
    border-color 120ms ease,
    background 120ms ease,
    color 120ms ease;
}

.library-scan-btn:hover {
  border-color: rgba(134, 198, 255, 0.42);
  color: #fff7fb;
  background: rgba(255, 255, 255, 0.1);
}

.library-scan-btn:focus-visible {
  outline: 2px solid rgba(134, 198, 255, 0.72);
  outline-offset: 2px;
}

.library-search {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 0.55rem;
  background: rgba(255, 255, 255, 0.05);
  color: #f4f7ff;
  padding: 0.42rem 0.55rem;
  font: inherit;
  font-size: 0.72rem;
  outline: none;
}

.library-search::placeholder {
  color: rgba(220, 229, 244, 0.35);
}

.library-search:focus {
  border-color: rgba(134, 198, 255, 0.62);
  background: rgba(255, 255, 255, 0.08);
}
</style>
