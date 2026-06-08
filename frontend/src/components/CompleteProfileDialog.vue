<script setup>
import { ref, reactive, watch, onBeforeUnmount, computed } from 'vue';
import request from '@/utils/request';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { API } from '@/config/api';

const router = useRouter();

let stageTimer = null;
let dialogTimeout = null;
let uploadDelayTimer = null;
let closeDelayTimer = null;
let _isMounted = true

// ==========================================
// 1. 基础逻辑与显示控制
// ==========================================
const props = defineProps({
   modelValue: { type: Boolean, default: false },
   userData: { type: Object, default: null }
 });
const emit = defineEmits(['update:modelValue', 'success']);

const step = ref(0);
const isSaving = ref(false);
const saveError = ref('');
const uploadError = ref('');
const uploadProgress = ref(0);
const syncStage = ref(0);
const syncStages = [
  '正在同步记忆回路...',
  '构建情感链路...',
  '校准核心参数...',
  '激活神经接口...',
  '连接准备就绪'
];
const profile = reactive({
  username: '',
  gender: null,   // 用户性别
  aiType: 2,      // AI身份：目前固定为2(妹妹)
  birthday: '',
  hobbies: [],
  avatarUrl: ''
});

// 模拟 AI 对话文本 (更具治愈感和引导性)
const aiMessages = [
  "你好……这是我们第一次正式见面吧？",                     // Step 0
  "我该怎么称呼你呢？",                                   // Step 1
  "在这个时空里，你希望我以什么样的身份陪伴你？",           // Step 2 (新增身份选择)
  "我明白了。那么，我可以怎么定义你呢？",                  // Step 3 (用户性别/称呼)
  "你的诞生日……我想把它记在核心里，可以告诉我吗？",         // Step 4
  "你平时……有什么特别喜欢做的事情吗？",                  // Step 5
  "最后，我可以看看你的样子吗？",                         // Step 6
  "正在同步记忆回路……请稍后。"                           // Step 7
];

// 监听场景开启
watch(() => props.modelValue, (newVal) => {
  if (newVal) {
    // 清除旧定时器，防止快速重开导致 step 被旧定时器提前跳转
    if (dialogTimeout) { clearTimeout(dialogTimeout); dialogTimeout = null; }
    step.value = 0;
    dialogTimeout = setTimeout(() => { step.value = 1; }, 2200);
  }
}, { immediate: true });

// ==========================================
// 2. 交互处理
// ==========================================
// ── 自定义日期选择器 ──
const currentYear = new Date().getFullYear()
const years = Array.from({ length: 101 }, (_, i) => currentYear - 100 + i) // 静态数组，免 GC
const months = ['1月', '2月', '3月', '4月', '5月', '6月', '7月', '8月', '9月', '10月', '11月', '12月']
const dateParts = reactive({ year: '', month: '', day: '' })
const daysInMonth = computed(() => {
  const y = parseInt(dateParts.year)
  const m = parseInt(dateParts.month)
  if (!y || !m) return []
  return Array.from({ length: new Date(y, m, 0).getDate() }, (_, i) => i + 1)
})
const confirmBirthday = () => {
  if (dateParts.year && dateParts.month && dateParts.day) {
    profile.birthday = `${dateParts.year}-${String(dateParts.month).padStart(2, '0')}-${String(dateParts.day).padStart(2, '0')}`
  }
  nextStep()
}

const hobbyPool = ref(['音乐', '游戏', '摄影', '旅行', '编程', '绘画', '运动', '料理']);
const customHobby = ref('');

const toggleHobby = (tag) => {
  const i = profile.hobbies.indexOf(tag);
  if (i > -1) profile.hobbies.splice(i, 1);
  else if (profile.hobbies.length < 5) profile.hobbies.push(tag);
};

const addCustomHobby = () => {
  if (customHobby.value) {
    if (!hobbyPool.value.includes(customHobby.value)) {
      hobbyPool.value.push(customHobby.value);
    }
    toggleHobby(customHobby.value);
    customHobby.value = '';
  }
};

const MAX_AVATAR_SIZE = 5 * 1024 * 1024 // 5MB
const ALLOWED_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

