<template>
  <el-card shadow="never">
    <el-tabs v-model="tab">
      <!-- ===== 题库 ===== -->
      <el-tab-pane label="题库" name="bank">
        <div class="filters">
          <el-select v-model="filter.qtype" clearable placeholder="全部题型" class="w140" @change="loadQuestions">
            <el-option v-for="(l, v) in typeLabel" :key="v" :label="l" :value="v" />
          </el-select>
          <el-select v-model="filter.category" clearable filterable placeholder="全部分类" class="w160" @change="loadQuestions">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-input v-model="filter.keyword" placeholder="搜索题干" clearable class="w200" @change="loadQuestions" />
          <div class="spacer" />
          <el-button @click="api.download('/teacher/quiz/questions/template')">下载导入模板</el-button>
          <el-upload :show-file-list="false" :http-request="importFile" accept=".xlsx,.xls,.csv">
            <el-button>Excel 导入</el-button>
          </el-upload>
          <el-button type="primary" @click="openQuestion()">新增题目</el-button>
        </div>
        <el-table :data="questionList" stripe size="small">
          <el-table-column label="题型" width="80"><template #default="{ row }: { row: any }">{{ typeLabel[row.qtype] }}</template></el-table-column>
          <el-table-column label="题干" min-width="280">
            <template #default="{ row }: { row: any }"><div class="stem" v-html="safeHtml(row.stem)" /></template>
          </el-table-column>
          <el-table-column label="答案" width="140">
            <template #default="{ row }: { row: any }">{{ answerText(row) }}</template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="100" />
          <el-table-column prop="score" label="分值" width="60" />
          <el-table-column label="来源" width="100">
            <template #default="{ row }: { row: any }">
              <el-tag v-if="row.teacherId !== me" size="small" type="info">{{ row.teacherName }}</el-tag>
              <el-tag v-else-if="row.shared" size="small">共享</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }: { row: any }">
              <template v-if="row.teacherId === me">
                <el-button link type="primary" @click="openQuestion(row)">编辑</el-button>
                <el-popconfirm title="删除该题？" @confirm="delQuestion(row)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <!-- ===== 试卷 ===== -->
      <el-tab-pane label="试卷" name="papers">
        <div class="filters">
          <div class="spacer" />
          <el-button type="primary" @click="openPaper()">新建试卷</el-button>
        </div>
        <el-table :data="paperList" stripe>
          <el-table-column prop="name" label="试卷名称" min-width="200" />
          <el-table-column prop="questionCount" label="题数" width="70" />
          <el-table-column prop="totalScore" label="总分" width="70" />
          <el-table-column label="作者" width="120">
            <template #default="{ row }: { row: any }">{{ row.teacherName }}<el-tag v-if="row.shared" size="small" class="ml">共享</el-tag></template>
          </el-table-column>
          <el-table-column label="操作" width="230">
            <template #default="{ row }: { row: any }">
              <el-button link type="primary" @click="openPublish(row)">发布测验</el-button>
              <template v-if="row.teacherId === me">
                <el-button link type="primary" @click="openPaper(row)">编辑</el-button>
                <el-popconfirm title="删除该试卷？" @confirm="delPaper(row)">
                  <template #reference><el-button link type="danger">删除</el-button></template>
                </el-popconfirm>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <!-- 题目编辑 -->
    <el-dialog v-model="qVisible" :title="qForm.id ? '编辑题目' : '新增题目'" width="640px" append-to-body>
      <el-form :model="qForm" label-width="70px">
        <el-form-item label="题型">
          <el-radio-group v-model="qForm.qtype">
            <el-radio-button v-for="(l, v) in typeLabel" :key="v" :value="v">{{ l }}</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="题干"><el-input v-model="qForm.stem" type="textarea" :rows="3" /></el-form-item>
        <template v-if="qForm.qtype === 'SINGLE' || qForm.qtype === 'MULTI'">
          <el-form-item v-for="(o, i) in qForm.options" :key="i" :label="'选项' + letter(i)">
            <div class="opt-row">
              <el-input v-model="qForm.options[i]" />
              <el-radio v-if="qForm.qtype === 'SINGLE'" v-model="qForm.answer" :value="letter(i)">正确</el-radio>
              <el-checkbox v-else :model-value="(qForm.answer || '').includes(letter(i))" @change="(v: any) => toggleMulti(letter(i), !!v)">正确</el-checkbox>
              <el-button link type="danger" :disabled="qForm.options.length <= 2" @click="removeOption(i)">删</el-button>
            </div>
          </el-form-item>
          <el-form-item><el-button size="small" :disabled="qForm.options.length >= 6" @click="qForm.options.push('')">+ 添加选项</el-button></el-form-item>
        </template>
        <el-form-item v-if="qForm.qtype === 'JUDGE'" label="答案">
          <el-radio-group v-model="qForm.answer"><el-radio value="T">对</el-radio><el-radio value="F">错</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item v-if="qForm.qtype === 'FILL'" label="答案">
          <div class="fill-answers">
            <div v-for="(b, i) in qForm.blanks" :key="i" class="opt-row">
              <el-input v-model="qForm.blanks[i]" :placeholder="`第 ${i + 1} 空答案，多个可接受答案用 | 分隔`" />
              <el-button link type="danger" :disabled="qForm.blanks.length <= 1" @click="qForm.blanks.splice(i, 1)">删</el-button>
            </div>
            <el-button size="small" @click="qForm.blanks.push('')">+ 添加一空</el-button>
          </div>
        </el-form-item>
        <el-form-item label="解析"><el-input v-model="qForm.analysis" type="textarea" :rows="2" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="qForm.category" filterable allow-create clearable placeholder="知识点分类" class="w200">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <span class="ml">分值</span>
          <el-input-number v-model="qForm.score" :min="0" :max="100" class="ml" />
          <el-checkbox v-model="qForm.shared" class="ml">共享（可用于学生刷题）</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="qVisible = false">取消</el-button>
        <el-button type="primary" @click="saveQuestion">保存</el-button>
      </template>
    </el-dialog>

    <!-- 试卷编辑 -->
    <el-dialog v-model="pVisible" :title="pForm.id ? '编辑试卷' : '新建试卷'" width="900px" top="5vh" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="pForm.name" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="pForm.description" /></el-form-item>
        <el-form-item label="共享"><el-switch v-model="pForm.shared" /></el-form-item>
      </el-form>
      <div class="paper-edit">
        <div>
          <div class="sub-title">试卷题目（{{ pForm.items.length }} 题，共 {{ paperTotal }} 分）</div>
          <div v-for="(it, i) in pForm.items" :key="it.questionId" class="p-item">
            <span class="no">{{ i + 1 }}.</span>
            <span class="p-stem">[{{ typeLabel[it.qtype] }}] {{ plain(it.stem) }}</span>
            <el-input-number v-model="it.score" :min="0" :max="100" size="small" controls-position="right" class="w90" />
            <el-button link size="small" :disabled="i === 0" @click="swap(i, -1)">↑</el-button>
            <el-button link size="small" type="danger" @click="pForm.items.splice(i, 1)">移除</el-button>
          </div>
          <div class="random">
            随机抽题：
            <span v-for="(l, v) in typeLabel" :key="v">{{ l }}<el-input-number v-model="randomCounts[v]" :min="0" :max="50" size="small" controls-position="right" class="w80" /></span>
            <el-button size="small" :disabled="!pForm.id" @click="randomFill">抽取</el-button>
            <span v-if="!pForm.id" class="muted">（先保存试卷）</span>
          </div>
        </div>
        <div>
          <div class="sub-title">从题库添加</div>
          <el-input v-model="pickKeyword" size="small" placeholder="搜索" class="mb" />
          <div class="pick-list">
            <div v-for="q in pickable" :key="q.id" class="pick" @click="addToPaper(q)">
              <span>[{{ typeLabel[q.qtype] }}] {{ plain(q.stem) }}</span><span class="muted">+{{ q.score }}</span>
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="pVisible = false">取消</el-button>
        <el-button type="primary" @click="savePaper">保存试卷</el-button>
      </template>
    </el-dialog>

    <PublishTestDialog v-model="publishVisible" :paper="publishPaper" />
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { safeHtml } from '../../utils/format'
import { useAuthStore } from '../../stores/auth'
import PublishTestDialog from './PublishTestDialog.vue'

