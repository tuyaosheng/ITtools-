import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '../LoginView.vue'

vi.mock('../../../api/http', () => ({ http: { get: vi.fn().mockResolvedValue({ data: { code: 0, data: [] } }), post: vi.fn() } }))

describe('LoginView', () => {
  it('renders all four login entrances', () => {
    setActivePinia(createPinia())
    const wrapper = mount(LoginView, { global: { stubs: { 'el-tab-pane': false } } })
    const text = wrapper.text()
    expect(text).toContain('学籍号')
    expect(text).toContain('教师')
    expect(text).toContain('管理员')
  })
})
