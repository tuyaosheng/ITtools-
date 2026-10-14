<template>
  <RoleLayout brand="ITtools 教师工作台" :menu="menu" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import RoleLayout from './RoleLayout.vue'
import { useNoticeStore } from '../stores/notices'

const notices = useNoticeStore()
let timer: number | undefined
onMounted(() => {
  notices.refresh()
  timer = window.setInterval(() => notices.refresh(), 60000)
})
onBeforeUnmount(() => window.clearInterval(timer))

const menu = computed(() => [
  { path: '/teacher/home', label: '工作台' },
  { path: '/teacher/courses', label: '我的课程' },
  { path: '/teacher/lessons', label: '上课记录' },
  { path: '/teacher/homework', label: '作品批阅' },
  { path: '/teacher/quiz', label: '题库与试卷' },
  { path: '/teacher/tests', label: '作业与测验' },
  { path: '/teacher/python', label: 'Python 题库' },
  { path: '/teacher/rubrics', label: '评价量规' },
  { path: '/teacher/resources', label: '资源库' },
  { path: '/teacher/lesson-plans', label: '教案' },
  { path: '/teacher/reflections', label: '学习心得' },
  { path: '/teacher/disk', label: '教师网盘' },
  { path: '/teacher/student-space', label: '学生空间' },
  { path: '/teacher/classes', label: '我的班级' },
  { path: '/teacher/reports', label: '学习情况' },
  { path: '/teacher/login-logs', label: '登入记录' },
  { path: '/teacher/timetable', label: '课表' },
  { path: '/teacher/messages', label: '系统消息', badge: notices.unreadMessages || undefined },
  { path: '/teacher/qa', label: '师生答疑', badge: notices.pendingQuestions || undefined }
])
</script>
