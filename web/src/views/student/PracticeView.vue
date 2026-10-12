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

    <!-- 正在做 / 查看的练习卷 -->
    <template v-if="session">
      <div class="s-head">
        <el-button link @click="closeSession">← 返回</el-button>
        <b>{{ session.title }}</b>
        <span v-if="session.submittedAt" class="score">答对 <b>{{ session.correct }}</b> / {{ session.total }}</span>
      </div>
      <QuestionItem v-for="(q, i) in session.questions" :key="q.id" v-model="sAnswers[q.id]" :q="q" :index="i"
        :readonly="!!session.submittedAt" :show-key="!!session.submittedAt" />
      <div v-if="!session.submittedAt" class="ops">
        <span class="muted">已答 {{ sAnswered }}/{{ session.questions.length }}</span>
        <el-button type="primary" @click="submitSession">交卷批改</el-button>
      </div>
    </template>

    <el-tabs v-else v-model="tab" @tab-change="onTab">
      <el-tab-pane label="抽题练习" name="draw">
        <el-form label-width="90px" class="draw">
          <el-form-item label="刷题方式">
            <el-radio-group v-model="draw.source">
              <el-radio-button value="SET" :disabled="!sets.length">指定题库</el-radio-button>
              <el-radio-button value="RECORD">学习记录</el-radio-button>
              <el-radio-button value="POOL">公共题库</el-radio-button>
            </el-radio-group>
            <div class="muted full">{{ sourceHelp[draw.source] }}</div>
          </el-form-item>
          <el-form-item v-if="draw.source === 'SET'" label="题库">
            <el-select v-model="draw.setId" class="w280">
              <el-option v-for="s in sets" :key="s.id" :label="`${s.name}（${s.questionCount} 题 · ${s.teacherName}）`" :value="s.id" />
            </el-select>
          </el-form-item>
          <el-form-item v-if="draw.source === 'POOL'" label="分类">
            <el-select v-model="draw.category" clearable placeholder="全部分类" class="w200">
              <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
            </el-select>
          </el-form-item>
          <el-form-item label="抽取题数"><el-input-number v-model="draw.count" :min="1" :max="50" /></el-form-item>
          <el-form-item><el-button type="primary" @click="start">开始抽题</el-button></el-form-item>
        </el-form>
      </el-tab-pane>

      <el-tab-pane label="逐题练习" name="free">
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
      </el-tab-pane>

      <el-tab-pane label="我的刷题记录" name="history">
        <el-empty v-if="!history.length" description="还没有刷题记录" />
        <el-table v-else :data="history" stripe size="small">
          <el-table-column prop="title" label="练习" min-width="200" />
          <el-table-column label="时间" width="150"><template #default="{ row }: { row: any }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
          <el-table-column label="成绩" width="120">
            <template #default="{ row }: { row: any }">
              <span v-if="row.submittedAt">{{ row.correct }} / {{ row.total }}（{{ Math.round(row.correct * 100 / row.total) }}%）</span>
              <el-tag v-else size="small" type="warning">未交卷</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="90">
            <template #default="{ row }: { row: any }"><el-button link type="primary" @click="openSession(row.id)">{{ row.submittedAt ? '查看' : '继续' }}</el-button></template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import QuestionItem from '../quiz/QuestionItem.vue'

const sourceHelp: Record<string, string> = {
  SET: '老师为你们班指定的刷题题库',
  RECORD: '从你做过的作业、测验里抽题，做错的题优先（已标记“掌握”的不抽）',
  POOL: '全校老师共享的练习题'
}
const tab = ref('draw')
const categories = ref<string[]>([])
const stats = ref<any>({ total: 0, correct: 0, today: 0 })
const sets = ref<any[]>([])
const draw = reactive<any>({ source: 'RECORD', setId: undefined, category: '', count: 10 })
const session = ref<any>(null)
const sAnswers = reactive<Record<number, any>>({})
const sAnswered = computed(() => Object.values(sAnswers).filter(v => (Array.isArray(v) ? v.some(x => x) : v)).length)
const history = ref<any[]>([])

// 逐题练习
const category = ref('')
const qtype = ref('')
const q = ref<any>(null)
const answer = ref<any>(null)
const result = ref<any>(null)
const empty = ref(false)
const count = ref(0)
const shown = computed(() => (result.value ? { ...q.value, answer: result.value.answer, analysis: result.value.analysis, correct: result.value.correct } : q.value))

function applySession(s: any) {
  session.value = s
  Object.keys(sAnswers).forEach(k => delete sAnswers[Number(k)])
  s.questions.forEach((x: any) => { if (x.given != null) sAnswers[x.id] = x.given })
}

async function start() {
  try {
    applySession(await api.post('/student/quiz/practice-sessions', { ...draw, setId: draw.source === 'SET' ? draw.setId : null }))
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function openSession(id: number) { applySession(await api.get(`/student/quiz/practice-sessions/${id}`)) }

async function submitSession() {
  const left = session.value.questions.length - sAnswered.value
  try {
    if (left > 0) await ElMessageBox.confirm(`还有 ${left} 题没做，确定交卷？`, '交卷')
    applySession(await api.post(`/student/quiz/practice-sessions/${session.value.id}/submit`, {
      answers: session.value.questions.map((x: any) => ({ questionId: x.id, answer: sAnswers[x.id] ?? null }))
    }))
    await loadMeta()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function closeSession() {
  session.value = null
  if (tab.value === 'history') await loadHistory()
}

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

async function loadHistory() { history.value = await api.get('/student/quiz/practice-sessions') }
async function onTab() { if (tab.value === 'history') await loadHistory() }

async function loadMeta() {
  const m = await api.get('/student/quiz/practice/meta')
  categories.value = m.categories
  stats.value = m.stats
}

onMounted(async () => {
  await loadMeta()
  sets.value = await api.get('/student/quiz/practice-sets')
  if (sets.value.length) { draw.source = 'SET'; draw.setId = sets.value[0].id }
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.stats { display: flex; gap: 16px; font-size: 13px; color: #606266; }
.stats b { color: var(--el-color-primary); font-size: 16px; }
.filters { display: flex; gap: 8px; margin-bottom: 14px; }
.w140 { width: 140px; } .w160 { width: 160px; } .w200 { width: 200px; } .w280 { width: 280px; }
.full { width: 100%; }
.muted { color: #909399; font-size: 12px; }
.draw { max-width: 640px; }
.s-head { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
.score { margin-left: auto; }
.score b { font-size: 20px; color: var(--el-color-primary); }
.ops { display: flex; justify-content: flex-end; align-items: center; gap: 10px; }
.mb { margin-bottom: 10px; }
</style>
