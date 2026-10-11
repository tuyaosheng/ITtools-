<template>
  <RoleLayout brand="ITtools 学生空间" :menu="menu" :extra-info="extra" />
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import RoleLayout from './RoleLayout.vue'
import { api } from '../api/client'

const extra = ref('')
const menu = [
  { path: '/student/home', label: '我的课堂' },
  { path: '/student/works', label: '我的作品' },
  { path: '/student/showcase', label: '精华作品' },
  { path: '/student/tests', label: '在线测验' },
  { path: '/student/practice', label: '刷题练习' },
  { path: '/student/resources', label: '学习资源' },
  { path: '/student/disk', label: '我的网盘' },
  { path: '/student/notes', label: '学习笔记' },
  { path: '/student/records', label: '学习记录' }
]

onMounted(async () => {
  try {
    const w = await api.get('/lab/whoami')
    extra.value = '机器名：' + (w.pcName || w.ip)
  } catch {
    /* ignore */
  }
})
</script>
