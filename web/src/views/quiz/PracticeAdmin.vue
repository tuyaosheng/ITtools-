<template>
  <el-tabs v-model="sub" tab-position="left" class="pa" @tab-change="load">
    <el-tab-pane label="指定刷题题库" name="sets">
      <div class="bar">
        <span class="muted">把一份试卷开放给班级，学生在「刷题练习 → 指定题库」中抽题练习。也可以在测验详情的「更多」里一键生成。</span>
        <el-button type="primary" size="small" @click="openSet()">新建</el-button>
      </div>
      <el-table :data="sets" size="small" stripe empty-text="还没有刷题题库">
        <el-table-column prop="name" label="名称" min-width="160" />
        <el-table-column prop="paperName" label="试卷" min-width="140" />
        <el-table-column prop="questionCount" label="题数" width="60" />
        <el-table-column label="开放班级" min-width="140">
          <template #default="{ row }: { row: any }"><el-tag v-for="id in row.classIds" :key="id" size="small" class="mr4">{{ className(id) }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="sessionCount" label="练习次数" width="80" />
        <el-table-column label="启用" width="70">
          <template #default="{ row }: { row: any }"><el-switch :model-value="row.enabled" size="small" @change="(v: any) => saveSet({ ...row, enabled: !!v })" /></template>
        </el-table-column>
        <el-table-column label="操作" width="110">
          <template #default="{ row }: { row: any }">
            <el-button link type="primary" @click="openSet(row)">编辑</el-button>
            <el-popconfirm title="删除该刷题题库？" @confirm="delSet(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="学生刷题情况" name="report">
      <div class="bar">
        <el-select v-model="classId" placeholder="选择班级" class="w200" @change="loadReport">
          <el-option v-for="k in classes" :key="k.id" :label="k.name" :value="k.id" />
        </el-select>
      </div>
      <el-table :data="report" size="small" stripe empty-text="请选择班级">
        <el-table-column prop="studentNo" label="学号" width="70" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column prop="answered" label="刷题数" width="80" sortable />
        <el-table-column label="正确率" width="90" sortable :sort-method="(a: any, b: any) => acc(a) - acc(b)">
          <template #default="{ row }: { row: any }">{{ row.answered ? acc(row) + '%' : '—' }}</template>
        </el-table-column>
        <el-table-column prop="lastWeek" label="近 7 天" width="80" sortable />
        <el-table-column prop="sessions" label="练习卷" width="80" />
        <el-table-column label="最近刷题"><template #default="{ row }: { row: any }">{{ fmtTime(row.lastAt) }}</template></el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="错误率报表" name="errors">
      <div class="bar">
        <span class="muted">我的题目在作业、测验、刷题中的作答情况，作答不少于</span>
        <el-input-number v-model="minCount" :min="1" :max="100" size="small" @change="loadErrors" />
        <span class="muted">次才统计，按错误率从高到低</span>
      </div>
      <el-table :data="errors" size="small" stripe empty-text="暂无足够的作答数据">
        <el-table-column label="题目" min-width="260">
          <template #default="{ row }: { row: any }">
            <el-tag size="small" effect="plain" class="mr4">{{ typeLabel[row.qtype] }}</el-tag>
            <span v-if="row.stem" v-html="safeHtml(row.stem)" class="stem" />
            <span v-else class="muted">（图片题 #{{ row.id }}）</span>
          </template>
        </el-table-column>
        <el-table-column prop="answerCount" label="作答" width="70" />
        <el-table-column prop="wrongCount" label="答错" width="70" />
        <el-table-column label="错误率" width="160">
          <template #default="{ row }: { row: any }"><el-progress :percentage="Number(row.errorRate)" :status="row.errorRate >= 60 ? 'exception' : undefined" /></template>
        </el-table-column>
      </el-table>
    </el-tab-pane>

    <el-tab-pane label="题库查重" name="dups">
      <div class="bar"><span class="muted">题型相同、去掉空格标点后题干相同的文字题。可以把多余的移入回收站（图片题在批量传图时已自动去重）。</span></div>
      <el-empty v-if="!dups.length" description="没有发现重复题目" />
      <div v-for="(g, gi) in dups" :key="gi" class="dup">
        <div class="muted">第 {{ gi + 1 }} 组 · {{ typeLabel[g.qtype] }} · {{ g.questions.length }} 题</div>
        <div v-for="x in g.questions" :key="x.id" class="dup-q">
          <span v-html="safeHtml(x.stem)" class="stem" />
          <span class="muted">答案 {{ Array.isArray(x.answer) ? x.answer.join('/') : x.answer }} · {{ fmtTime(x.createdAt) }}</span>
          <el-popconfirm title="移入回收站？" @confirm="trash(x)">
            <template #reference><el-button link type="danger" size="small">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </el-tab-pane>
  </el-tabs>

  <el-dialog v-model="setVisible" :title="setForm.id ? '编辑刷题题库' : '新建刷题题库'" width="520px" append-to-body>
    <el-form label-width="80px">
      <el-form-item label="名称" required><el-input v-model="setForm.name" maxlength="128" /></el-form-item>
      <el-form-item label="试卷" required>
        <el-select v-model="setForm.paperId" filterable class="full">
          <el-option v-for="p in papers" :key="p.id" :label="`${p.name}（${p.questionCount} 题）`" :value="p.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="开放班级" required>
        <el-checkbox-group v-model="setForm.classIds">
          <el-checkbox v-for="k in classes" :key="k.id" :value="k.id">{{ k.name }}</el-checkbox>
        </el-checkbox-group>
      </el-form-item>
      <el-form-item label="启用"><el-switch v-model="setForm.enabled" /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="setVisible = false">取消</el-button>
      <el-button type="primary" @click="saveSet(setForm, true)">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'

