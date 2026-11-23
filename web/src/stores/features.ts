import { defineStore } from 'pinia'
import { api } from '../api/client'

/** 管理员关闭的功能（当前登录角色）。菜单、首页入口、路由守卫都靠它。 */
export const useFeatureStore = defineStore('features', {
  state: () => ({ disabled: [] as string[], loaded: false }),
  getters: {
    isOpen: (s) => (key?: string) => !key || !s.disabled.includes(key)
  },
  actions: {
    async load(force = false) {
      if (this.loaded && !force) return
      try {
        const r = await api.get('/features/mine')
        this.disabled = r.disabled || []
        this.loaded = true
      } catch {
        /* 未登录时忽略 */
      }
    },
    reset() {
      this.disabled = []
      this.loaded = false
    }
  }
})