const auth = useAuthStore()
const me = computed(() => auth.user?.userId)
const typeLabel: Record<string, string> = { SINGLE: '单选题', MULTI: '多选题', JUDGE: '判断题', FILL: '填空题' }
const letter = (i: number) => String.fromCharCode(65 + i)
const plain = (h: string) => (h || '').replace(/<[^>]+>/g, '').slice(0, 60)

const tab = ref('bank')
const questionList = ref<any[]>([])
const categories = ref<string[]>([])
const paperList = ref<any[]>([])
const filter = reactive({ qtype: '', category: '', keyword: '' })

const qVisible = ref(false)
const qForm = reactive<any>({})
const pVisible = ref(false)
const pForm = reactive<any>({ items: [] })
const pickKeyword = ref('')
const randomCounts = reactive<Record<string, number>>({ SINGLE: 0, MULTI: 0, JUDGE: 0, FILL: 0 })
const publishVisible = ref(false)
const publishPaper = ref<any>(null)

const paperTotal = computed(() => pForm.items.reduce((s: number, i: any) => s + (i.score || 0), 0))
const pickable = computed(() => questionList.value.filter(q =>
  !pForm.items.some((i: any) => i.questionId === q.id) && (!pickKeyword.value || q.stem.includes(pickKeyword.value))))

