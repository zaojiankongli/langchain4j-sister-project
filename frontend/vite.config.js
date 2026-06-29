import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import viteCompression from 'vite-plugin-compression'

// https://vite.dev/config/
export default defineConfig({
  define: {
    // sockjs-client 在浏览器中依赖 Node.js global 对象
    global: 'globalThis',
  },
  plugins: [
    vue(),
    ...(process.env.NODE_ENV === 'development' ? [vueDevTools()] : []),
    // 生产构建启用 brotli/gzip 压缩
    viteCompression({
      algorithm: 'brotliCompress',
      threshold: 1024,
      deleteOriginFile: false,
      verbose: false,
    }),
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
      '/actuator': {
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
          stomp: ['sockjs-client', '@stomp/stompjs'],
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
