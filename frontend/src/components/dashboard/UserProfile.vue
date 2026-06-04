<script setup>
import { ref, computed, watch, onBeforeUnmount } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'
import { API } from '@/config/api'
import { useAsyncData } from '@/composables/useAsyncData'
import { useUiStore } from '@/stores/ui'
import { useUserStore } from '@/stores/user'

let _isMounted = true
onBeforeUnmount(() => { _isMounted = false })

const uiStore = useUiStore()
const userStore = useUserStore()

// ==========================================
// 一、数据获取与初始化
// ==========================================
const { data: user, loading, error, execute: fetchUserData } = useAsyncData(async () => {
  const userId = getUserId()
  if (!userId) return
  const res = await request.get(API.MY_PROFILE)
  if (res.code === 200 && res.data) return res.data
  throw new Error(res.message || '获取用户数据失败')
})

// ==========================================
// 二、数据结构 (Data Model Mapping)
// ==========================================
const userDefault = ref({
  id: '1024',
  username: 'Master',
  avatar_url: '',
  gender: 1,
  birthday: '2000-01-01',
  hobbies: '前端工程,Nijigen,交互设计',
  user_profile: '她的变化：最近变得比以前更主动了。通过近期的互动，她似乎对你的技术追求产生了浓厚的兴趣。',
  // AI 画像生成的图片
  ai_image_url: '',
  ai_tags: ['理性派', '深夜码农', '温柔的', '技术宅'],
  ai_type: 2,
  created_at: '2023-10-01T00:00:00Z',
  updated_at: new Date().toISOString()
})

// Merge fetched data with defaults
const mergedUser = ref({})
// Watch for user data changes and merge with defaults
// 字段映射：后端返回 snake_case，前端使用 camelCase 默认值
watch(() => user.value, (newVal) => {
  if (newVal) {
    mergedUser.value = {
      ...userDefault.value,
      ...newVal,
      // 映射后端 interest_tags → 前端 ai_tags
      ai_tags: newVal.interestTags || newVal.interest_tags || userDefault.value.ai_tags,
      // 后端 avatar_url → 前端 avatar_url（@JsonProperty 已映射）
      // 后端无 ai_image_url 字段，保留默认空值，由"重新绘制"按钮填充
    }
  } else {
    mergedUser.value = { ...userDefault.value }
  }
}, { immediate: true })

// ==========================================
// 三、前端派生数据
// ==========================================
const age = computed(() => {
  if (!mergedUser.value.birthday) return null
  const birthYear = new Date(mergedUser.value.birthday).getFullYear()
  return new Date().getFullYear() - birthYear
})
let _prevHobbies = undefined
let _cachedHobbyList = []
const hobbyList = computed(() => {
  const hobbies = mergedUser.value.hobbies
  if (hobbies === _prevHobbies) return _cachedHobbyList
  _prevHobbies = hobbies
  _cachedHobbyList = hobbies ? hobbies.split(',') : []
  return _cachedHobbyList
})
const aiTypeMap = {  2: '妹妹' }
const aiTypeLabel = computed(() => aiTypeMap[mergedUser.value.ai_type])
const daysTogether = computed(() => {
  const start = new Date(mergedUser.value.created_at)
  return Math.floor((new Date() - start) / (1000 * 60 * 60 * 24))
})

// ==========================================
// 四、状态管理
// ==========================================
const isEditingProfile = ref(false)
const profileForm = ref({ username: '', gender: 1 })
const avatarInput = ref(null)
const isAddingHobby = ref(false)
const hobbyInputValue = ref('')
const showAITypeSelector = ref(false)
const isActivityCollapsed = ref(true)

// ==========================================
// 七、交互行为
// ==========================================
const triggerAvatarUpload = () => avatarInput.value.click()
const handleAvatarChange = async (event) => {
  const file = event.target.files[0]
  if (!file) return
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await request.post(API.USER_AVATAR, formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    if (!_isMounted) return
    if (res.code === 200 && res.data) {
      mergedUser.value.avatar_url = res.data
    }
  } catch (err) {
    if (!_isMounted) return
    console.error('Failed to upload avatar', err)
    uiStore.error('头像上传失败，请重试')
  }
  event.target.value = ''
}
const toggleEditProfile = () => {
  if (!isEditingProfile.value) {
    profileForm.value.username = mergedUser.value.username
    profileForm.value.gender = mergedUser.value.gender
    isEditingProfile.value = true
  } else { isEditingProfile.value = false }
}
const saveProfile = async () => {
    try {
      await userStore.updateBasic({
        username: profileForm.value.username,
        gender: profileForm.value.gender
      })
      if (!_isMounted) return
      mergedUser.value.username = profileForm.value.username
      mergedUser.value.gender = profileForm.value.gender
      isEditingProfile.value = false
    } catch (err) {
      console.error('Failed to save profile', err)
      uiStore.error('保存资料失败，请重试')
    }
  }
