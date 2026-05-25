<script setup>
import { ref, computed, onUnmounted } from 'vue';
import { useRouter } from 'vue-router';
import { setToken } from '@/utils/auth';
import request from '@/utils/request';
import CompleteProfileDialog from '@/components/CompleteProfileDialog.vue';
import { API } from '@/config/api';

const router = useRouter();

// ==========================================
// 状态管理
// ==========================================
const email = ref('');
const code = ref('');
const isLoading = ref(false);
const errorMessage = ref('');
const successMessage = ref('');

const showProfileDialog = ref(false);
const userData = ref(null);

const countdown = ref(0);
const canSendCode = ref(true);
const errorField = ref(''); // 新增：用于记录哪个输入框触发了震动
let countdownTimer = null; // 保存定时器引用，用于组件卸载时清理

// ==========================================
// 交互反馈与校验逻辑
// ==========================================
const isEmailValid = computed(() => {
  const pattern = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$/;
  return pattern.test(email.value);
});

// 触发错误震动
const triggerError = (field, message) => {
  errorField.value = field;
  errorMessage.value = message;
  setTimeout(() => { errorField.value = ''; }, 400); // 400ms后移除震动class
};

const clearError = () => {
  errorMessage.value = '';
  errorField.value = '';
};

// ==========================================
// 业务逻辑
// ==========================================
const sendVerificationCode = async () => {
  clearError();
  if (!isEmailValid.value) {
    return triggerError('email', '请输入正确的邮箱地址');
  }

  try {
    const response = await request.post(API.AUTH_SEND_CODE, { email: email.value });
    if (response.code === 200) {
      successMessage.value = '验证码已发送，请查收邮箱';
      startCountdown();
    } else {
      triggerError('email', response.message || '发送失败');
    }
  } catch (error) {
    triggerError('email', error.message || '发送失败，请稍后重试');
  }
};

const startCountdown = () => {
  countdown.value = 60;
  canSendCode.value = false;
  countdownTimer = setInterval(() => {
    countdown.value--;
    if (countdown.value <= 0) {
      clearInterval(countdownTimer);
      countdownTimer = null;
      canSendCode.value = true;
    }
  }, 1000);
};

onUnmounted(() => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
});

const handleLogin = async () => {
  clearError();
  if (!isEmailValid.value) return triggerError('email', '请输入正确的邮箱地址');
  if (!code.value) return triggerError('code', '请输入验证码');

  try {
    isLoading.value = true;
    successMessage.value = '';

    const response = await request.post(API.AUTH_LOGIN, {
      email: email.value,
      code: code.value
    });

    if (response.code === 200) {
      const { accessToken, refreshToken, user, requiresProfileComplete } = response.data;
      setToken(accessToken, refreshToken, user);

      if (requiresProfileComplete) {
        userData.value = user;
        showProfileDialog.value = true;
        successMessage.value = '登录成功，请完善个人资料';
      } else {
        successMessage.value = '登录成功，正在同步记忆...';
        setTimeout(() => { router.push('/dashboard'); }, 1000);
      }
    } else {
      triggerError('code', response.message || '登录失败');
    }
  } catch (error) {
    triggerError('code', error.message || '登录失败，请稍后重试');
  } finally {
    isLoading.value = false;
  }
};

const handleProfileComplete = () => {
  router.push('/dashboard');
};
</script>

