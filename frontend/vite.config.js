import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    vueDevTools(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: process.env.VITE_API_TARGET || 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
    }
  },
  build: {
    // 生产构建优化
    target: 'es2020',
    minify: 'esbuild',
    cssMinify: 'esbuild',
    rollupOptions: {
      output: {
        // 代码分割：三方库独立 chunk
        manualChunks: {
          vendor: ['vue', 'vue-router', 'pinia'],
          live2d: ['oh-my-live2d'],
          stomp: ['sockjs-client', 'stompjs'],
          gsap: ['gsap'],
        },
        // 长效缓存 hash
        entryFileNames: 'assets/[name]-[hash].js',
        chunkFileNames: 'assets/[name]-[hash].js',
        assetFileNames: 'assets/[name]-[hash][extname]',
      },
    },
    // 生成 sourcemap 用于线上调试（不包含源码）
    sourcemap: false,
    // 警告体积阈值
    chunkSizeWarningLimit: 400,
  },
})