const handleAvatarUpload = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  // 前置校验 — 避免无效请求浪费带宽
  if (!ALLOWED_TYPES.includes(file.type)) {
    uploadError.value = '仅支持 JPEG/PNG/WebP/GIF 格式';
    return;
  }
  if (file.size > MAX_AVATAR_SIZE) {
    uploadError.value = '头像图片不能超过 5MB';
    return;
  }
  uploadError.value = '';
  uploadProgress.value = 0;
  try {
    const formData = new FormData();
    formData.append('file', file);
    const res = await request.post(API.USER_AVATAR, formData, {
      onUploadProgress: (progressEvent) => {
        uploadProgress.value = Math.round((progressEvent.loaded * 100) / progressEvent.total);
      }
    });
    uploadProgress.value = 100;
    // 短暂展示完成态，然后推进
    await new Promise(r => { uploadDelayTimer = setTimeout(r, 150); });
    if (!_isMounted) return
    if (res.code === 200) {
      profile.avatarUrl = res.data;
      nextStep();
    } else {
      uploadError.value = res.message || '头像上传失败，请重试';
      uploadProgress.value = 0;
    }
  } catch (err) {
    uploadError.value = '头像上传失败，请检查网络后重试';
    uploadProgress.value = 0;
  }
};

const selectMale = () => { profile.gender = 1; nextStep() }
const selectFemale = () => { profile.gender = 2; nextStep() }
const retrySubmit = () => { saveError.value = ''; submitData() }

const nextStep = () => {
  step.value++;
  if (step.value === 7) submitData();
};

const submitData = async () => {
  if (isSaving.value) return;
  isSaving.value = true;
  saveError.value = '';
  // Play sync stages sequentially for visual feedback
  syncStage.value = 0;
  stageTimer = setInterval(() => {
    syncStage.value = Math.min(syncStage.value + 1, syncStages.length - 1);
  }, 800);
  try {
      const res = await request.post(API.AUTH_COMPLETE_PROFILE, {
        ...profile,
        hobbies: profile.hobbies
      });
      syncStage.value = syncStages.length - 1; // 完成
      if (res.code === 200) {
        // 通过 authStore 同步更新 store 状态和 localStorage
        const authStore = useAuthStore();
        authStore.setTokens(authStore.accessToken, authStore.refreshToken, { ...authStore.user, ...profile });
        // 短暂展示完成状态后跳转
        await new Promise(r => { closeDelayTimer = setTimeout(r, 1500); });
        if (!_isMounted) return;
        emit('success');
        emit('update:modelValue', false);
      } else {
        saveError.value = res.message || '保存失败，请稍后重试';
      }
    } catch (err) {
      saveError.value = err.message || '保存失败，请检查网络连接';
    } finally {
      if (stageTimer) { clearInterval(stageTimer); stageTimer = null; }
      isSaving.value = false;
    }
};

onBeforeUnmount(() => {
  _isMounted = false;
  if (stageTimer) { clearInterval(stageTimer); stageTimer = null; }
  if (dialogTimeout) { clearTimeout(dialogTimeout); dialogTimeout = null; }
  if (uploadDelayTimer) { clearTimeout(uploadDelayTimer); uploadDelayTimer = null; }
  if (closeDelayTimer) { clearTimeout(closeDelayTimer); closeDelayTimer = null; }
});
</script>