async function loadQuestions() {
  questionList.value = await api.get('/teacher/quiz/questions', { ...filter })
  categories.value = await api.get('/teacher/quiz/categories')
}
async function loadPapers() { paperList.value = await api.get('/teacher/quiz/papers') }

function answerText(q: any) {
  if (q.qtype === 'JUDGE') return q.answer === 'T' ? '对' : '错'
  if (q.qtype === 'FILL') return (q.answer || []).join(' / ')
  return q.answer
}

function openQuestion(q?: any) {
  Object.keys(qForm).forEach(k => delete qForm[k])
  Object.assign(qForm, q
    ? { ...q, options: [...(q.options.length ? q.options : ['', '', '', ''])], blanks: q.qtype === 'FILL' ? [...q.answer] : [''], answer: q.qtype === 'FILL' ? '' : q.answer }
    : { qtype: 'SINGLE', stem: '', options: ['', '', '', ''], answer: '', blanks: [''], analysis: '', category: filter.category || '', score: 5, shared: false })
  qVisible.value = true
}

function toggleMulti(l: string, on: boolean) {
  const set = new Set((qForm.answer || '').split(''))
  if (on) set.add(l); else set.delete(l)
  qForm.answer = [...set].filter(Boolean).sort().join('')
}

function removeOption(i: number) {
  qForm.options.splice(i, 1)
  qForm.answer = ''
}

