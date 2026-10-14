<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button label="mine">我的题目</el-radio-button>
          <el-radio-button label="shared">共享题目</el-radio-button>
        </el-radio-group>
        <div class="filters">
          <el-input v-model="keyword" placeholder="搜索标题" clearable class="w200" @change="load" />
          <el-button v-if="scope === 'mine'" type="primary" @click="open()">新建题目</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" stripe empty-text="暂无 Python 题目">
      <el-table-column label="题目" min-width="200">
        <template #default="{ row }">
          <b>{{ row.title }}</b>
          <el-tag v-if="row.practice" size="small" type="success" class="ml8">课后题库</el-tag>
          <el-tag v-if="row.shared && scope === 'mine'" size="small" class="ml8">共享</el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="scope === 'shared'" prop="teacherName" label="出题人" width="100" />
      <el-table-column label="测试点" width="90">
        <template #default="{ row }">{{ row.caseCount }} 个</template>
      </el-table-column>
      <el-table-column prop="totalScore" label="总分" width="70" />
      <el-table-column label="版本" width="70">
        <template #default="{ row }">v{{ row.version }}</template>
      </el-table-column>
      <el-table-column prop="submissionCount" label="作答" width="70" />
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="open(row)">{{ scope === 'mine' ? '编辑' : '查看' }}</el-button>
          <el-button link type="primary" @click="copy(row)">复制</el-button>
          <el-popconfirm v-if="scope === 'mine'" title="删除该题目？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="readonly ? '查看题目' : form.id ? `编辑题目（v${form.version}）` : '新建题目'" width="1000px" top="3vh" append-to-body>
      <el-form label-width="90px" :disabled="readonly">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="选项">
          <span class="muted mr8">单点时限</span>
          <el-input-number v-model="form.timeLimitMs" :min="100" :max="10000" :step="500" controls-position="right" class="w140" />
          <span class="muted ml8">毫秒</span>
          <el-checkbox v-model="form.practice" class="ml16">加入课后题库（学生课后可练习）</el-checkbox>
          <el-checkbox v-model="form.shared">共享给其他老师</el-checkbox>
        </el-form-item>
        <el-form-item label="题干"><RichEditor v-model="form.description" /></el-form-item>
        <el-form-item label="半成品代码">
          <div class="full">
            <PyEditor v-model="form.starterCode" :readonly="readonly" height="160px" />
            <Hint>学生打开题目时编辑器里的初始代码，可留空</Hint>
          </div>
        </el-form-item>
        <el-form-item label="参考代码">
          <div class="full">
            <PyEditor v-model="form.referenceCode" :readonly="readonly" height="180px" />
            <Hint>课堂中可「开放参考代码」给学生；也用于自动生成测试点的期望输出</Hint>
          </div>
        </el-form-item>
        <el-form-item label="测试点">
          <div class="full">
            <div class="case-tools">
              <div><Hint block>每个测试点：把「输入」作为标准输入运行学生代码，输出与「期望输出」一致即得分（忽略行尾空格和末尾空行）。合计 {{ total }} 分</Hint></div>
              <el-button v-if="!readonly" size="small" :loading="generating" :disabled="!form.referenceCode || !form.cases.length" @click="generate">用参考代码生成期望输出</el-button>
            </div>
            <div v-for="(c, i) in form.cases" :key="i" class="case">
              <div class="case-head">
                <b>#{{ i + 1 }}</b>
                <el-input v-model="c.name" placeholder="名称（可选）" size="small" class="w140" />
                <span class="muted">分值</span>
                <el-input-number v-model="c.score" :min="0" :max="100" size="small" controls-position="right" class="w100" />
                <el-checkbox v-model="c.hidden" size="small">隐藏（学生看不到输入输出）</el-checkbox>
                <span class="spacer" />
                <el-button v-if="!readonly" link type="danger" size="small" @click="form.cases.splice(i, 1)">删除</el-button>
              </div>
              <div class="case-io">
                <el-input v-model="c.input" type="textarea" :rows="3" placeholder="输入（标准输入，可多行）" class="mono" />
                <el-input v-model="c.expected" type="textarea" :rows="3" placeholder="期望输出" class="mono" />
              </div>
              <div v-if="genErrors[i]" class="gen-err">参考代码在此输入下出错：{{ genErrors[i] }}</div>
            </div>
            <el-button v-if="!readonly" size="small" @click="addCase">+ 添加测试点</el-button>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span v-if="form.id && !readonly" class="muted footer-tip">修改测试点会使版本号 +1，已有成绩会标记为「需重测」</span>
        <el-button @click="visible = false">{{ readonly ? '关闭' : '取消' }}</el-button>
        <el-button v-if="!readonly" type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RichEditor from '../../components/RichEditor.vue'
