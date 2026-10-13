<template>
  <div v-if="data">
    <div class="bar">
      <el-radio-group v-model="scope" size="small" @change="load">
        <el-radio-button value="CLASS">本班结果</el-radio-button>
        <el-radio-button value="GRADE">年级段结果</el-radio-button>
      </el-radio-group>
      <span v-if="scope === 'GRADE'" class="muted">同年级 {{ data.lessons }} 个课堂的汇总，不显示学生名字</span>
      <el-button size="small" @click="load">刷新</el-button>
    </div>
    <el-empty v-if="!items.length" description="还没有设置调查题目（在课程编辑中设置）" />
    <SurveyResults v-else :items="items" :results="data.results" :total="scope === 'CLASS' ? data.classSize : undefined" />
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import SurveyResults from './SurveyResults.vue'

const props = defineProps<{ lessonId: number; page: any; lesson?: any }>()
const data = ref<any>(null)
const scope = ref('CLASS')
const items = computed(() => data.value?.config?.items || [])
let timer: number | undefined

async function load() {
  data.value = await api.get(`/teacher/activity/lessons/${props.lessonId}/pages/${props.page.id}/survey`, { scope: scope.value })
}

onMounted(() => {
  load()
  if (props.lesson?.status === 'ACTIVE') timer = window.setInterval(() => scope.value === 'CLASS' && load(), 5000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; flex-wrap: wrap; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
</style>
