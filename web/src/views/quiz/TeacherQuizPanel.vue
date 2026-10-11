<template>
  <div>
    <el-divider content-position="left">测验情况</el-divider>
    <el-alert v-if="error" type="warning" :closable="false" :title="error" />
    <TestDetail v-else :key="page.id" :load-url="`/teacher/quiz/lessons/${lessonId}/pages/${page.id}/test`" />
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api, errMsg } from '../../api/client'
import TestDetail from './TestDetail.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const error = ref('')
onMounted(async () => {
  try {
    await api.get(`/teacher/quiz/lessons/${props.lessonId}/pages/${props.page.id}/test`)
  } catch (err) {
    error.value = errMsg(err, '测验不可用')
  }
})
</script>
