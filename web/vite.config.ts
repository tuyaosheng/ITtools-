import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5003,
    proxy: {
      '/api': 'http://localhost:8300'
    }
  },
  test: {
    environment: 'jsdom'
  }
})
