<template>
  <div class="quiz-page">
    <el-divider content-position="left">课堂测验</el-divider>
    <el-alert v-if="error" type="info" :closable="false" :title="error" />
    <TestTaker v-else :load-url="`/student/quiz/lessons/${lessonId}/pages/${page.id}`" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errMsg } from '../../api/client'
import TestTaker from './TestTaker.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const error = ref('')

onMounted(async () => {
  try {
    await api.get(`/student/quiz/lessons/${props.lessonId}/pages/${props.page.id}`)
  } catch (err) {
    error.value = errMsg(err, '测验暂不可用')
  }
})
</script>
