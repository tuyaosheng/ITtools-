<template>
  <div v-if="data" class="panel">
    <el-divider content-position="left">学生作品（{{ submitted }}/{{ data.students.length }} 已交）</el-divider>
    <div class="ops">
      <el-button size="small" @click="load">刷新</el-button>
      <el-button size="small" :disabled="!submitted" @click="zip">打包下载全部作品</el-button>
      <el-button size="small" :disabled="!submitted" @click="carousel = true">循环展播</el-button>
      <el-button size="small" :disabled="!submitted" @click="regrade">按当前规则重新自动评分</el-button>
      <el-button size="small" :disabled="!submitted" @click="openStats">评分点通过率</el-button>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="done">已交</el-radio-button>
        <el-radio-button value="todo">未交</el-radio-button>
        <el-radio-button value="ungraded">待批阅</el-radio-button>
      </el-radio-group>
    </div>
    <div v-if="selected.length" class="batch">
      <span>偷懒模式 · 已选 {{ selected.length }} 份：</span>
      <el-input-number v-model="batchScore" :min="0" :max="1000" size="small" placeholder="分数" controls-position="right" class="w110" />
      <el-select v-model="batchRating" clearable size="small" placeholder="评价" class="w90">
        <el-option label="好评" value="GOOD" /><el-option label="中评" value="OK" /><el-option label="差评" value="BAD" />
      </el-select>
      <el-input v-model="batchComment" size="small" placeholder="统一评语（可选）" class="w200" />
      <el-checkbox v-model="skipGraded" size="small">跳过已批阅的</el-checkbox>
      <el-button size="small" type="primary" @click="batchGrade">批量批阅</el-button>
    </div>
    <el-table :data="rows" size="small" stripe @selection-change="(v: any[]) => (selected = v)">
      <el-table-column type="selection" width="34" :selectable="(r: any) => !!r.id" />
      <el-table-column prop="studentNo" label="学号" width="60" />
      <el-table-column prop="studentName" label="姓名" width="80" />
      <el-table-column label="作品" min-width="170">
        <template #default="{ row }: { row: any }">
          <template v-if="row.id">
            <div v-if="row.title" class="w-title">《{{ row.title }}》<span v-if="row.selfScore != null" class="muted">自评 {{ row.selfScore }}</span></div>
            <el-link type="primary" @click="download(row)">{{ row.fileName }}</el-link>
            <el-tag v-if="row.returned" size="small" type="danger" class="ml">已发回重做</el-tag>
            <div class="muted">{{ fmtTime(row.submittedAt) }} · {{ row.pcName || '' }}{{ row.late ? ' · 补交' : '' }}{{ row.submitCount > 1 ? ` · 交${row.submitCount}次` : '' }}</div>
          </template>
          <el-tag v-else size="small" type="info">未交</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="自动分" width="90">
        <template #default="{ row }: { row: any }">
          <el-popover v-if="row.autoDetail" trigger="hover" :width="520">
            <template #reference><el-link>{{ row.autoScore }}/{{ row.autoMax }}</el-link></template>
            <AutoDetail :detail="row.autoDetail" />
          </el-popover>
          <span v-else>—</span>
        </template>
      </el-table-column>
      <el-table-column v-if="data.config.peerReview" prop="peerAvg" label="互评" width="60" />
      <el-table-column label="教师评分" width="90">
        <template #default="{ row }: { row: any }">{{ row.teacherScore ?? '' }}<span v-if="row.rating" class="rating">{{ ratingLabel[row.rating] }}</span></template>
      </el-table-column>
      <el-table-column prop="teacherComment" label="评语" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="190" fixed="right">
        <template #default="{ row }: { row: any }">
          <template v-if="row.id">
            <el-button link type="primary" @click="openGrade(row)">批阅</el-button>
            <el-button v-if="!row.returned" link type="danger" @click="returnRedo(row)">发回</el-button>
            <el-button link :type="row.essence ? 'warning' : 'info'" @click="essence(row)">{{ row.essence ? '★精华' : '☆设精华' }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <WorksCarousel v-model="carousel" :students="data.students" />
    <el-dialog v-model="gradeVisible" :title="`批阅：${grading?.studentName}`" width="520px" append-to-body>
      <el-form label-width="90px">
        <template v-if="data.rubric?.length">
          <el-form-item v-for="it in data.rubric" :key="it.id" :label="it.name">
            <el-input-number v-model="rubricScores[it.id]" :min="0" :max="it.maxScore" />
            <span class="muted">满分 {{ it.maxScore }}</span>
          </el-form-item>
          <el-form-item label="合计"><b>{{ rubricTotal }}</b></el-form-item>
        </template>
        <el-form-item v-else label="分数">
          <el-input-number v-model="score" :min="0" :max="1000" :precision="1" />
          <el-button v-if="grading?.autoScore != null" link type="primary" @click="score = grading.autoScore">采用自动分 {{ grading.autoScore }}</el-button>
        </el-form-item>
        <el-form-item label="评价">
          <el-radio-group v-model="rating">
            <el-radio-button value="GOOD">好评</el-radio-button><el-radio-button value="OK">中评</el-radio-button>
            <el-radio-button value="BAD">差评</el-radio-button><el-radio-button value="">不评</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="评语"><el-input v-model="comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGrade">保存</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="statsDlg" title="评分点通过率" width="620px" append-to-body>
      <div><Hint block>按本堂课已提交作品的自动评分统计，通过率低的操作就是学生普遍没掌握的地方。</Hint></div>
      <el-table :data="stats" size="small" border empty-text="没有自动评分记录">
        <el-table-column prop="seq" label="#" width="40" />
        <el-table-column label="评分点" min-width="200">
          <template #default="{ row }: { row: any }">{{ row.name }}<span v-if="row.note" class="muted">（{{ row.note }}）</span></template>
        </el-table-column>
        <el-table-column label="通过" width="90"><template #default="{ row }: { row: any }">{{ row.passed }}/{{ row.total }}</template></el-table-column>
        <el-table-column label="通过率" width="180">
          <template #default="{ row }: { row: any }">
            <el-progress :percentage="row.rate ?? 0" :status="(row.rate ?? 0) < 60 ? 'exception' : 'success'" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import AutoDetail from './AutoDetail.vue'
import WorksCarousel from './WorksCarousel.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const data = ref<any>(null)
const filter = ref('all')
const gradeVisible = ref(false)
const grading = ref<any>(null)
const score = ref<number | undefined>()
const comment = ref('')
const rating = ref('')
const carousel = ref(false)
const ratingLabel: Record<string, string> = { GOOD: '好评', OK: '中评', BAD: '差评' }
const selected = ref<any[]>([])
const batchScore = ref<number | undefined>()
const batchRating = ref('')
const batchComment = ref('')
const skipGraded = ref(true)
const rubricScores = reactive<Record<string, number>>({})

const submitted = computed(() => data.value?.students.filter((s: any) => s.id).length || 0)
const rows = computed(() => {
  const all = data.value?.students || []
  if (filter.value === 'done') return all.filter((s: any) => s.id)
  if (filter.value === 'todo') return all.filter((s: any) => !s.id)
  if (filter.value === 'ungraded') return all.filter((s: any) => s.id && s.teacherScore == null)
  return all
})
const rubricTotal = computed(() => Object.values(rubricScores).reduce((a, b) => a + (Number(b) || 0), 0))
const base = () => `/teacher/homework/lessons/${props.lessonId}/pages/${props.page.id}`

async function load() {
  data.value = await api.get(base())
}

const statsDlg = ref(false)
const stats = ref<any[]>([])
async function openStats() {
  try {
    stats.value = await api.get(`/teacher/homework/lessons/${props.lessonId}/pages/${props.page.id}/rule-stats`)
    statsDlg.value = true
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function download(row: any) {
  api.download(`/homework/submissions/${row.id}/file`, row.fileName)
}

function zip() {
  api.download(base() + '/zip')
}

async function regrade() {
  const n = await api.post(base() + '/regrade')
  ElMessage.success(`已重新评分 ${n} 份作品`)
  await load()
}

function openGrade(row: any) {
  grading.value = row
  score.value = row.teacherScore ?? undefined
  comment.value = row.teacherComment || ''
  rating.value = row.rating || ''
  Object.keys(rubricScores).forEach(k => delete rubricScores[k])
  const prev = row.rubricScores ? JSON.parse(row.rubricScores) : {}
  data.value.rubric?.forEach((it: any) => { rubricScores[it.id] = prev[it.id] ?? 0 })
  gradeVisible.value = true
}

async function saveGrade() {
  try {
    await api.put(`/teacher/homework/submissions/${grading.value.id}/grade`, data.value.rubric?.length
      ? { rubricScores, comment: comment.value, rating: rating.value }
      : { score: score.value, comment: comment.value, rating: rating.value })
    gradeVisible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function returnRedo(row: any) {
  try {
    const { value } = await ElMessageBox.prompt(`发回 ${row.studentName} 的作品重做，可写一句说明（可选）`, '发回重做', { inputPlaceholder: '如：画面再丰富一些' })
    await api.post(`/teacher/homework/submissions/${row.id}/return`, { note: value || null })
    ElMessage.success('已发回，学生下课后也可以重新提交')
    await load()
  } catch (err) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function batchGrade() {
  if (batchScore.value == null && !batchRating.value && !batchComment.value) { ElMessage.warning('请至少填写分数、评价或评语中的一项'); return }
  try {
    const n = await api.post('/teacher/homework/submissions/batch-grade', {
      ids: selected.value.map(r => r.id), score: batchScore.value ?? null, rating: batchRating.value, comment: batchComment.value, skipGraded: skipGraded.value
    })
    ElMessage.success(`已批阅 ${n} 份`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function essence(row: any) {
  await api.put(`/teacher/homework/submissions/${row.id}/essence`, { essence: !row.essence })
  row.essence = !row.essence
}

watch(() => props.page.id, load)
onMounted(load)
</script>

<style scoped>
.ops { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 6px; }
.batch { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; padding: 6px 10px; margin-bottom: 8px; background: var(--el-color-primary-light-9); border-radius: 6px; font-size: 13px; }
.w90 { width: 90px; } .w110 { width: 110px; } .w200 { width: 200px; }
.ml { margin-left: 6px; }
.w-title { font-weight: 600; font-size: 13px; }
.rating { margin-left: 6px; font-size: 12px; color: var(--el-color-warning); }
</style>
