<template>
  <div v-if="data">
    <el-empty v-if="!data.questions.length" description="还没有设置题目" />
    <template v-else>
      <div class="bar">
        <span class="muted">已抽取 {{ drawn }} / {{ data.students.length }} 人</span>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
      <el-table :data="data.students" size="small" stripe max-height="460">
        <el-table-column prop="studentNo" label="学号" width="70" />
        <el-table-column prop="name" label="姓名" width="90" />
        <el-table-column label="分到的题目" min-width="260">
          <template #default="{ row }: { row: any }">
            <span class="qno">第 {{ row.questionNo }} 题</span>
            <span class="qtext">{{ data.questions[row.questionNo - 1] }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }: { row: any }">
            <el-tag v-if="row.drawnNo" size="small" type="success">已抽取</el-tag>
            <el-tag v-else-if="row.status === 'PRESENT'" size="small" type="info">未抽取</el-tag>
            <el-tag v-else size="small" type="danger">未进课</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'

const props = defineProps<{ lessonId: number; page: any; lesson?: any }>()
const data = ref<any>(null)
const drawn = computed(() => data.value?.students.filter((s: any) => s.drawnNo).length || 0)

async function load() {
  data.value = await api.get(`/teacher/activity/lessons/${props.lessonId}/pages/${props.page.id}/take`)
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.qno { color: var(--el-color-primary); margin-right: 6px; white-space: nowrap; }
.qtext { font-size: 12px; color: var(--el-text-color-regular); }
</style>
