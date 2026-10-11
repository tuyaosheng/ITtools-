import { defineAsyncComponent, type Component } from 'vue'

/**
 * 教师课堂控制台的扩展标签页（由各子项目登记），组件 props: { lesson }。
 * 学生课堂侧边工具同理见 studentTools。
 */
export interface ConsoleTab {
  name: string
  label: string
  component: Component
}

export const consoleTabs: ConsoleTab[] = [
  { name: 'homework', label: '作品汇总', component: defineAsyncComponent(() => import('../views/homework/LessonHomeworkSummary.vue')) },
  { name: 'interact', label: '举手与广播', component: defineAsyncComponent(() => import('../views/interact/TeacherInteractPanel.vue')) },
  { name: 'forum', label: '课堂论坛', component: defineAsyncComponent(() => import('../views/interact/ForumPanel.vue')) }
]

export const studentTools: ConsoleTab[] = [
  { name: 'interact', label: '举手/消息', component: defineAsyncComponent(() => import('../views/interact/StudentInteractPanel.vue')) },
  { name: 'forum', label: '课堂论坛', component: defineAsyncComponent(() => import('../views/interact/ForumPanel.vue')) }
]