async function saveQuestion() {
  const body = { ...qForm, answer: qForm.qtype === 'FILL' ? qForm.blanks : qForm.answer }
  try {
    if (qForm.id) await api.put(`/teacher/quiz/questions/${qForm.id}`, body)
    else await api.post('/teacher/quiz/questions', body)
    qVisible.value = false
    ElMessage.success('已保存')
    await loadQuestions()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delQuestion(q: any) {
  try { await api.del(`/teacher/quiz/questions/${q.id}`); await loadQuestions() } catch (err) { ElMessage.error(errMsg(err)) }
}

async function importFile(opt: UploadRequestOptions) {
  try {
    const r = await api.upload('/teacher/quiz/questions/import', opt.file)
    ElMessage.success(`导入 ${r.imported}/${r.total} 题${r.failed ? '，失败 ' + r.failed + ' 题：' + r.errors.map((e: any) => `第${e.row}行 ${e.message}`).join('；') : ''}`)
    await loadQuestions()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function openPaper(p?: any) {
  if (!questionList.value.length) await loadQuestions()
  if (p) {
    const full = await api.get(`/teacher/quiz/papers/${p.id}`)
    Object.assign(pForm, { id: full.id, name: full.name, description: full.description, shared: full.shared,
      items: full.questions.map((q: any) => ({ questionId: q.id, qtype: q.qtype, stem: q.stem, score: q.score })) })
  } else {
    Object.assign(pForm, { id: null, name: '', description: '', shared: false, items: [] })
  }
  pVisible.value = true
}

function addToPaper(q: any) { pForm.items.push({ questionId: q.id, qtype: q.qtype, stem: q.stem, score: q.score }) }
function swap(i: number, d: number) { const a = pForm.items; [a[i], a[i + d]] = [a[i + d], a[i]] }

async function savePaper() {
  try {
    const body = { name: pForm.name, description: pForm.description, shared: pForm.shared, items: pForm.items }
    const saved = pForm.id ? await api.put(`/teacher/quiz/papers/${pForm.id}`, body) : await api.post('/teacher/quiz/papers', body)
    pForm.id = saved.id
    ElMessage.success('试卷已保存')
    pVisible.value = false
    await loadPapers()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function randomFill() {
  try {
    await api.put(`/teacher/quiz/papers/${pForm.id}`, { name: pForm.name, description: pForm.description, shared: pForm.shared, items: pForm.items })
    const full = await api.post(`/teacher/quiz/papers/${pForm.id}/random`, { ...randomCounts, category: filter.category })
    pForm.items = full.questions.map((q: any) => ({ questionId: q.id, qtype: q.qtype, stem: q.stem, score: q.score }))
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function delPaper(p: any) {
  try { await api.del(`/teacher/quiz/papers/${p.id}`); await loadPapers() } catch (err) { ElMessage.error(errMsg(err)) }
}

function openPublish(p: any) { publishPaper.value = p; publishVisible.value = true }

onMounted(async () => { await loadQuestions(); await loadPapers() })
</script>

<style scoped>
.filters { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.spacer { flex: 1; }
.w140 { width: 140px; } .w160 { width: 160px; } .w200 { width: 200px; } .w90 { width: 90px; } .w80 { width: 80px; margin: 0 8px 0 4px; }
.ml { margin-left: 10px; }
.mb { margin-bottom: 8px; }
.muted { color: #909399; font-size: 12px; }
.stem :deep(p) { margin: 0; }
.opt-row { display: flex; gap: 8px; align-items: center; width: 100%; }
.fill-answers { display: flex; flex-direction: column; gap: 6px; width: 100%; }
.paper-edit { display: grid; grid-template-columns: 1.4fr 1fr; gap: 16px; }
.sub-title { font-weight: 600; margin-bottom: 8px; }
.p-item { display: flex; align-items: center; gap: 6px; padding: 4px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.p-stem { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.no { width: 22px; }
.random { margin-top: 10px; font-size: 13px; display: flex; flex-wrap: wrap; align-items: center; gap: 4px; }
.pick-list { max-height: 360px; overflow-y: auto; border: 1px solid #ebeef5; border-radius: 6px; }
.pick { display: flex; justify-content: space-between; padding: 6px 10px; cursor: pointer; font-size: 13px; border-bottom: 1px solid #f5f5f5; }
.pick:hover { background: var(--el-color-primary-light-9); }
</style>