<template>
  <div class="login-container">
    <div class="bg-blur-layer"></div>

    <div class="login-card animate-sitewide-enter">
      <div class="login-header">
        <div class="section-tag">AUTH_SYSTEM //</div>
        <h1 class="login-title">知微 Zeeva</h1>
        <p class="login-subtitle">建立连接，唤醒属于你的记忆回路...</p>
      </div>

      <div class="login-form">

        <div class="wave-group" :class="{ 'error-shake': errorField === 'email' }">
          <input
              required
              v-model="email"
              type="email"
              class="input"
              @focus="clearError"
          >
          <span class="bar"></span>
          <label class="label">
            <span
                v-for="(char, index) in 'IDENTITY_EMAIL'"
                :key="index"
                class="label-char"
                :style="`--index: ${index}`"
            >{{ char === ' ' ? '&nbsp;' : char }}</span>
          </label>
        </div>

        <div class="code-container">
          <div class="wave-group" :class="{ 'error-shake': errorField === 'code' }">
            <input
                required
                v-model="code"
                type="text"
                maxlength="6"
                class="input"
                @focus="clearError"
            >
            <span class="bar"></span>
            <label class="label">
              <span
                  v-for="(char, index) in 'SYNC_CODE'"
                  :key="index"
                  class="label-char"
                  :style="`--index: ${index}`"
              >{{ char === ' ' ? '&nbsp;' : char }}</span>
            </label>
          </div>

          <button class="code-btn-ghost" :disabled="!canSendCode || isLoading" @click="sendVerificationCode">
            {{ canSendCode ? '获取指令' : `冷却 [${countdown}s]` }}
          </button>
        </div>

        <div class="msg-box">
          <transition name="fade">
            <div v-if="errorMessage" class="sys-message error">{{ errorMessage }}</div>
            <div v-else-if="successMessage" class="sys-message success">{{ successMessage }}</div>
          </transition>
        </div>

        <button class="login-btn-primary" :disabled="isLoading" @click="handleLogin">
          {{ isLoading ? 'CONNECTING...' : '建立连接 // LOGIN' }}
        </button>

        <div class="register-footer">
          <span class="footer-text">未注册账号自动注册</span>
        </div>
      </div>
    </div>

    <CompleteProfileDialog v-model="showProfileDialog" :user-data="userData" @success="handleProfileComplete" />
  </div>
</template>

<style scoped>
/* ==========================================
   全局容器与背景
   ========================================== */
.login-container {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  /* 替换为你的二次元房间原图 URL */
  background-image: url('https://images.unsplash.com/photo-1519681393784-d120267933ba?auto=format&fit=crop&w=1920&q=80');
  background-size: cover;
  background-position: center;
  position: relative;
  overflow: hidden;
  color: #ffffff;
  font-family: 'PingFang SC', 'Microsoft YaHei', sans-serif;
}

.bg-blur-layer {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.2);
  backdrop-filter: blur(15px);
  z-index: 0;
}

/* ==========================================
   登录卡片 (毛玻璃质感)
   ========================================== */
.login-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(25px);
  border: 1px solid rgba(255, 255, 255, 0.15);
  border-radius: 12px;
  padding: 50px 40px;
  width: 100%;
  max-width: 400px;
  box-shadow: 0 20px 40px rgba(0, 0, 0, 0.2);
  position: relative;
  z-index: 1;
}

