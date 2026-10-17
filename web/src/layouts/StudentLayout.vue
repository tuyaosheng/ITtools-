<template>
  <RoleLayout brand="信息课堂" :menu="menu" :extra-info="extra" />
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
  { path: '/student/home', icon: '🏫', label: '我的课堂' },
  { path: '/student/works', icon: '🎨', label: '我的作品' },
  { path: '/student/showcase', icon: '🏆', label: '精华作品' },
  { path: '/student/tests', icon: '📝', label: '作业与测验' },
  { path: '/student/practice', icon: '✏️', label: '刷题练习' },
  { path: '/student/python', icon: '🐍', label: 'Python 练习' },
  { path: '/student/op-practice', icon: '🖱️', label: '课后操作题' },
  { path: '/student/resources', icon: '📚', label: '学习资源' },
  { path: '/student/disk', icon: '💾', label: '我的网盘' },
  { path: '/student/site', icon: '🌐', label: '个人网站' },
  { path: '/student/typing', icon: '⌨️', label: '指法练习' },
  { path: '/student/notes', icon: '📒', label: '学习笔记' },
  { path: '/student/electives', icon: '🧭', label: '走班选课' },
  { path: '/student/messages', icon: '🔔', label: '系统消息', badge: notices.unreadMessages || undefined },
  { path: '/student/qa', icon: '💬', label: '师生答疑' },
  { path: '/student/records', icon: '🗂️', label: '学习记录' },
  { path: '/student/report', icon: '📈', label: '我的学情' }
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
