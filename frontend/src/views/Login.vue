<script setup>
import { ref, computed, onBeforeUnmount, defineAsyncComponent } from 'vue';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import request from '@/utils/request';
import { API } from '@/config/api';

const CompleteProfileDialog = defineAsyncComponent(() => import('@/components/CompleteProfileDialog.vue'));

const router = useRouter();

// ==========================================
// 状态管理
// ==========================================
const email = ref('');
const code = ref('');
const isLoading = ref(false);
const sendingCode = ref(false);       // 验证码发送中（与 isLoading 分离，实现即时反馈）
const errorMessage = ref('');
const successMessage = ref('');

const showProfileDialog = ref(false);
const userData = ref(null);

const countdown = ref(0);
const canSendCode = ref(true);
const errorField = ref('');

// 内部状态（非响应式）
let _isMounted = true;
let countdownTimer = null;
let navigationTimer = null;
let _errorTimer = null;
let codeAbortController = null;      // 用于取消上一次验证码请求

// ==========================================
// 交互反馈与校验逻辑
// ==========================================
const isEmailValid = computed(() => {
  const pattern = /^[a-zA-Z0-9_+&*-]+(?:\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\.)+[a-zA-Z]{2,7}$/;
  return pattern.test(email.value);
});

const triggerError = (field, message) => {
  errorField.value = field;
  errorMessage.value = message;
  successMessage.value = ''; // Clear stale successMessage to prevent it showing after error fades
  if (_errorTimer) clearTimeout(_errorTimer);
  _errorTimer = setTimeout(() => { errorMessage.value = ''; errorField.value = ''; _errorTimer = null; }, 400);
};

const clearError = () => {
  errorMessage.value = '';
  errorField.value = '';
};

// ==========================================
// 倒计时（乐观 UI：点击按钮立即开始）
// ==========================================
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

// ==========================================
// 业务逻辑
// ==========================================
const sendVerificationCode = async () => {
  clearError();
  if (!isEmailValid.value) {
    return triggerError('email', '请输入正确的邮箱地址');
  }
  if (sendingCode.value) return; // 请求进行中，忽略重复点击

  // ── 取消上一次未完成的请求 ──
  if (codeAbortController) codeAbortController.abort();

  // ── 乐观 UI：按钮立刻进入发送状态 + 倒计时 ──
  sendingCode.value = true;
  startCountdown();
  successMessage.value = '验证码发送中...';

  codeAbortController = new AbortController();
  const timeoutId = setTimeout(() => codeAbortController.abort(), 10000); // 10s 超时

  try {
    const response = await request.post(API.AUTH_SEND_CODE, { email: email.value }, {
      signal: codeAbortController.signal,
      _skipRefresh: true, // 登录页无 token，跳过 refresh 检查
    });
    clearTimeout(timeoutId);
    if (!_isMounted) return;

    if (response.code === 200) {
      successMessage.value = '验证码已发送，请查收邮箱';
    } else {
      // 失败：回滚倒计时
      stopCountdown();
      triggerError('email', response.message || '发送失败');
    }
  } catch (error) {
    clearTimeout(timeoutId);
    if (!_isMounted || error?.name === 'CanceledError' || error?.name === 'AbortError') return;

    const isTimeout = error?.code === 'ECONNABORTED' || error?.message?.includes('timeout');
    stopCountdown();
    triggerError('email', isTimeout ? '发送超时，请检查网络或稍后重试' : (error.message || '发送失败，请稍后重试'));
  } finally {
    if (_isMounted) sendingCode.value = false;
  }
};

const stopCountdown = () => {
  if (countdownTimer) {
    clearInterval(countdownTimer);
    countdownTimer = null;
  }
  canSendCode.value = true;
  countdown.value = 0;
  sendingCode.value = false;
};

