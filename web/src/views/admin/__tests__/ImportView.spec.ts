import { mount, flushPromises } from '@vue/test-utils'
import { describe, it, expect, vi } from 'vitest'
import ImportView from '../ImportView.vue'

vi.mock('../../../api/admin', () => ({
  adminApi: {
    imports: {
      students: vi.fn().mockResolvedValue({ total: 0, imported: 0, failed: 0, errors: [] }),
      teachers: vi.fn().mockResolvedValue({ total: 0, imported: 0, failed: 0, errors: [] }),
      downloadStudentTemplate: vi.fn().mockResolvedValue(undefined),
      downloadTeacherTemplate: vi.fn().mockResolvedValue(undefined)
    }
  }
}))

// Element Plus isn't registered in the unit test; stub the two components that
// carry the header slot / click behaviour these assertions depend on.
const stubs = {
  'el-card': { template: '<div><div class="hdr"><slot name="header" /></div><slot /></div>' },
  // $attrs already carries the parent's onClick listener (+ data-test), so binding
  // it onto the native button is enough — no separate $emit (that would double-fire).
  'el-button': { inheritAttrs: false, template: '<button v-bind="$attrs"><slot /></button>' }
}

describe('ImportView', () => {
  it('renders both student and teacher import sections', async () => {
    const wrapper = mount(ImportView, { global: { stubs } })
    await flushPromises()
    const text = wrapper.text()
    expect(text).toContain('学生导入')
    expect(text).toContain('教师导入')
    expect(text).toContain('下载模板')
  })

  it('downloads the student template when the button is clicked', async () => {
    const { adminApi } = await import('../../../api/admin')
    const wrapper = mount(ImportView, { global: { stubs } })
    await wrapper.get('[data-test="dl-student-template"]').trigger('click')
    expect(adminApi.imports.downloadStudentTemplate).toHaveBeenCalledTimes(1)
  })
})
