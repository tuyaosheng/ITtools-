<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>刷题练习</span>
        <div class="stats">
          <span>累计 <b>{{ stats.total }}</b> 题</span>
          <span>正确率 <b>{{ stats.total ? Math.round((stats.correct * 100) / stats.total) : 0 }}%</b></span>
          <span>今日 <b>{{ stats.today }}</b> 题</span>
        </div>
      </div>
    </template>
    <div class="filters">
      <el-select v-model="category" clearable placeholder="全部分类" class="w160">
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="qtype" clearable placeholder="全部题型" class="w140">
        <el-option label="单选题" value="SINGLE" /><el-option label="多选题" value="MULTI" />
        <el-option label="判断题" value="JUDGE" /><el-option label="填空题" value="FILL" />
      </el-select>
      <el-button type="primary" @click="next">{{ q ? '换一题' : '开始练习' }}</el-button>
    </div>
    <el-empty v-if="empty" description="题库中暂无可练习的题目" />
    <template v-if="q">
      <QuestionItem v-model="answer" :q="shown" :index="count - 1" :readonly="!!result" :show-key="!!result" />
      <el-alert v-if="result" :type="result.correct ? 'success' : 'error'" :closable="false" :title="result.correct ? '回答正确！' : '回答错误，看看解析吧'" class="mb" />
      <div class="ops">
        <el-button v-if="!result" type="primary" @click="check">提交答案</el-button>
        <el-button v-else type="primary" @click="next">下一题</el-button>
      </div>
    </template>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api } from '../../api/client'
import QuestionItem from '../quiz/QuestionItem.vue'

const categories = ref<string[]>([])
const stats = ref<any>({ total: 0, correct: 0, today: 0 })
const category = ref('')
const qtype = ref('')
const q = ref<any>(null)
const answer = ref<any>(null)
const result = ref<any>(null)
const empty = ref(false)
const count = ref(0)

const shown = computed(() => (result.value ? { ...q.value, answer: result.value.answer, analysis: result.value.analysis, correct: result.value.correct } : q.value))

async function next() {
  result.value = null
  answer.value = null
  q.value = await api.get('/student/quiz/practice/next', { category: category.value, qtype: qtype.value })
  empty.value = !q.value
  if (q.value) count.value++
}

async function check() {
  result.value = await api.post(`/student/quiz/practice/${q.value.id}`, { answer: answer.value })
  stats.value = result.value.stats
}

onMounted(async () => {
  const m = await api.get('/student/quiz/practice/meta')
  categories.value = m.categories
  stats.value = m.stats
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.stats { display: flex; gap: 16px; font-size: 13px; color: #606266; }
.stats b { color: var(--el-color-primary); font-size: 16px; }
.filters { display: flex; gap: 8px; margin-bottom: 14px; }
.w160 { width: 160px; } .w140 { width: 140px; }
.ops { display: flex; justify-content: flex-end; }
.mb { margin-bottom: 10px; }
</style>
