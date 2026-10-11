<template>
  <RoleLayout brand="ITtools 教师工作台" :menu="menu" />
</template>

<script setup lang="ts">
import RoleLayout from './RoleLayout.vue'

const menu = [
  { path: '/teacher/home', label: '工作台' },
  { path: '/teacher/courses', label: '我的课程' },
  { path: '/teacher/lessons', label: '上课记录' },
  { path: '/teacher/homework', label: '作品批阅' },
  { path: '/teacher/quiz', label: '题库与试卷' },
  { path: '/teacher/tests', label: '测验管理' },
  { path: '/teacher/rubrics', label: '评价量规' },
  { path: '/teacher/resources', label: '资源库' },
  { path: '/teacher/lesson-plans', label: '教案' },
  { path: '/teacher/classes', label: '我的班级' },
  { path: '/teacher/timetable', label: '课表' }
]
</script>