/* 入场动画 */
.animate-sitewide-enter {
  animation: sitewideEnter 0.8s cubic-bezier(0.22, 1, 0.36, 1) both;
}
@keyframes sitewideEnter {
  from { opacity: 0; transform: translateY(20px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}

/* ==========================================
   排版与标题
   ========================================== */
.login-header { margin-bottom: 20px; }
.section-tag { font-size: 10px; color: rgba(255, 255, 255, 0.5); margin-bottom: 12px; letter-spacing: 2px; }
.login-title { font-size: 32px; font-weight: 300; color: #fff; margin: 0 0 8px 0; letter-spacing: 2px; }
.login-subtitle { color: rgba(255, 255, 255, 0.6); font-size: 13px; }

/* ==========================================
   Wave Group 核心动画特效 (已恢复)
   ========================================== */
.wave-group {
  position: relative;
  margin-top: 35px;
  margin-bottom: 25px;
  width: 100%;
}

.wave-group .input {
  font-size: 15px;
  padding: 10px 10px 10px 0;
  display: block;
  width: 100%;
  border: none;
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  background: transparent;
  color: #fff;
  transition: all 0.3s;
}

.wave-group .input:focus { outline: none; }

/* 标签波浪动画 */
.wave-group .label {
  color: rgba(255, 255, 255, 0.5);
  font-size: 12px;
  font-weight: normal;
  position: absolute;
  pointer-events: none;
  left: 0;
  top: 10px;
  display: flex;
  letter-spacing: 1px;
}

.wave-group .label-char {
  transition: 0.2s cubic-bezier(0.4, 0, 0.2, 1) all;
  transition-delay: calc(var(--index) * .03s);
}

/* 焦点或已输入状态下，文字弹起 */
.wave-group .input:focus ~ .label .label-char,
.wave-group .input:valid ~ .label .label-char {
  transform: translateY(-22px);
  font-size: 10px;
  color: #fff; /* 主界面风格：白色 */
}

/* 底部发光横条扩展动画 */
.wave-group .bar {
  position: absolute;
  display: block;
  width: 100%;
  bottom: 0;
  left: 0;
}

.wave-group .bar:before, .wave-group .bar:after {
  content: '';
  height: 1px;
  width: 0;
  bottom: 0px;
  position: absolute;
  background: #fff;
  transition: 0.3s ease all;
}

.wave-group .bar:before { left: 50%; }
.wave-group .bar:after { right: 50%; }

.wave-group .input:focus ~ .bar:before,
.wave-group .input:focus ~ .bar:after {
  width: 50%;
}

/* ==========================================
   验证码与按钮布局
   ========================================== */
.code-container {
  display: flex;
  align-items: flex-end; /* 让按钮和输入框底部对齐 */
  gap: 15px;
}

.code-btn-ghost {
  padding: 8px 12px;
  background: transparent;
  color: #fff;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 4px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.3s;
  white-space: nowrap;
  margin-bottom: 25px; /* 对齐 wave input 的底部边缘 */
  min-width: 80px;
}

.code-btn-ghost:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.1);
  border-color: #fff;
}
.code-btn-ghost:disabled { opacity: 0.4; cursor: not-allowed; }

/* ==========================================
   错误震动动画 (已恢复)
   ========================================== */
.error-shake .input { border-bottom-color: #ff4d4f !important; }
.error-shake .label .label-char { color: #ff4d4f !important; }

.error-shake {
  animation: shake 0.4s cubic-bezier(0.36, 0.07, 0.19, 0.97) both;
}

@keyframes shake {
  10%, 90% { transform: translate3d(-1px, 0, 0); }
  20%, 80% { transform: translate3d(2px, 0, 0); }
  30%, 50%, 70% { transform: translate3d(-4px, 0, 0); }
  40%, 60% { transform: translate3d(4px, 0, 0); }
}

/* ==========================================
   消息提示与系统按钮
   ========================================== */
.msg-box { min-height: 24px; margin-bottom: 20px; }
.sys-message { font-size: 11px; padding: 6px 10px; border-radius: 4px; border-left: 2px solid; }
.sys-message.error { background: rgba(255, 77, 79, 0.1); color: #ff4d4f; border-color: #ff4d4f; }
.sys-message.success { background: rgba(82, 196, 26, 0.1); color: #52c41a; border-color: #52c41a; }

.login-btn-primary {
  width: 100%;
  padding: 14px;
  background: rgba(255, 255, 255, 0.1);
  color: white;
  border: 1px solid rgba(255, 255, 255, 0.3);
  border-radius: 6px;
  font-size: 14px;
  letter-spacing: 2px;
  cursor: pointer;
  transition: all 0.3s;
  backdrop-filter: blur(5px);
}

.login-btn-primary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
}
.login-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

/* 页脚 */
.register-footer { text-align: center; margin-top: 24px; font-size: 12px; }
.footer-text { color: rgba(255, 255, 255, 0.5); }
.link { color: #fff; text-decoration: none; margin-left: 6px; border-bottom: 1px solid rgba(255,255,255,0.3); padding-bottom: 1px; transition: 0.3s;}
.link:hover { border-color: #fff; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-5px); }

@media (max-width: 480px) {
  .login-card { padding: 32px 24px; margin: 20px; }
  .login-title { font-size: 26px; }
}
</style>