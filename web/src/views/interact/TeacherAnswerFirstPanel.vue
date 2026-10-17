<template>
  <div>
    <el-divider content-position="left">抢答控制</el-divider>
    <div class="start">
      <el-input v-model="question" placeholder="抢答题目" />
      <el-button type="danger" @click="start">发起抢答</el-button>
    </div>
    <div v-for="r in rounds" :key="r.id" class="round">
      <div class="r-head">
        <b>{{ r.question }}</b>
        <el-tag size="small" :type="r.status === 'OPEN' ? 'danger' : 'info'">{{ r.status === 'OPEN' ? '进行中' : '已结束' }}</el-tag>
        <el-button v-if="r.status === 'OPEN'" size="small" @click="close(r)">结束抢答</el-button>
      </div>
      <el-table :data="r.entries" size="small">
        <el-table-column prop="rank" label="名次" width="60" />
        <el-table-column prop="studentName" label="学生" width="90" />
        <el-table-column prop="answer" label="答案" />
        <el-table-column label="用时" width="90"><template #default="{ row }: { row: any }">{{ seconds(r, row) }} 秒</template></el-table-column>
        <el-table-column label="判定" width="260">
          <template #default="{ row }: { row: any }">
            <el-button size="small" :type="row.correct === true ? 'success' : ''" @click="judge(r, row, true)">正确</el-button>
            <el-button size="small" :type="row.correct === false ? 'danger' : ''" @click="judge(r, row, false)">错误</el-button>
            <el-input-number v-model="row.score" :min="0" :max="20" size="small" controls-position="right" class="w80" @change="judge(r, row, row.correct)" />
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ lessonId: number; page: any }>()
const rounds = ref<any[]>([])
const question = ref('')
let timer: number | undefined

const plain = (h?: string) => (h || '').replace(/<[^>]+>/g, '').trim()
const seconds = (r: any, e: any) => ((new Date(e.createdAt).getTime() - new Date(r.createdAt).getTime()) / 1000).toFixed(1)

async function load() { rounds.value = await api.get(`/teacher/interact/lessons/${props.lessonId}/answer-first/${props.page.id}`) }

async function start() {
  try {
    await api.post(`/teacher/interact/lessons/${props.lessonId}/answer-first`, { pageId: props.page.id, question: question.value || plain(props.page.content) || props.page.title })
    question.value = ''
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function close(r: any) { await api.post(`/teacher/interact/answer-first/${r.id}/close`); await load() }
async function judge(r: any, e: any, correct: boolean | null) {
  await api.put(`/teacher/interact/answer-first/${r.id}/entries/${e.studentId}`, { correct, score: e.score ?? (correct ? 5 : 0) })
  await load()
}

onMounted(async () => { await load(); timer = window.setInterval(load, 2000) })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.start { display: flex; gap: 8px; margin-bottom: 12px; }
.round { margin-bottom: 16px; }
.r-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.w80 { width: 90px; margin-left: 6px; }
</style>