const removeHobby = async (index) => {
    const prevHobbies = mergedUser.value.hobbies // 保存旧值用于回滚
    const list = [...hobbyList.value]; list.splice(index, 1)
    mergedUser.value.hobbies = list.join(',')
    try {
      await userStore.updateHobbies(mergedUser.value.hobbies)
      if (!_isMounted) { mergedUser.value.hobbies = prevHobbies; return }
    } catch (err) {
      mergedUser.value.hobbies = prevHobbies // 回滚
      console.error('Failed to remove hobby', err)
      uiStore.error('删除兴趣标签失败，请重试')
    }
  }
const addHobby = async () => {
   if (hobbyInputValue.value.trim()) {
     const prevHobbies = mergedUser.value.hobbies // 保存旧值用于回滚
     const list = [...hobbyList.value, hobbyInputValue.value.trim()]
     mergedUser.value.hobbies = list.join(',')
      try {
        await userStore.updateHobbies(mergedUser.value.hobbies)
        if (!_isMounted) { mergedUser.value.hobbies = prevHobbies; return }
     } catch (err) {
         mergedUser.value.hobbies = prevHobbies // 回滚
        console.error('Failed to add hobby', err)
        uiStore.error('添加兴趣标签失败，请重试')
      }
   }
   hobbyInputValue.value = ''
   isAddingHobby.value = false
 }
const changeAIType = async (type) => {
  const prevType = mergedUser.value.ai_type // 保存旧值用于回滚
  mergedUser.value.ai_type = type
  showAITypeSelector.value = false
  try {
    await request.post(API.USER_UPDATE_AI_TYPE, { ai_type: type })
    if (!_isMounted) { mergedUser.value.ai_type = prevType; return }
  } catch (err) {
    mergedUser.value.ai_type = prevType // 回滚
    console.error('Failed to change AI type', err)
    uiStore.error('变更AI类型失败，请重试')
  }
}

// ==========================================
// 五、AI 图片重新绘制
// ==========================================
import { useImageGeneration } from '@/composables/useImageGeneration'
const { loading: genLoading, error: genError, generateFromContent } = useImageGeneration(() => _isMounted)

const isRedrawing = ref(false)

async function handleRedraw() {
  if (!_isMounted || isRedrawing.value) return
  isRedrawing.value = true
  try {
    const content = mergedUser.value.user_profile || mergedUser.value.userProfile || ''
    const url = await generateFromContent({ content })
    if (!_isMounted) return
    if (url) {
      mergedUser.value.ai_image_url = url
      uiStore.success('画像已更新')
    } else {
      uiStore.error(genError.value || '绘制失败')
    }
  } catch (e) {
    uiStore.error('重新绘制失败')
    console.error('重新绘制失败:', e)
  } finally {
    if (_isMounted) isRedrawing.value = false
  }
}
</script>

