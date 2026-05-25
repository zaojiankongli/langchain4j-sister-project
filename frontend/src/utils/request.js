import axios from 'axios';
import router from '@/router';
import { STORAGE_KEYS } from '@/config/storage';
import { API } from '@/config/api';

// 创建 axios 实例
const request = axios.create({
  baseURL: '/api', // 基础路径
  timeout: 60000, // 超时时间 60 秒（后端 LLM 调用最多 30 秒 + RAG 检索 30 秒）
});

// 请求拦截器：添加 token
request.interceptors.request.use(
  (config) => {
    // 直接从 localStorage 读取，避免 useAuthStore() 时序问题
    const accessToken = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN);
    if (accessToken) {
      config.headers.Authorization = `Bearer ${accessToken}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Token 刷新状态管理
let isRefreshing = false;
let pendingRequests = [];

function onTokenRefreshed(newAccessToken) {
  pendingRequests.forEach(({ config, resolve, reject }) => {
    config.headers.Authorization = `Bearer ${newAccessToken}`;
    resolve(request(config));
  });
  pendingRequests = [];
}

function onRefreshFailed(error) {
  pendingRequests.forEach(({ reject }) => reject(error));
  pendingRequests = [];
}

// 响应拦截器：处理 token 过期 & 滑动过期
request.interceptors.response.use(
  (response) => {
    // 滑动过期：检查响应头是否有新 token
    const newAccessToken = response.headers?.['new-access-token'];
    if (newAccessToken) {
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, newAccessToken);
    }
    // 直接返回 data，简化调用
    return response.data;
  },
  (error) => {
    const originalConfig = error.config;

    // 401：token 过期或无效
    if (error.response?.status === 401 && !originalConfig._retry) {
      const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN);

      if (refreshToken) {
        if (isRefreshing) {
          // 正在刷新中，将请求加入等待队列
          return new Promise((resolve, reject) => {
            pendingRequests.push({ config: originalConfig, resolve, reject });
          });
        }

        originalConfig._retry = true;
        isRefreshing = true;

        return axios.post('/api' + API.AUTH_REFRESH, { refreshToken })
          .then((response) => {
            if (response.data && response.data.code === 200) {
              const { accessToken, refreshToken: newRefreshToken } = response.data.data;
              localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken);
              localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, newRefreshToken);
              onTokenRefreshed(accessToken);
              originalConfig.headers.Authorization = `Bearer ${accessToken}`;
              return request(originalConfig);
            }
          })
          .catch((refreshError) => {
            onRefreshFailed(refreshError);
            localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
            localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
            localStorage.removeItem(STORAGE_KEYS.USER);
            router.push('/login');
            return Promise.reject(refreshError);
          })
          .finally(() => {
            isRefreshing = false;
          });
      }
      // 没有 refresh token，清除并跳转到登录页
      localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN);
      localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN);
      localStorage.removeItem(STORAGE_KEYS.USER);
      router.push('/login');
    }

    // 返回错误信息
    return Promise.reject(error.response?.data || error);
  }
);

export default request;