<template>
  <transition name="fade">
    <div v-if="modelValue" class="scene-container">
      <div class="background-overlay" @click="$emit('update:modelValue', false)"></div>

      <div class="dialog-stage">
        <div class="character-box">
          <div class="character-inner">
            <div class="glow-sphere"></div>
          </div>
        </div>

        <div class="ai-bubble">
          <transition name="slide-fade" mode="out-in">
            <p :key="step">{{ aiMessages[step] }}</p>
          </transition>
        </div>

        <div class="user-action-area">
          <transition name="action-pop" mode="out-in">

            <div v-if="step === 1" class="action-box">
              <input v-model="profile.username" placeholder="输入识别名..." class="glass-input" @keyup.enter="nextStep" />
              <button class="glass-btn" @click="nextStep">这就是我的名字</button>
            </div>

            <div v-else-if="step === 2" class="action-box wide">
              <div class="identity-grid">
                <button class="id-card active" @click="nextStep">
                  <div class="id-tag">INITIALIZED</div>
                  <span class="cn">妹妹</span>
                  <span class="desc">温柔内敛 · 极度依赖</span>
                </button>
                <button class="id-card disabled">
                  <div class="id-tag locked">LOCKED</div>
                  <span class="cn">青梅</span>
                  <span class="desc">活泼开朗 · 敬请期待</span>
                </button>
              </div>
            </div>

            <div v-else-if="step === 3" class="action-box row">
              <button class="option-card" @click="selectMale">
                <span class="en">MALE</span>
                <span class="cn">我是哥哥</span>
              </button>
              <button class="option-card" @click="selectFemale">
                <span class="en">FEMALE</span>
                <span class="cn">我是姐姐</span>
              </button>
            </div>

            <div v-else-if="step === 4" class="action-box">
              <div class="date-picker-group">
                <span class="picker-icon">📅</span>
                <select v-model="dateParts.year" class="glass-select">
                  <option value="" disabled hidden>年</option>
                  <option v-for="y in years" :key="y" :value="y">{{ y }}</option>
                </select>
                <span class="sep">/</span>
                <select v-model="dateParts.month" class="glass-select narrow">
                  <option value="" disabled hidden>月</option>
                  <option v-for="(m, i) in months" :key="i" :value="i + 1">{{ m }}</option>
                </select>
                <span class="sep">/</span>
                <select v-model="dateParts.day" class="glass-select narrow">
                  <option value="" disabled hidden>日</option>
                  <option v-for="d in daysInMonth" :key="d" :value="d">{{ d }}</option>
                </select>
              </div>
              <div class="btn-group">
                <button class="glass-btn" @click="confirmBirthday">确认日期</button>
                <button class="text-btn" @click="nextStep">不方便透露</button>
              </div>
            </div>

            <div v-else-if="step === 5" class="action-box wide">
              <div class="tag-cloud">
                <button v-for="h in hobbyPool" :key="h"
                        class="tag-item" :class="{ active: profile.hobbies.includes(h) }"
                        @click="toggleHobby(h)">{{ h }}</button>
                <input v-model="customHobby" placeholder="+自定义" class="inline-tag-input" @keyup.enter="addCustomHobby" />
              </div>
              <button class="glass-btn" @click="nextStep">同步兴趣偏好</button>
            </div>

            <div v-else-if="step === 6" class="action-box">
              <label class="upload-trigger">
                <input type="file" hidden @change="handleAvatarUpload" accept="image/*" />
                <div class="upload-box" :class="{ 'uploading': uploadProgress > 0 && uploadProgress < 100, 'done': uploadProgress === 100 }">
                  <template v-if="uploadProgress === 0">
                    <span class="icon">✦</span>
                    <span class="txt">上传形象数据</span>
                  </template>
                  <template v-else-if="uploadProgress === 100">
                    <span class="icon">✓</span>
                    <span class="txt">上传完成</span>
                  </template>
                  <template v-else>
                    <svg class="progress-ring" viewBox="0 0 40 40">
                      <circle class="progress-ring-bg" cx="20" cy="20" r="17" />
                      <circle class="progress-ring-fill" cx="20" cy="20" r="17"
                        :style="{ strokeDashoffset: 106.8 - (106.8 * uploadProgress / 100) }" />
                    </svg>
                    <span class="progress-pct">{{ uploadProgress }}%</span>
                  </template>
                </div>
              </label>
              <div v-if="uploadError" class="error-msg">{{ uploadError }}</div>
              <button class="text-btn" :disabled="uploadProgress > 0 && uploadProgress < 100" @click="nextStep">使用默认识别码</button>
            </div>

            <div v-else-if="step === 7" class="action-box">
              <div class="sync-status">
                <div class="sync-stages">
                  <div v-for="(stage, i) in syncStages" :key="i" class="sync-stage-row"
                    :class="{ past: i < syncStage, current: i === syncStage, future: i > syncStage }">
                    <div class="sync-dot">
                      <span v-if="i < syncStage" class="dot-check">✓</span>
                      <span v-else-if="i === syncStage" class="dot-pulse"></span>
                      <span v-else class="dot-empty"></span>
                    </div>
                    <span class="sync-label">{{ stage }}</span>
                  </div>
                </div>
              </div>
              <div v-if="saveError" class="error-msg">{{ saveError }}</div>
              <button v-if="saveError" class="text-btn" @click="retrySubmit">返回重试</button>
            </div>

          </transition>
        </div>
      </div>
    </div>
  </transition>
</template>

<style scoped>
/* 容器布局 */
.scene-container {
  position: fixed; inset: 0; z-index: 9999;
  display: flex; align-items: center; justify-content: center;
  overflow: hidden; font-family: 'PingFang SC', sans-serif;
}

.background-overlay {
  position: absolute; inset: 0;
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(12px) brightness(0.9);
  z-index: -1;
}

.dialog-stage { width: 100%; max-width: 700px; display: flex; flex-direction: column; align-items: center; }

