<script setup>
import { ref, reactive, watch } from 'vue';
import request from '@/utils/request';
import { useRouter } from 'vue-router';
import { API } from '@/config/api';

const router = useRouter();

// ==========================================
// 1. 基础逻辑与显示控制
// ==========================================
const props = defineProps({
  modelValue: { type: Boolean, default: false }
});
const emit = defineEmits(['update:modelValue', 'success']);

const step = ref(0);
const isSaving = ref(false);
const saveError = ref('');
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
    step.value = 0;
    setTimeout(() => { step.value = 1; }, 2200);
  }
}, { immediate: true });

// ==========================================
// 2. 交互处理
// ==========================================
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

const handleAvatarUpload = async (e) => {
  const file = e.target.files[0];
  if (!file) return;
  const formData = new FormData();
  formData.append('file', file);
  try {
    const res = await request.post(API.USER_AVATAR, formData);
    if (res.code === 200) {
      profile.avatarUrl = res.data;
      nextStep();
    }
  } catch { /* upload failure is non-blocking, user can skip */ }
};

const nextStep = () => {
  step.value++;
  if (step.value === 7) submitData();
};

const submitData = async () => {
  if (isSaving.value) return;
  isSaving.value = true;
  saveError.value = '';
  try {
    const res = await request.post(API.AUTH_COMPLETE_PROFILE, {
      ...profile,
      hobbies: profile.hobbies.join(',')
    });
    if (res.code === 200) {
      setTimeout(() => {
        emit('success');
        emit('update:modelValue', false);
        router.push('/dashboard');
      }, 2000);
    } else {
      saveError.value = res.message || '保存失败，请稍后重试';
    }
  } catch (err) {
    saveError.value = err.message || '保存失败，请检查网络连接';
  } finally {
    isSaving.value = false;
  }
};
</script>

<template>
  <transition name="fade">
    <div v-if="modelValue" class="scene-container">
      <div class="background-overlay"></div>

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
              <button class="option-card" @click="() => { profile.gender=1; nextStep(); }">
                <span class="en">MALE</span>
                <span class="cn">我是哥哥</span>
              </button>
              <button class="option-card" @click="() => { profile.gender=2; nextStep(); }">
                <span class="en">FEMALE</span>
                <span class="cn">我是姐姐</span>
              </button>
            </div>

            <div v-else-if="step === 4" class="action-box">
              <input v-model="profile.birthday" type="date" class="glass-input" />
              <div class="btn-group">
                <button class="glass-btn" @click="nextStep">确认日期</button>
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
                <div class="upload-box">
                  <span class="icon">✦</span>
                  <span class="txt">上传形象数据</span>
                </div>
              </label>
              <button class="text-btn" @click="nextStep">使用默认识别码</button>
            </div>

            <div v-else-if="step === 7" class="action-box">
              <div class="sync-status">
                <div class="line-loader"></div>
                <span>{{ isSaving ? 'CORE_SYNCING...' : '同步完成' }}</span>
              </div>
              <div v-if="saveError" class="error-msg">{{ saveError }}</div>
              <button v-if="saveError" class="text-btn" @click="() => { step = 5; saveError = ''; }">返回重试</button>
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
  backdrop-filter: blur(25px) brightness(0.9);
  z-index: -1;
}

.dialog-stage { width: 100%; max-width: 700px; display: flex; flex-direction: column; align-items: center; }

/* 角色与气泡 */
.character-box { height: 220px; display: flex; align-items: center; }
.character-inner { position: relative; width: 100px; height: 100px; }
.glow-sphere {
  width: 100%; height: 100%; border-radius: 50%;
  background: radial-gradient(circle, #fff 0%, transparent 70%);
  filter: blur(20px); animation: breathe 3s infinite ease-in-out;
}
@keyframes breathe { 0%, 100% { opacity: 0.3; transform: scale(1); } 50% { opacity: 0.6; transform: scale(1.2); } }

.ai-bubble {
  background: rgba(255, 255, 255, 0.7); backdrop-filter: blur(10px);
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
  border-radius: 12px; cursor: pointer; transition: 0.3s;
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
  font-size: 16px; outline: none; transition: 0.3s; text-align: center;
}
.glass-input:focus { background: #fff; border-color: #fff; }

.glass-btn {
  background: #333; color: #fff; border: none; padding: 12px 40px;
  border-radius: 50px; font-size: 13px; letter-spacing: 2px;
  cursor: pointer; transition: 0.3s;
}
.glass-btn:hover { background: #000; transform: translateY(-2px); box-shadow: 0 5px 15px rgba(0,0,0,0.1); }

/* 选项卡片 (用户身份) */
.option-card {
  background: rgba(255,255,255,0.5); border: 1px solid rgba(255,255,255,0.8);
  padding: 20px 45px; border-radius: 12px; cursor: pointer; transition: 0.3s;
  display: flex; flex-direction: column; align-items: center;
}
.option-card:hover { background: #fff; transform: translateY(-5px); }
.option-card .en { font-size: 9px; color: #aaa; letter-spacing: 2px; }
.option-card .cn { font-size: 16px; color: #333; margin-top: 5px; }

/* 爱好标签云 */
.tag-cloud { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; max-width: 500px; }
.tag-item {
  padding: 8px 18px; border-radius: 20px; font-size: 13px;
  background: rgba(255,255,255,0.4); border: 1px solid rgba(255,255,255,0.6);
  color: #666; cursor: pointer; transition: 0.3s;
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
  border-radius: 12px; cursor: pointer; transition: 0.3s;
}
.upload-box:hover { background: rgba(255,255,255,0.5); border-color: #888; }
.upload-box .icon { font-size: 24px; color: #888; margin-bottom: 8px; }
.upload-box .txt { font-size: 12px; color: #999; }

/* 底部状态 */
.sync-status { display: flex; flex-direction: column; align-items: center; gap: 15px; }
.error-msg { color: #e74c3c; font-size: 12px; text-align: center; padding: 8px 12px; background: rgba(231,76,60,0.1); border-radius: 4px; }
.line-loader { width: 200px; height: 2px; background: rgba(0,0,0,0.05); position: relative; overflow: hidden; }
.line-loader::after {
  content: ''; position: absolute; left: -100%; width: 100%; height: 100%;
  background: #333; animation: lineSlide 2s infinite;
}
@keyframes lineSlide { to { left: 100%; } }
.sync-status span { font-size: 10px; color: #aaa; letter-spacing: 3px; }

/* 动画过渡 */
.fade-enter-active, .fade-leave-active { transition: opacity 1s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.slide-fade-enter-active { transition: all 0.5s ease-out; }
.slide-fade-enter-from { opacity: 0; transform: translateY(10px); }

.action-pop-enter-active { transition: all 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275); }
.action-pop-enter-from { opacity: 0; transform: scale(0.9) translateY(20px); }

.text-btn { background: transparent; border: none; color: #999; font-size: 12px; cursor: pointer; text-decoration: underline; }
</style>