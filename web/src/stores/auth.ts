import { defineStore } from 'pinia'
import { http } from '../api/http'

interface User {
  userId: number
  name: string
  role: string
  permissions: string[]
}

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null }),
  getters: {
    isAuthenticated: (s) => s.user !== null,
    hasPermission: (s) => (code: string) => !!s.user?.permissions.includes(code)
  },
  actions: {
    async fetchMe() {
      try {
        const { data } = await http.get('/auth/me')
        this.user = data.data
      } catch {
        this.user = null
      }
    },
    async login(payload: Record<string, unknown>) {
      const { data } = await http.post('/auth/login', payload)
      this.user = data.data
    },
    async logout() {
      await http.post('/auth/logout')
      this.user = null
    }
  }
})
