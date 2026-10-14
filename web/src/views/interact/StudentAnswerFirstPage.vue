<template>
  <div class="af">
    <el-divider content-position="left">抢答</el-divider>
    <el-empty v-if="!round" description="抢答还没开始" :image-size="50" />
    <template v-else>
      <div class="question">{{ round.question }}</div>
      <el-tag :type="round.status === 'OPEN' ? 'danger' : 'info'">{{ round.status === 'OPEN' ? '抢答进行中' : '抢答已结束' }}</el-tag>
      <div v-if="round.status === 'OPEN' && !mine" class="answer">
        <el-input v-model="answer" placeholder="输入你的答案" @keyup.enter="submit" />
        <el-button type="danger" size="large" :loading="sending" @click="submit">抢 答</el-button>
      </div>
      <el-alert v-if="mine" :type="mine.correct === true ? 'success' : mine.correct === false ? 'error' : 'info'" :closable="false" class="mt"
        :title="`你是第 ${mine.rank} 位抢答者` + (mine.correct === true ? `，回答正确！+${mine.score ?? 0} 分` : mine.correct === false ? '，回答不正确' : '，等待老师判定')" />
      <div class="ranks">
        <div v-for="e in round.entries" :key="e.studentId" class="rank">
          <span class="no">{{ e.rank }}</span>{{ e.studentName }}
          <el-tag v-if="e.correct === true" size="small" type="success">✔</el-tag>
          <el-tag v-else-if="e.correct === false" size="small" type="danger">✘</el-tag>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'

const props = defineProps<{ lessonId: number; page: any }>()
const auth = useAuthStore()
const round = ref<any>(null)
const answer = ref('')
const sending = ref(false)
const mine = computed(() => round.value?.entries.find((e: any) => e.studentId === auth.user?.userId))
let timer: number | undefined

async function load() { round.value = await api.get(`/student/interact/lessons/${props.lessonId}/answer-first/${props.page.id}`) }

async function submit() {
  sending.value = true
  try {
    round.value = await api.post(`/student/interact/answer-first/${round.value.id}`, { answer: answer.value })
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    sending.value = false
  }
}

onMounted(async () => { await load(); timer = window.setInterval(load, 2000) })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.question { font-size: 18px; font-weight: 600; margin-bottom: 10px; }
.answer { display: flex; gap: 10px; margin-top: 12px; }
.mt { margin-top: 12px; }
.ranks { margin-top: 14px; display: flex; flex-direction: column; gap: 6px; }
.rank { display: flex; align-items: center; gap: 8px; }
.no { width: 24px; height: 24px; border-radius: 50%; background: var(--el-color-danger); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 12px; }
</style>
