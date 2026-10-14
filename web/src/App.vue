<template>
  <router-view />
</template>

<script setup lang="ts">
import { watchEffect } from 'vue'
import { useRoute } from 'vue-router'

// 学生端（含登录页）用完整的手账装饰，教师端/管理后台用素净版
const route = useRoute()
watchEffect(() => {
  const staff = route.path.startsWith('/teacher') || route.path.startsWith('/admin')
  document.body.classList.toggle('hb-staff', staff)
  document.body.classList.toggle('hb-student', !staff)
})
</script>
