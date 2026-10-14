<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <el-empty v-else-if="data && !data.problem" description="还没有选择 Python 题目" />
  <div v-else-if="data">
    <div class="head">
      <div>
        <b>{{ data.problem.title }}</b>
        <span class="muted ml8">v{{ data.problem.version }} · {{ data.problem.cases.length }} 个测试点 · 满分 {{ data.problem.totalScore }}</span>
      </div>
      <div class="ops">
        <span class="muted">开放参考代码</span>
        <el-switch v-model="showRef" @change="toggleRef" />
        <el-popconfirm title="按每位学生最近一次提交的代码重新评测？" @confirm="regrade">
          <template #reference><el-button size="small" :loading="regrading">重新评测</el-button></template>
        </el-popconfirm>
        <el-button size="small" @click="load">刷新</el-button>
      </div>
    </div>
    <div class="stats">
      <el-tag>已提交 {{ submitted }} / {{ data.rows.length }}</el-tag>
      <el-tag type="success">满分 {{ fullCount }} 人</el-tag>
      <el-tag type="info">平均分 {{ avg }}</el-tag>
      <el-tag v-if="staleCount" type="warning">{{ staleCount }} 人成绩基于旧版题目，需重新评测</el-tag>
    </div>
    <el-table :data="data.rows" size="small" stripe max-height="520">
      <el-table-column prop="studentNo" label="学号" width="80" />
      <el-table-column prop="name" label="姓名" width="100" />
      <el-table-column label="得分" width="100" sortable :sort-method="(a: any, b: any) => Number(a.score ?? -1) - Number(b.score ?? -1)">
        <template #default="{ row }">
          <span v-if="row.score != null" :class="{ full: Number(row.score) === Number(row.maxScore) }">{{ Number(row.score) }} / {{ Number(row.maxScore) }}</span>
          <span v-else-if="row.submissionId" class="muted">已保存未提交</span>
          <span v-else class="muted">未作答</span>
        </template>
      </el-table-column>
      <el-table-column label="测试点" min-width="160">
        <template #default="{ row }">
          <span v-for="(p, i) in row.passes" :key="i" :class="['dot', p ? 'ok' : 'bad']" :title="`#${i + 1} ${p ? '通过' : '未通过'}`" />
          <el-tag v-if="row.stale" type="warning" size="small" class="ml8">旧版</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="submitCount" label="提交次数" width="80" />
      <el-table-column label="最近评测" width="140">
        <template #default="{ row }">{{ fmtTime(row.gradedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="90">
        <template #default="{ row }">
          <el-button v-if="row.submissionId" link type="primary" @click="viewCode(row)">查看代码</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="codeVisible" :title="`${current?.studentName} 的代码`" width="760px" append-to-body>
      <el-tabs v-if="current" v-model="codeTab">
        <el-tab-pane :label="`评分代码（${Number(current.score ?? 0)} / ${Number(current.maxScore ?? 0)}）`" name="graded">
          <PyEditor v-if="current.submittedCode != null" :model-value="current.submittedCode" readonly height="300px" />
          <el-empty v-else description="还没有提交评分" :image-size="50" />
        </el-tab-pane>
        <el-tab-pane v-if="current.draftCode !== current.submittedCode" label="最新草稿（未提交）" name="draft">
          <PyEditor :model-value="current.draftCode" readonly height="300px" />
        </el-tab-pane>
      </el-tabs>
      <div v-if="current?.detail?.length" class="detail">
        <el-tag v-for="d in current.detail" :key="d.seq" :type="d.passed ? 'success' : 'danger'" class="mr4">
          #{{ d.seq }} {{ d.passed ? '通过' : d.status }}
        </el-tag>
        <pre v-if="firstError" class="err">{{ firstError }}</pre>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import PyEditor from '../../components/PyEditor.vue'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lessonId: number; page: any }>()
const data = ref<any>(null)
const error = ref('')
const showRef = ref(false)
const regrading = ref(false)
const codeVisible = ref(false)
const current = ref<any>(null)
const codeTab = ref('graded')
let timer: number | undefined

const base = computed(() => `/teacher/python/lessons/${props.lessonId}/pages/${props.page.id}`)
const graded = computed(() => (data.value?.rows || []).filter((r: any) => r.score != null))
const submitted = computed(() => graded.value.length)
const fullCount = computed(() => graded.value.filter((r: any) => Number(r.score) === Number(r.maxScore)).length)
const avg = computed(() => (graded.value.length ? (graded.value.reduce((s: number, r: any) => s + Number(r.score), 0) / graded.value.length).toFixed(1) : '-'))
const staleCount = computed(() => (data.value?.rows || []).filter((r: any) => r.stale).length)
const firstError = computed(() => current.value?.detail?.find((d: any) => d.stderr)?.stderr || '')

async function load() {
  try {
    data.value = await api.get(base.value)
    showRef.value = !!data.value.showReference
    error.value = ''
  } catch (err) {
    error.value = errMsg(err, '加载失败')
  }
}

async function toggleRef(v: string | number | boolean) {
  try {
    await api.put(`${base.value}/reference`, { show: !!v })
    ElMessage.success(v ? '已向学生开放参考代码' : '已关闭参考代码')
  } catch (err) {
    showRef.value = !v
    ElMessage.error(errMsg(err))
  }
}

async function regrade() {
  regrading.value = true
  try {
    const n = await api.post(`${base.value}/regrade`)
    ElMessage.success(`已重新评测 ${n} 份代码`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    regrading.value = false
  }
}

async function viewCode(row: any) {
  current.value = await api.get(`/teacher/python/submissions/${row.submissionId}`)
  codeTab.value = 'graded'
  codeVisible.value = true
}

onMounted(() => {
  load()
  timer = window.setInterval(load, 15000)
})
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 8px; }
.ops { display: flex; align-items: center; gap: 8px; }
.stats { display: flex; gap: 6px; margin-bottom: 8px; flex-wrap: wrap; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ml8 { margin-left: 8px; }
.mr4 { margin-right: 4px; }
.full { color: var(--el-color-success); font-weight: 600; }
.dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-right: 3px; }
.dot.ok { background: var(--el-color-success); }
.dot.bad { background: var(--el-color-danger); }
.detail { margin-top: 8px; }
.err { color: var(--el-color-danger); background: var(--el-color-danger-light-9); padding: 8px; border-radius: 4px; white-space: pre-wrap; font-size: 12px; }
</style>
