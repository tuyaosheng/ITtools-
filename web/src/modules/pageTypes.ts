import { defineAsyncComponent, type Component } from 'vue'

/**
 * 课程页面类型注册表（对应原 it_coursetype）。
 * - student：学生课堂中渲染该页面的组件（props: lessonId, page）
 * - teacher：教师课堂控制台中该页面的"查看/批改"组件（props: lessonId, page），无则只显示内容预览
 * - settings：课程编辑中该页面类型的专属设置组件（props: page），页面保存后显示
 * 各子项目（作业/测验/互动）在此登记自己的组件。
 */
export interface PageTypeDef {
  label: string
  desc: string
  student?: Component
  teacher?: Component
  settings?: Component
}

export const pageTypes: Record<string, PageTypeDef> = {
  TEXT: { label: '图文', desc: '文字、图片等学习内容' },
  FILE: { label: '资料课件', desc: '上传 PPT/PDF/文档等供学生查看下载' },
  VIDEO: { label: '视频', desc: '上传视频或填写视频地址' },
  LINK: { label: '外部网页', desc: '嵌入或打开一个网址' },
  HOMEWORK: {
    label: '作品提交',
    desc: '学生上传作品，可自动评分、互评',
    student: defineAsyncComponent(() => import('../views/homework/StudentHomeworkPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/homework/TeacherHomeworkPanel.vue'))
  },
  QUIZ: {
    label: '课堂测验',
    desc: '关联题库试卷，学生在线作答自动批改',
    student: defineAsyncComponent(() => import('../views/quiz/StudentQuizPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/quiz/TeacherQuizPanel.vue'))
  },
  DISCUSS: {
    label: '讨论',
    desc: '按小组讨论并提交小组结论',
    student: defineAsyncComponent(() => import('../views/interact/StudentDiscussPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/interact/TeacherDiscussPanel.vue'))
  },
  ANSWER_FIRST: {
    label: '抢答',
    desc: '教师发起抢答，学生抢先作答',
    student: defineAsyncComponent(() => import('../views/interact/StudentAnswerFirstPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/interact/TeacherAnswerFirstPanel.vue'))
  },
  PYTHON: {
    label: 'Python 操作题',
    desc: '学生在线编写运行 Python，按测试点自动评分',
    student: defineAsyncComponent(() => import('../views/python/StudentPythonPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/python/TeacherPythonPanel.vue'))
  },
  GROUPING: {
    label: '分组与组长',
    desc: '学生自由选择小组、推选组长',
    student: defineAsyncComponent(() => import('../views/groups/StudentGroupingPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/groups/TeacherGroupManager.vue'))
  },
  GROUP_WORK: {
    label: '小组合作',
    desc: '设置研究主题、组内分工、资源交流、组员互评，组长提交小组成果',
    student: defineAsyncComponent(() => import('../views/groups/StudentGroupWork.vue')),
    teacher: defineAsyncComponent(() => import('../views/groups/TeacherGroupWorkPanel.vue'))
  },
  GROUP_SHOW: {
    label: '小组成果展示',
    desc: '展示各组成果，组间互相评分',
    student: defineAsyncComponent(() => import('../views/groups/StudentGroupShow.vue')),
    teacher: defineAsyncComponent(() => import('../views/groups/TeacherGroupWorkPanel.vue'))
  },
  SURVEY: {
    label: '课堂调查',
    desc: '判断/单选/多选/填空调查，实时统计全班与年级结果',
    student: defineAsyncComponent(() => import('../views/activity/StudentSurveyPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/activity/TeacherSurveyPanel.vue')),
    settings: defineAsyncComponent(() => import('../views/activity/SurveySettings.vue'))
  },
  TAKE_QUESTION: {
    label: '随机抽题',
    desc: '学生随机抽取一道问题（按学号分配，相邻同学题目不同）',
    student: defineAsyncComponent(() => import('../views/activity/StudentTakeQuestionPage.vue')),
    teacher: defineAsyncComponent(() => import('../views/activity/TeacherTakeQuestionPanel.vue')),
    settings: defineAsyncComponent(() => import('../views/activity/TakeQuestionSettings.vue'))
  }
}

export const pageTypeOptions = Object.entries(pageTypes).map(([value, d]) => ({ value, label: d.label, desc: d.desc }))
