import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'globalThis',
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks(id: string) {
          if (id.includes('node_modules/vue') || id.includes('node_modules/@vue')) {
            return 'vendor-vue'
          }
          if (id.includes('node_modules/pixi-live2d-display')) {
            return 'vendor-live2d'
          }
          if (id.includes('node_modules/live2dcubismcore')) {
            return 'vendor-live2d'
          }
          if (id.includes('node_modules/pixi') || id.includes('node_modules/@pixi')) {
            return 'vendor-pixi-core'
          }
          if (id.includes('node_modules/sockjs') || id.includes('node_modules/@stomp')) {
            return 'vendor-network'
          }
        },
      },
    },
  },
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true,
        ws: true,
      },
    },
  },
})
