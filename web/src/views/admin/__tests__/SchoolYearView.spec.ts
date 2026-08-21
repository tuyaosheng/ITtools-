import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import SchoolYearView from '../SchoolYearView.vue'

vi.mock('../../../api/admin', () => ({
  adminApi: { years: { list: vi.fn().mockResolvedValue([{ id: 1, yearCode: '2021', label: '2021级', active: true }]) } }
}))

describe('SchoolYearView', () => {
  it('lists years on mount', async () => {
    const wrapper = mount(SchoolYearView)
    await flushPromises()
    expect(wrapper.text()).toContain('2021级')
  })
})