<template>
  <div class="subspace-about">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p class="loading-text">加载用户数据...</p>
    </div>
    <div v-else-if="error" class="error-overlay">
      <p class="error-text">加载失败：{{ error.message || '未知错误' }}</p>
      <span class="action-btn" @click="fetchUserData()">重试 //</span>
    </div>

    <div class="glass-section data-grid stagger-1 relative-section">
      <div class="section-tag">IDENTITY_CARD //</div>
      <div class="profile-main">
        <div class="avatar-area" @click="triggerAvatarUpload">
          <img v-if="mergedUser.avatar_url" :src="mergedUser.avatar_url" class="avatar-circle avatar-img" alt="avatar" />
          <div v-else class="avatar-circle avatar-placeholder"><span class="upload-icon">+</span></div>
          <input type="file" ref="avatarInput" accept="image/*" class="hidden-file-input" @change="handleAvatarChange" />
        </div>
        <div class="info-column">
          <template v-if="isEditingProfile">
            <div class="info-row"><span class="label">昵称</span><input v-model="profileForm.username" class="inline-input" /></div>
            <div class="info-row">
              <span class="label">核心属性</span>
              <span class="value clickable" @click="profileForm.gender = profileForm.gender === 1 ? 2 : 1">
                {{ profileForm.gender === 1 ? 'MALE' : 'FEMALE' }} <span class="action-hint">[切换]</span>
              </span>
            </div>
          </template>
          <template v-else>
            <div class="info-row"><span class="label">昵称</span><span class="value">{{ mergedUser.username }}</span></div>
            <div class="info-row">
              <span class="label">核心属性</span>
              <span class="value">{{ mergedUser.gender === 1 ? 'MALE' : 'FEMALE' }}</span>
              <span class="value-divider">/</span>
              <span class="value">{{ age }} YEARS OLD</span>
            </div>
          </template>
        </div>
      </div>
      <div class="bottom-right-action">
        <span v-if="isEditingProfile" class="action-btn" @click="saveProfile">保存更改 //</span>
        <span v-else class="action-btn" @click="toggleEditProfile">修改资料 //</span>
      </div>
    </div>

    <div class="glass-section relationship-status stagger-2">
      <div class="section-tag">RELATIONSHIP_BOND //</div>
      <div class="bond-display">
        <div class="bond-header compact-header">
          <div class="bond-label">设定关系:</div>
          <div class="ai-selector-wrapper">
            <span class="value editable" @click="showAITypeSelector = !showAITypeSelector">{{ aiTypeLabel }}</span>
            <div v-if="showAITypeSelector" class="type-dropdown">
              <div v-for="(label, key) in aiTypeMap" :key="key" @click="changeAIType(parseInt(key))" class="type-item">{{ label }}</div>
            </div>
          </div>
        </div>
        <div class="bond-value"><span class="unit">相处</span>{{ daysTogether }} <span class="unit">天</span></div>
      </div>
    </div>

    <div class="glass-section hobby-tags stagger-3">
      <div class="section-tag">HOBBY_TAGS //</div>
      <div class="tag-cloud">
        <span v-for="(tag, index) in hobbyList" :key="'hobby-' + tag + '-' + index" class="mini-tag">
          {{ tag }} <span class="tag-del" @click="removeHobby(index)">×</span>
        </span>
        <input v-if="isAddingHobby" v-model="hobbyInputValue" @blur="addHobby" @keyup.enter="addHobby" class="tag-input" autoFocus />
        <span v-else class="mini-tag add-btn" @click="isAddingHobby = true">+ ADD</span>
      </div>
    </div>

    <div class="glass-section perception-card stagger-4">
      <div class="section-tag">AI_PERCEPTION //</div>
      <div class="perception-flex">
        <div class="perception-info">
          <div class="memo-box">{{ mergedUser.user_profile }}</div>
          <div class="action-row">
            <span class="action-btn" :class="{ 'is-disabled': isRedrawing }" @click="handleRedraw">
              {{ isRedrawing ? '绘制中...' : '重新绘制 //' }}
            </span>
          </div>
        </div>
        <div class="perception-poster">
          <img v-if="mergedUser.ai_image_url" :src="mergedUser.ai_image_url" alt="AI Perception" />
          <div v-else class="perception-placeholder">
            <span class="placeholder-hint">{{ isRedrawing ? '✦' : '✧' }}</span>
          </div>
          <div class="poster-overlay"></div>
        </div>
      </div>
    </div>

    <div class="glass-section ai-impression stagger-5">
      <div class="section-tag">AI_IMPRESSION_TAGS //</div>
      <div class="memo-box">她眼中的你</div>
      <div class="tag-cloud readonly">
        <span v-for="(tag, index) in mergedUser.ai_tags" :key="'ai-tag-' + tag + '-' + index" class="ai-pixel-tag">
          <span class="prefix">#</span>{{ tag }}
        </span>
      </div>
      <div class="section-tag">* 该内容由 AI 根据日常互动自动生成，无法手动修改</div>
    </div>

    <div class="glass-section activity-info stagger-6" :class="{ 'collapsed': isActivityCollapsed }">
      <div class="section-tag clickable" @click="isActivityCollapsed = !isActivityCollapsed">
        ACTIVITY_LOG // <span class="toggle-icon">{{ isActivityCollapsed ? '[+]' : '[-]' }}</span>
      </div>
      <div v-if="!isActivityCollapsed" class="status-list">
        <div class="status-item">CREATED_AT: {{ new Date(mergedUser.created_at).toLocaleDateString() }}</div>
        <div class="status-item">UPDATED_AT: {{ new Date(mergedUser.updated_at).toLocaleDateString() }}</div>
      </div>
    </div>

  </div>
</template>

<style scoped>
/* 核心布局 */
.subspace-about { padding: 40px 20px; color: #fff; animation: space-dissolve 1.2s ease both; max-width: 800px; margin: 0 auto; }

/* 通用毛玻璃卡片 */
.glass-section {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.03);
  padding: 24px;
  margin-bottom: 24px;
  border-radius: 4px;
  backdrop-filter: blur(12px);
  position: relative;
  transition: all 0.3s cubic-bezier(0.22, 1, 0.36, 1);
}

.section-tag { font-size: 10px; color: rgba(255, 255, 255, 0.5); letter-spacing: 2px; margin-bottom: 20px; font-family: monospace; }