import PyEditor from '../../components/PyEditor.vue'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const scope = ref<'mine' | 'shared'>('mine')
const keyword = ref('')
const list = ref<any[]>([])
const visible = ref(false)
const readonly = ref(false)
const saving = ref(false)
const generating = ref(false)
const genErrors = ref<Record<number, string>>({})
const form = reactive<any>({ cases: [] })
const total = computed(() => form.cases.reduce((s: number, c: any) => s + (c.score || 0), 0))

async function load() {
  list.value = await api.get('/teacher/python/problems', { shared: scope.value === 'shared', keyword: keyword.value || undefined })
}

function addCase() {
  form.cases.push({ name: '', input: '', expected: '', score: 10, hidden: false })
}

async function open(row?: any) {
  genErrors.value = {}
  readonly.value = scope.value === 'shared'
  if (row) {
    const p = await api.get(`/teacher/python/problems/${row.id}`)
    Object.assign(form, {
      id: p.id, version: p.version, title: p.title, description: p.description || '', starterCode: p.starterCode || '',
      referenceCode: p.referenceCode || '', timeLimitMs: p.timeLimitMs, shared: p.shared, practice: p.practice,
      cases: p.cases.map((c: any) => ({ name: c.name || '', input: c.input, expected: c.expected, score: c.score, hidden: c.hidden }))
    })
  } else {
    Object.assign(form, {
      id: null, version: 1, title: '', description: '', starterCode: '# 在这里编写代码\n', referenceCode: '', timeLimitMs: 2000,
      shared: false, practice: false, cases: [{ name: '样例', input: '', expected: '', score: 50, hidden: false }, { name: '', input: '', expected: '', score: 50, hidden: true }]
    })
  }
  visible.value = true
}

async function generate() {
  generating.value = true
  genErrors.value = {}
  try {
    const res = await api.post('/teacher/python/run', {
      code: form.referenceCode, inputs: form.cases.map((c: any) => c.input), timeLimitMs: form.timeLimitMs
    })
    res.forEach((r: any, i: number) => {
      if (r.status === 'OK') form.cases[i].expected = r.stdout.replace(/\s+$/, '')
      else genErrors.value[i] = r.status === 'TLE' ? '运行超时' : (r.stderr || '运行出错').trim().split('\n').slice(-1)[0]
    })
    const bad = Object.keys(genErrors.value).length
    if (bad) ElMessage.warning(`${bad} 个测试点生成失败，请检查参考代码或输入`)
    else ElMessage.success('已生成全部期望输出')
  } catch (err) {
    ElMessage.error(errMsg(err, '运行失败'))
  } finally {
    generating.value = false
  }
}

async function save() {
  const body = { ...form }
  saving.value = true
  try {
    if (form.id) await api.put(`/teacher/python/problems/${form.id}`, body)
    else await api.post('/teacher/python/problems', body)
    visible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function copy(row: any) {
  try {
    await api.post(`/teacher/python/problems/${row.id}/copy`)
    ElMessage.success('已复制到我的题目')
    if (scope.value === 'mine') await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(row: any) {
  try {
    await api.del(`/teacher/python/problems/${row.id}`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.full { width: 100%; }
.w100 { width: 100px; }
.w140 { width: 140px; }
.w200 { width: 200px; }
.ml8 { margin-left: 8px; }
.ml16 { margin-left: 16px; }
.mr8 { margin-right: 8px; }
.case-tools { display: flex; justify-content: space-between; align-items: center; gap: 8px; margin-bottom: 8px; }
.case { border: 1px solid var(--el-border-color-lighter); border-radius: 4px; padding: 8px; margin-bottom: 8px; }
.case-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.case-io { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.mono :deep(textarea) { font-family: Consolas, monospace; }
.spacer { flex: 1; }
.gen-err { color: var(--el-color-danger); font-size: 12px; margin-top: 4px; }
.footer-tip { margin-right: 12px; }
</style>
