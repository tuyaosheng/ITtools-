import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5003,
    proxy: {
      '/api': 'http://localhost:8300',
      '/courseware': 'http://localhost:8300',
      '/site/': 'http://localhost:8300',
      // 课堂实时通道：后端只接受同源 WebSocket，开发代理时把 Origin 改写为后端地址
      '/ws': {
        target: 'http://localhost:8300',
        ws: true,
        changeOrigin: true,
        headers: { Origin: 'http://localhost:8300' }
      }
    }
  },
  test: {
    environment: 'jsdom'
  }
})
