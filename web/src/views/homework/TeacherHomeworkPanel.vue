<template>
  <div v-if="data" class="panel">
    <el-divider content-position="left">学生作品（{{ submitted }}/{{ data.students.length }} 已交）</el-divider>
    <div class="ops">
      <el-button size="small" @click="load">刷新</el-button>
      <el-button size="small" :disabled="!submitted" @click="zip">打包下载全部作品</el-button>
      <el-button size="small" :disabled="!submitted" @click="regrade">按当前规则重新自动评分</el-button>
      <el-radio-group v-model="filter" size="small">
        <el-radio-button value="all">全部</el-radio-button>
        <el-radio-button value="done">已交</el-radio-button>
        <el-radio-button value="todo">未交</el-radio-button>
        <el-radio-button value="ungraded">待批阅</el-radio-button>
      </el-radio-group>
    </div>
    <el-table :data="rows" size="small" stripe>
      <el-table-column prop="studentNo" label="学号" width="60" />
      <el-table-column prop="studentName" label="姓名" width="80" />
      <el-table-column label="作品" min-width="170">
        <template #default="{ row }: { row: any }">
          <template v-if="row.id">
            <el-link type="primary" @click="download(row)">{{ row.fileName }}</el-link>
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
        <template #default="{ row }: { row: any }">{{ row.teacherScore ?? '' }}</template>
      </el-table-column>
      <el-table-column prop="teacherComment" label="评语" min-width="120" show-overflow-tooltip />
      <el-table-column label="操作" width="150" fixed="right">
        <template #default="{ row }: { row: any }">
          <template v-if="row.id">
            <el-button link type="primary" @click="openGrade(row)">批阅</el-button>
            <el-button link :type="row.essence ? 'warning' : 'info'" @click="essence(row)">{{ row.essence ? '★精华' : '☆设精华' }}</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

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
        <el-form-item label="评语"><el-input v-model="comment" type="textarea" :rows="3" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="gradeVisible = false">取消</el-button>
        <el-button type="primary" @click="saveGrade">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import AutoDetail from './AutoDetail.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const data = ref<any>(null)
const filter = ref('all')
const gradeVisible = ref(false)
const grading = ref<any>(null)
const score = ref<number | undefined>()
const comment = ref('')
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
  Object.keys(rubricScores).forEach(k => delete rubricScores[k])
  const prev = row.rubricScores ? JSON.parse(row.rubricScores) : {}
  data.value.rubric?.forEach((it: any) => { rubricScores[it.id] = prev[it.id] ?? 0 })
  gradeVisible.value = true
}

async function saveGrade() {
  try {
    await api.put(`/teacher/homework/submissions/${grading.value.id}/grade`, data.value.rubric?.length
      ? { rubricScores, comment: comment.value }
      : { score: score.value, comment: comment.value })
    gradeVisible.value = false
    ElMessage.success('已保存')
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
.muted { color: #909399; font-size: 12px; margin-left: 6px; }
</style>