/* 角色与气泡 */
.character-box { height: 220px; display: flex; align-items: center; }
.character-inner { position: relative; width: 100px; height: 100px; }
.glow-sphere {
  width: 100%; height: 100%; border-radius: 50%;
  background: radial-gradient(circle, #fff 0%, transparent 70%);
  filter: blur(10px); animation: breathe 3s infinite ease-in-out;
}
@keyframes breathe { 0%, 100% { opacity: 0.3; transform: scale(1); } 50% { opacity: 0.6; transform: scale(1.2); } }

.ai-bubble {
  background: rgba(255, 255, 255, 0.7); backdrop-filter: blur(6px);
  padding: 25px 45px; border-radius: 4px; margin-bottom: 40px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 10px 40px rgba(0,0,0,0.03);
}
.ai-bubble p { color: #444; font-size: 17px; margin: 0; text-align: center; font-weight: 300; letter-spacing: 1px; }

/* 交互卡片 - AI身份选择 */
.identity-grid { display: flex; gap: 15px; width: 100%; justify-content: center; }
.id-card {
  flex: 1; max-width: 180px; padding: 25px 15px;
  background: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.8);
  border-radius: 12px; cursor: pointer; transition: background-color 0.3s ease, opacity 0.3s ease, filter 0.3s ease, transform 0.3s ease;
  display: flex; flex-direction: column; align-items: center; position: relative;
}
.id-card.active:hover { background: #fff; transform: translateY(-5px); }
.id-card.disabled { opacity: 0.5; cursor: not-allowed; filter: grayscale(1); }
.id-tag { position: absolute; top: 10px; font-size: 8px; letter-spacing: 1px; color: #87ceeb; }
.id-tag.locked { color: #999; }
.id-card .cn { font-size: 18px; color: #333; margin: 10px 0 5px; font-weight: 600; }
.id-card .desc { font-size: 11px; color: #888; text-align: center; line-height: 1.4; }

/* 通用交互件 */
.action-box { display: flex; flex-direction: column; align-items: center; gap: 25px; width: 100%; }
.action-box.row { flex-direction: row; justify-content: center; }

.glass-input {
  background: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.6);
  padding: 14px 25px; border-radius: 8px; width: 320px;
  font-size: 16px; outline: none; transition: background-color 0.3s ease, border-color 0.3s ease; text-align: center;
}
.glass-input:focus { background: #fff; border-color: #fff; }

.glass-btn {
  background: #333; color: #fff; border: none; padding: 12px 40px;
  border-radius: 50px; font-size: 13px; letter-spacing: 2px;
  cursor: pointer; transition: background-color 0.3s ease, box-shadow 0.3s ease, transform 0.3s ease;
}
.glass-btn:hover { background: #000; transform: translateY(-2px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); }

/* 选项卡片 (用户身份) */
.option-card {
  background: rgba(255,255,255,0.5); border: 1px solid rgba(255,255,255,0.8);
  padding: 20px 45px; border-radius: 12px; cursor: pointer; transition: background-color 0.3s ease, transform 0.3s ease;
  display: flex; flex-direction: column; align-items: center;
}
.option-card:hover { background: #fff; transform: translateY(-5px); }
.option-card .en { font-size: 9px; color: #aaa; letter-spacing: 2px; }
.option-card .cn { font-size: 16px; color: #333; margin-top: 5px; }

/* ── 自定义日期选择器 (3-select glassmorphism) ── */
.date-picker-group {
  display: flex; align-items: center; gap: 6px;
  background: rgba(255,255,255,0.4); backdrop-filter: blur(6px);
  border: 1px solid rgba(255,255,255,0.6);
  border-radius: 12px; padding: 8px 16px;
  transition: background-color 0.3s ease, border-color 0.3s ease, box-shadow 0.3s ease;
}
.date-picker-group:focus-within {
  background: #fff; border-color: #fff;
  box-shadow: 0 4px 20px rgba(0,0,0,0.06);
}
.picker-icon { font-size: 16px; line-height: 1; margin-right: 2px; user-select: none; }
.sep { color: #bbb; font-size: 14px; font-weight: 300; user-select: none; }

.glass-select {
  appearance: none; -webkit-appearance: none;
  background: transparent; border: none; outline: none;
  font-size: 15px; color: #444; text-align: center;
  cursor: pointer; padding: 6px 20px 6px 4px;
  font-family: 'SF Mono', 'PingFang SC', monospace;
  letter-spacing: 0.5px;
  /* 自定义下拉箭头 (SVG inline) */
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='10' height='6' fill='none'%3E%3Cpath d='M1 1l4 4 4-4' stroke='%23999' stroke-width='1.5' stroke-linecap='round'/%3E%3C/svg%3E");
  background-repeat: no-repeat; background-position: right 4px center;
  background-size: 10px 6px;
  min-width: 70px; transition: color 0.2s;
}
.glass-select.narrow { min-width: 50px; }
.glass-select:hover { color: #000; }
.glass-select:focus { color: #000; }
/* 选中态微动效 */
.glass-select option { color: #333; background: #fff; padding: 4px 8px; }

/* 响应式缩小 */
@media (max-width: 480px) {
  .date-picker-group { padding: 6px 10px; gap: 4px; }
  .glass-select { font-size: 13px; min-width: 56px; padding: 4px 16px 4px 2px; }
  .glass-select.narrow { min-width: 40px; }
  .picker-icon { font-size: 14px; }
}

/* 爱好标签云 */
.tag-cloud { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; max-width: 500px; }
.tag-item {
  padding: 8px 18px; border-radius: 20px; font-size: 13px;
  background: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.6);
  color: #666; cursor: pointer; transition: background-color 0.3s ease, border-color 0.3s ease, color 0.3s ease;
}
.tag-item.active { background: #333; color: #fff; border-color: #333; }
.inline-tag-input {
  background: transparent; border: none; border-bottom: 1px solid #ccc;
  width: 70px; font-size: 13px; padding: 5px; outline: none; margin-left: 10px;
}

/* 头像上传 */
.upload-box {
  width: 160px; height: 100px; border: 1px dashed #ccc;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  border-radius: 12px; cursor: pointer; transition: background-color 0.3s ease, border-color 0.3s ease; position: relative;
}
.upload-box:hover { background: rgba(255,255,255,0.5); border-color: #888; }
.upload-box .icon { font-size: 24px; color: #888; margin-bottom: 8px; }
.upload-box .txt { font-size: 12px; color: #999; }

/* 上传进度圆环 */
.upload-box.uploading { border-color: #333; background: rgba(255,255,255,0.6); cursor: default; }
.upload-box.done { border-color: #52c41a; border-style: solid; background: rgba(82,194,26,0.05); }
.upload-box.done .icon { color: #52c41a; }
.upload-box.done .txt { color: #52c41a; }
.progress-ring { width: 44px; height: 44px; transform: rotate(-90deg); }
.progress-ring-bg { fill: none; stroke: #eee; stroke-width: 3; }
.progress-ring-fill { fill: none; stroke: #333; stroke-width: 3; stroke-dasharray: 106.8; stroke-linecap: round; transition: stroke-dashoffset 0.3s ease; }
.progress-pct { position: absolute; font-size: 11px; color: #333; font-weight: 600; }

/* 底部状态 — 多阶段同步 */
.sync-status { display: flex; flex-direction: column; align-items: flex-start; gap: 0; width: 280px; }
.sync-stages { display: flex; flex-direction: column; gap: 14px; width: 100%; padding: 8px 0; }
.sync-stage-row { display: flex; align-items: center; gap: 12px; transition: opacity 0.4s ease; }
.sync-stage-row.future { opacity: 0.3; }
.sync-stage-row.current { opacity: 1; }
.sync-stage-row.past { opacity: 0.65; }
.sync-dot { width: 18px; height: 18px; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.dot-check { font-size: 12px; color: #52c41a; }
.dot-pulse { width: 8px; height: 8px; background: #333; border-radius: 50%; animation: syncPulse 1s infinite; }
.dot-empty { width: 6px; height: 6px; background: #ccc; border-radius: 50%; }
.sync-label { font-size: 13px; color: #555; letter-spacing: 0.5px; }
@keyframes syncPulse { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.5); opacity: 0.5; } }

.error-msg { color: #e74c3c; font-size: 12px; text-align: center; padding: 8px 12px; background: rgba(231,76,60,0.1); border-radius: 4px; }

/* 动画过渡 */
.fade-enter-active { transition: opacity 1s; }
.fade-leave-active { transition: opacity 0.4s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.slide-fade-enter-active { transition: opacity 0.5s ease-out, transform 0.5s ease-out; }
.slide-fade-enter-from { opacity: 0; transform: translateY(10px); }

.action-pop-enter-active { transition: opacity 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275), transform 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
.action-pop-enter-from { opacity: 0; transform: scale(0.9) translateY(20px); }

.text-btn { background: transparent; border: none; color: #999; font-size: 12px; cursor: pointer; text-decoration: underline; }
</style>
