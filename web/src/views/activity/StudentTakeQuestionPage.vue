<template>
  <div v-if="data" class="take">
    <el-empty v-if="!data.count" description="教师还没有设置题目" />
    <template v-else-if="!data.question">
      <el-button type="primary" size="large" :loading="drawing" :disabled="lesson?.status === 'ENDED'" @click="draw">🎲 随机抽取我的问题</el-button>
    </template>
    <el-card v-else shadow="never" class="q">
      <template #header>我的问题 · 第 {{ data.no }} 题</template>
      <div class="text">{{ data.question }}</div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lessonId: number; page: any; lesson?: any }>()
const data = ref<any>(null)
const drawing = ref(false)
const url = () => `/student/activity/lessons/${props.lessonId}/pages/${props.page.id}/take`

async function draw() {
  drawing.value = true
  try {
    data.value = await api.post(url())
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    drawing.value = false
  }
}

onMounted(async () => { data.value = await api.get(url()) })
</script>

<style scoped>
.take { margin-top: 8px; }
.q { max-width: 720px; border-left: 4px solid var(--el-color-primary); }
.text { white-space: pre-wrap; font-size: 16px; line-height: 1.8; }
.muted { color: var(--el-text-color-secondary); }
</style>
