import { defineAsyncComponent, type Component } from 'vue'

/**
 * 教师课堂控制台的扩展标签页（由各子项目登记），组件 props: { lesson }。
 * 学生课堂侧边工具同理见 studentTools。
 */
export interface ConsoleTab {
  name: string
  label: string
  component: Component
  /** 对应教师可按课堂关闭的功能（LessonService.FEATURES），被关闭时学生端不显示 */
  feature?: string
}

export const consoleTabs: ConsoleTab[] = [
  { name: 'groups', label: '分组管理', component: defineAsyncComponent(() => import('../views/groups/GroupConsoleTab.vue')) },
  { name: 'scores', label: '课堂成绩', component: defineAsyncComponent(() => import('../views/homework/LessonScores.vue')) },
  { name: 'homework', label: '作品汇总', component: defineAsyncComponent(() => import('../views/homework/LessonHomeworkSummary.vue')) },
  { name: 'interact', label: '举手与广播', component: defineAsyncComponent(() => import('../views/interact/TeacherInteractPanel.vue')) },
  { name: 'forum', label: '课堂论坛', component: defineAsyncComponent(() => import('../views/interact/ForumPanel.vue')) },
  { name: 'dwell', label: '学情监控', component: defineAsyncComponent(() => import('../views/interact/DwellPanel.vue')) },
  { name: 'netgate', label: '成绩开网', component: defineAsyncComponent(() => import('../views/quiz/NetGatePanel.vue')) },
  { name: 'rollcall', label: '随机点名', component: defineAsyncComponent(() => import('../views/activity/RollCallPanel.vue')) }
]

export const studentTools: ConsoleTab[] = [
  { name: 'interact', label: '举手/消息', component: defineAsyncComponent(() => import('../views/interact/StudentInteractPanel.vue')) },
  { name: 'forum', label: '课堂论坛', feature: 'FORUM', component: defineAsyncComponent(() => import('../views/interact/ForumPanel.vue')) }
]

/** 课堂里已被教师关闭的功能列表 */
export const closedFeatures = (lesson: any): string[] =>
  String(lesson?.disabledFeatures || '').split(',').map(s => s.trim()).filter(Boolean)