const handleLogin = async () => {
  clearError();
  if (!isEmailValid.value) return triggerError('email', '请输入正确的邮箱地址');
  if (!code.value) return triggerError('code', '请输入验证码');

  try {
    isLoading.value = true;
    successMessage.value = '校验中...';

    const response = await request.post(API.AUTH_LOGIN, {
      email: email.value,
      code: code.value
    }, { _skipRefresh: true });
    if (!_isMounted) return;

    if (response.code === 200) {
      const { accessToken, refreshToken, user, requiresProfileComplete } = response.data;
      const authStore = useAuthStore();
      authStore.setTokens(accessToken, refreshToken, user, !requiresProfileComplete);

      if (requiresProfileComplete) {
        userData.value = user;
        showProfileDialog.value = true;
        successMessage.value = '登录成功，请完善个人资料';
      } else {
        successMessage.value = '登录成功，正在同步记忆...';
        // 延迟 1500ms 跳转：让用户看一眼成功提示的 toast 再切页面
        // 匹配 successMessage 的可读时长（约 1.2s）+ 余量
        navigationTimer = setTimeout(() => {
          if (_isMounted) router.push({ name: 'Dashboard' }).catch(() => {});
        }, 1500);
      }
    } else {
      triggerError('code', response.message || '登录失败');
    }
  } catch (error) {
    if (!_isMounted) return;
    triggerError('code', error.message || '登录失败，请稍后重试');
  } finally {
    if (_isMounted) isLoading.value = false;
  }
};

const handleProfileComplete = () => {
  useAuthStore().setProfileComplete(true);
  router.push({ name: 'Dashboard' }).catch(() => {});
};

// ── 生命周期：统一清理 ──
onBeforeUnmount(() => {
  _isMounted = false;
  if (codeAbortController) codeAbortController.abort();
  if (countdownTimer) { clearInterval(countdownTimer); countdownTimer = null; }
  if (navigationTimer) { clearTimeout(navigationTimer); navigationTimer = null; }
  if (_errorTimer) { clearTimeout(_errorTimer); _errorTimer = null; }
});
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
            >{{ char === ' ' ? '\u00A0' : char }}</span>
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
              >{{ char === ' ' ? '\u00A0' : char }}</span>
            </label>
          </div>

          <button class="code-btn-ghost" :disabled="!canSendCode || isLoading || sendingCode" @click="sendVerificationCode">
            {{ sendingCode ? '发送中...' : (canSendCode ? '获取指令' : `冷却 [${countdown}s]`) }}
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
  backdrop-filter: blur(8px);
  z-index: 0;
}

/* ==========================================
   登录卡片 (毛玻璃质感)
   ========================================== */
.login-card {
  background: rgba(255, 255, 255, 0.05);
  backdrop-filter: blur(12px);
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
  transition: border-bottom-color 0.3s ease, color 0.3s ease;
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
  transition: transform 0.2s cubic-bezier(0.4, 0, 0.2, 1), font-size 0.2s cubic-bezier(0.4, 0, 0.2, 1), color 0.2s cubic-bezier(0.4, 0, 0.2, 1);
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
  transition: width 0.3s ease;
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
  transition: background-color 0.3s ease, border-color 0.3s ease, opacity 0.3s ease;
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
  transition: background-color 0.3s ease, box-shadow 0.3s ease, opacity 0.3s ease;
  backdrop-filter: blur(3px);
}

.login-btn-primary:hover:not(:disabled) {
  background: rgba(255, 255, 255, 0.2);
  box-shadow: 0 0 15px rgba(255, 255, 255, 0.1);
}
.login-btn-primary:disabled { opacity: 0.5; cursor: not-allowed; }

/* 页脚 */
.register-footer { text-align: center; margin-top: 24px; font-size: 12px; }
.footer-text { color: rgba(255, 255, 255, 0.5); }
.link { color: #fff; text-decoration: none; margin-left: 6px; border-bottom: 1px solid rgba(255,255,255,0.3); padding-bottom: 1px; transition: border-color 0.3s ease;}
.link:hover { border-color: #fff; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.3s ease, transform 0.3s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; transform: translateY(-5px); }

@media (max-width: 480px) {
  .login-card { padding: 32px 24px; margin: 20px; }
  .login-title { font-size: 26px; }
}
</style>
