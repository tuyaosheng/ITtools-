import { setActivePinia, createPinia } from 'pinia'
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { useAuthStore } from '../auth'
import { http } from '../../api/http'

vi.mock('../../api/http', () => ({ http: { get: vi.fn(), post: vi.fn() } }))

describe('auth store', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('stores user after fetchMe', async () => {
    ;(http.get as any).mockResolvedValue({ data: { code: 0, data: { userId: 1, name: 'admin', role: 'ADMIN', permissions: ['ADMIN_USERS'] } } })
    const store = useAuthStore()
    await store.fetchMe()
    expect(store.isAuthenticated).toBe(true)
    expect(store.hasPermission('ADMIN_USERS')).toBe(true)
  })
})
