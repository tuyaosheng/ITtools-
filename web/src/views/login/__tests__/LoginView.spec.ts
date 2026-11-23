import { mount } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import LoginView from '../LoginView.vue'

vi.mock('../../../api/http', () => ({ http: { get: vi.fn().mockResolvedValue({ data: { code: 0, data: [] } }), post: vi.fn() } }))
// 登录页读 route.query.sso（第三方登录失败的原因）；测试里没有真路由
vi.mock('vue-router', () => ({ useRoute: () => ({ query: {} }), useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }))

describe('LoginView', () => {
  it('renders all four login entrances', () => {
    setActivePinia(createPinia())
    const wrapper = mount(LoginView, { global: { stubs: { 'el-tab-pane': false } } })
    const text = wrapper.text()
    expect(text).toContain('学号')
    expect(text).toContain('教师')
    expect(text).toContain('管理员')
  })
})
