<script setup>
import { ref, computed, onMounted } from 'vue'
import request from '@/utils/request'
import { getUserId } from '@/utils/auth'

// ==========================================
// 一、数据获取与初始化
// ==========================================
const loading = ref(false)

onMounted(async () => {
  await fetchUserData()
})

const fetchUserData = async () => {
  loading.value = true
  try {
    const userId = getUserId()
    if (!userId) return
    const res = await request.get('/user/profile')
    if (res.code === 200 && res.data) {
      const data = res.data
      Object.assign(user.value, {
        id: data.id || user.value.id,
        username: data.username || user.value.username,
        avatar_url: data.avatar_url || user.value.avatar_url,
        gender: data.gender ?? user.value.gender,
        birthday: data.birthday || user.value.birthday,
        hobbies: data.hobbies || user.value.hobbies,
        user_profile: data.user_profile || user.value.user_profile,
        ai_type: data.ai_type ?? user.value.ai_type,
        created_at: data.created_at || user.value.created_at,
        updated_at: data.updated_at || user.value.updated_at,
        ai_tags: data.interestTags || user.value.ai_tags
      })
    }
  } catch (error) {
    console.error('Failed to fetch user data', error)
  } finally {
    loading.value = false
  }
}
  } catch {
    // 静默失败
  }
}

// ==========================================
// 二、数据结构 (Data Model Mapping)
// ==========================================
const user = ref({
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

// ==========================================
// 三、前端派生数据
// ==========================================
const age = computed(() => {
  if (!user.value.birthday) return null
  const birthYear = new Date(user.value.birthday).getFullYear()
  return new Date().getFullYear() - birthYear
})
const hobbyList = computed(() => user.value.hobbies ? user.value.hobbies.split(',') : [])
const aiTypeMap = {  2: '妹妹' }
const aiTypeLabel = computed(() => aiTypeMap[user.value.ai_type])
const daysTogether = computed(() => {
  const start = new Date(user.value.created_at)
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
    if (res.code === 200 && res.data) {
      user.value.avatar_url = res.data
    }
  } catch {
    // 静默失败
  }
  event.target.value = ''
}
const toggleEditProfile = () => {
  if (!isEditingProfile.value) {
    profileForm.value.username = user.value.username
    profileForm.value.gender = user.value.gender
    isEditingProfile.value = true
  } else { isEditingProfile.value = false }
}
const saveProfile = async () => {
  try {
    const res = await request.post(API.USER_UPDATE_BASIC, {
      username: profileForm.value.username,
      gender: profileForm.value.gender
    })
    if (res.code === 200) {
      user.value.username = profileForm.value.username
      user.value.gender = profileForm.value.gender
      isEditingProfile.value = false
    }
  } catch {
    // 静默失败
  }
}
const removeHobby = async (index) => {
  const list = [...hobbyList.value]; list.splice(index, 1)
  user.value.hobbies = list.join(',')
  try {
    await request.post(API.USER_UPDATE_HOBBIES, { hobbies: user.value.hobbies })
  } catch {
    // 静默失败
  }
}
const addHobby = async () => {
  if (hobbyInputValue.value.trim()) {
    const list = [...hobbyList.value, hobbyInputValue.value.trim()]
    user.value.hobbies = list.join(',')
    try {
      await request.post(API.USER_UPDATE_HOBBIES, { hobbies: user.value.hobbies })
    } catch {
    // 静默失败
    }
  }
  hobbyInputValue.value = ''
  isAddingHobby.value = false
}
const changeAIType = async (type) => {
  user.value.ai_type = type
  showAITypeSelector.value = false
  try {
    await request.post(API.USER_UPDATE_AI_TYPE, { ai_type: type })
  } catch {
    // 静默失败
  }
}
</script>

<template>
  <div class="subspace-about">
    <div v-if="loading" class="loading-overlay">
      <div class="loading-spinner"></div>
      <p class="loading-text">加载用户数据...</p>
    </div>

    <div class="glass-section data-grid stagger-1 relative-section">
      <div class="section-tag">IDENTITY_CARD //</div>
      <div class="profile-main">
        <div class="avatar-area" @click="triggerAvatarUpload">
          <img v-if="user.avatar_url" :src="user.avatar_url" class="avatar-circle avatar-img" alt="avatar" />
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
            <div class="info-row"><span class="label">昵称</span><span class="value">{{ user.username }}</span></div>
            <div class="info-row">
              <span class="label">核心属性</span>
              <span class="value">{{ user.gender === 1 ? 'MALE' : 'FEMALE' }}</span>
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
          <div class="memo-box">{{ user.user_profile }}</div>
          <div class="action-row"><span class="action-btn">重新绘制 //</span></div>
        </div>
        <div v-if="user.ai_image_url" class="perception-poster">
          <img :src="user.ai_image_url" alt="AI Perception" />
          <div class="poster-overlay"></div>
        </div>
      </div>
    </div>

    <div class="glass-section ai-impression stagger-5">
      <div class="section-tag">AI_IMPRESSION_TAGS //</div>
      <div class="memo-box">她眼中的你</div>
      <div class="tag-cloud readonly">
        <span v-for="(tag, index) in user.ai_tags" :key="'ai-tag-' + tag + '-' + index" class="ai-pixel-tag">
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
        <div class="status-item">CREATED_AT: {{ new Date(user.created_at).toLocaleDateString() }}</div>
        <div class="status-item">UPDATED_AT: {{ new Date(user.updated_at).toLocaleDateString() }}</div>
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