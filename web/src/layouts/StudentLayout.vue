<template>
  <RoleLayout brand="ITtools 学生空间" :menu="menu" :extra-info="extra" />
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import RoleLayout from './RoleLayout.vue'
import { api } from '../api/client'
import { useNoticeStore } from '../stores/notices'

const notices = useNoticeStore()
let timer: number | undefined

const extra = ref('')
const menu = computed(() => [
  { path: '/student/home', label: '我的课堂' },
  { path: '/student/works', label: '我的作品' },
  { path: '/student/showcase', label: '精华作品' },
  { path: '/student/tests', label: '作业与测验' },
  { path: '/student/practice', label: '刷题练习' },
  { path: '/student/python', label: 'Python 练习' },
  { path: '/student/resources', label: '学习资源' },
  { path: '/student/disk', label: '我的网盘' },
  { path: '/student/notes', label: '学习笔记' },
  { path: '/student/electives', label: '走班选课' },
  { path: '/student/messages', label: '系统消息', badge: notices.unreadMessages || undefined },
  { path: '/student/qa', label: '师生答疑' },
  { path: '/student/records', label: '学习记录' }
])

onBeforeUnmount(() => window.clearInterval(timer))

onMounted(async () => {
  notices.refresh()
  timer = window.setInterval(() => notices.refresh(), 60000)
  try {
    const w = await api.get('/lab/whoami')
    extra.value = '机器名：' + (w.pcName || w.ip)
  } catch {
    /* ignore */
  }
})
</script>