/* 1. IDENTITY 修复 */
.profile-main { display: flex; gap: 24px; align-items: center; }
.avatar-area { position: relative; width: 64px; height: 64px; cursor: pointer; }
.avatar-circle { width: 100%; height: 100%; border-radius: 50%; border: 1px solid rgba(255,255,255,0.2); object-fit: cover; }
.avatar-placeholder { display: flex; align-items: center; justify-content: center; background: rgba(255,255,255,0.05); font-size: 24px; color: rgba(255,255,255,0.3); }

/* 核心修复：彻底隐藏 input 文本 */
.hidden-file-input {
  position: absolute;
  top: 0; left: 0;
  width: 100%; height: 100%;
  opacity: 0;
  cursor: pointer;
  display: block;
}

.info-row { display: flex; align-items: baseline; margin-bottom: 10px; }
.label { width: 80px; font-size: 12px; opacity: 0.5; }
.value { font-size: 14px; letter-spacing: 1px; }
.inline-input { background: transparent; border: none; border-bottom: 1px solid #7c9cff; color: #fff; width: 140px; outline: none; }

/* 2. RELATIONSHIP */
.compact-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 15px; }
.bond-value { font-size: 36px; font-weight: 200; background: linear-gradient(90deg, #fff, rgba(255,255,255,0.2)); -webkit-background-clip: text; -webkit-text-fill-color: transparent; letter-spacing: 2px; }
.unit { font-size: 12px; margin: 0 6px; opacity: 0.4; -webkit-text-fill-color: initial; color: #fff; }

/* 4. PERCEPTION (AI画像布局修复) */
.perception-flex { display: flex; gap: 20px; align-items: flex-start; }
.perception-info { flex: 1; }
.perception-poster {
  width: 120px; height: 160px;
  border-radius: 4px;
  overflow: hidden;
  position: relative;
  border: 1px solid rgba(255,255,255,0.1);
  box-shadow: 0 10px 30px rgba(0,0,0,0.5);
  flex-shrink: 0;
}
.perception-poster img { width: 100%; height: 100%; object-fit: cover; }
.perception-placeholder {
  width: 100%; height: 100%;
  display: flex; align-items: center; justify-content: center;
  background: rgba(255,255,255,0.03);
}
.placeholder-hint { font-size: 28px; color: rgba(255,255,255,0.15); }
.action-btn.is-disabled { opacity: 0.4; cursor: not-allowed; pointer-events: none; }
.poster-overlay {
  position: absolute; top: 0; left: 0; width: 100%; height: 100%;
  background: linear-gradient(to top, rgba(0,0,0,0.4), transparent);
}
.memo-box { font-size: 13px; line-height: 1.8; color: rgba(255,255,255,0.8); margin-bottom: 15px; }

/* 5. IMPRESSION */
.ai-label-title {  }
.ai-hint{font-size: 14px; margin-bottom: 15px; color: #7c9cff; font-weight: bold;}
.ai-pixel-tag {
  font-size: 11px; padding: 4px 12px;
  background: rgba(124, 156, 255, 0.08);
  border: 1px solid rgba(124, 156, 255, 0.2);
  color: #acc2ff; margin-right: 10px; margin-bottom: 8px;
  display: inline-block;
}

/* 其他辅助 */
.action-btn { color: #7c9cff; font-size: 11px; cursor: pointer; opacity: 0.7; transition: 0.3s; }
.action-btn:hover { opacity: 1; text-shadow: 0 0 10px rgba(124,156,255,0.6); }
.bottom-right-action { position: absolute; bottom: 24px; right: 24px; }
.tag-cloud { display: flex; gap: 8px; flex-wrap: wrap; }
.mini-tag { font-size: 11px; padding: 3px 10px; border: 1px solid rgba(255,255,255,0.1); background: rgba(255,255,255,0.03); }
.tag-del { margin-left: 6px; cursor: pointer; color: #ff4d4f; }

/* 加载状态 */
.loading-overlay {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  min-height: 200px; gap: 16px;
}
.loading-spinner {
  width: 24px; height: 24px;
  border: 2px solid rgba(255,255,255,0.1);
  border-top-color: #fff;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}
.loading-text { font-size: 13px; color: rgba(255,255,255,0.5); letter-spacing: 1px; }

/* 动画 */
@keyframes space-dissolve {
  0% { opacity: 0; transform: translateY(15px); filter: blur(10px); }
  100% { opacity: 1; transform: translateY(0); filter: blur(0); }
}
.stagger-1 { animation-delay: 0.1s; }
.stagger-2 { animation-delay: 0.2s; }
.stagger-3 { animation-delay: 0.3s; }
.stagger-4 { animation-delay: 0.4s; }
.stagger-5 { animation-delay: 0.5s; }
.stagger-6 { animation-delay: 0.6s; }
</style>