const typeLabel: Record<string, string> = { SINGLE: '单选', MULTI: '多选', JUDGE: '判断', FILL: '填空' }
const sub = ref('sets')
const sets = ref<any[]>([])
const classes = ref<any[]>([])
const papers = ref<any[]>([])
const classId = ref<number>()
const report = ref<any[]>([])
const minCount = ref(5)
const errors = ref<any[]>([])
const dups = ref<any[]>([])
const setVisible = ref(false)
const setForm = reactive<any>({})

const className = (id: number) => classes.value.find(k => k.id === id)?.name || '#' + id
const acc = (r: any) => (r.answered ? Math.round((r.correct * 100) / r.answered) : 0)

async function load() {
  if (sub.value === 'sets') sets.value = await api.get('/teacher/quiz/practice-sets')
  if (sub.value === 'errors') await loadErrors()
  if (sub.value === 'dups') dups.value = await api.get('/teacher/quiz/duplicates')
}
async function loadReport() { report.value = classId.value ? await api.get('/teacher/quiz/practice-report', { classId: classId.value }) : [] }
async function loadErrors() { errors.value = await api.get('/teacher/quiz/error-rates', { minCount: minCount.value }) }

async function openSet(s?: any) {
  if (!papers.value.length) papers.value = await api.get('/teacher/quiz/papers')
  Object.assign(setForm, s ? { ...s, classIds: [...s.classIds] } : { id: null, name: '', paperId: undefined, classIds: [], enabled: true })
  setVisible.value = true
}

async function saveSet(s: any, close = false) {
  try {
    const body = { name: s.name, paperId: s.paperId, classIds: s.classIds, enabled: s.enabled }
    if (s.id) await api.put(`/teacher/quiz/practice-sets/${s.id}`, body)
    else await api.post('/teacher/quiz/practice-sets', body)
    if (close) setVisible.value = false
    sets.value = await api.get('/teacher/quiz/practice-sets')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delSet(s: any) {
  await api.del(`/teacher/quiz/practice-sets/${s.id}`)
  sets.value = await api.get('/teacher/quiz/practice-sets')
}

async function trash(x: any) {
  await api.del(`/teacher/quiz/questions/${x.id}`)
  dups.value = await api.get('/teacher/quiz/duplicates')
}

onMounted(async () => {
  classes.value = await api.get('/teacher/classes')
  await load()
})
</script>

<style scoped>
.pa { min-height: 360px; }
.bar { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 10px; }
.bar .muted { flex: 1; }
.muted { color: #909399; font-size: 12px; }
.w200 { width: 200px; }
.full { width: 100%; }
.mr4 { margin-right: 4px; }
.stem :deep(p) { display: inline; margin: 0; }
.dup { border: 1px solid #ebeef5; border-radius: 6px; padding: 8px 10px; margin-bottom: 10px; }
.dup-q { display: flex; align-items: center; gap: 10px; padding: 4px 0; border-top: 1px dashed #ebeef5; font-size: 13px; }
.dup-q .stem { flex: 1; }
</style>
