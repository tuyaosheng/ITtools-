<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button label="mine">我的教案</el-radio-button>
          <el-radio-button label="shared">共享教案</el-radio-button>
        </el-radio-group>
        <div class="filters">
          <el-select v-model="termId" placeholder="全部学期" clearable class="w160" @change="load">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-upload v-if="scope === 'mine'" :show-file-list="false" accept=".zip" :http-request="importPack">
            <el-button>导入教案包</el-button>
          </el-upload>
          <el-button v-if="scope === 'mine'" type="primary" @click="open()">新建教案</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" stripe empty-text="暂无教案">
      <el-table-column label="教案名称" min-width="220">
        <template #default="{ row }">
          <el-link type="primary" @click="view(row)">{{ row.title }}</el-link>
          <el-tag v-if="row.shared && scope === 'mine'" size="small" type="success" class="ml8">共享</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="termLabel" label="学期" width="150" />
      <el-table-column v-if="scope === 'shared'" prop="teacherName" label="作者" width="100" />
      <el-table-column label="附件" min-width="200">
        <template #default="{ row }">
          <el-button v-if="row.docName" link type="primary" @click="dl(row, 'doc')">📄 {{ row.docName }}</el-button>
          <el-button v-if="row.pptName" link type="primary" @click="dl(row, 'ppt')">📊 {{ row.pptName }}</el-button>
        </template>
      </el-table-column>
      <el-table-column label="更新时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <template v-if="scope === 'mine'">
            <el-button link type="primary" @click="open(row)">编辑</el-button>
            <el-button link type="primary" @click="copy(row)">复制</el-button>
            <el-button link type="primary" @click="pack(row)">打包</el-button>
            <el-popconfirm title="删除该教案？" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
          <template v-else>
            <el-button link type="primary" @click="copy(row)">复制到我的教案</el-button>
            <el-button link type="primary" @click="pack(row)">打包</el-button>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑教案' : '新建教案'" width="820px" top="5vh" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="教案名称" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.termId" clearable class="w240">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <span class="ml8">共享</span>
          <el-switch v-model="form.shared" class="ml8" />
        </el-form-item>
        <el-form-item label="教学设计">
          <RichEditor v-model="form.content" />
          <div class="muted">可写教学目标、重难点、教学过程，以及课后的教学反思。</div>
        </el-form-item>
        <el-form-item label="教学资料">
          <input ref="docInput" type="file" @change="e => (doc = pick(e))" />
          <span v-if="form.docName && !doc" class="muted ml8">当前：{{ form.docName }}</span>
        </el-form-item>
        <el-form-item label="教学课件">
          <input ref="pptInput" type="file" @change="e => (ppt = pick(e))" />
          <span v-if="form.pptName && !ppt" class="muted ml8">当前：{{ form.pptName }}</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="viewVisible" :title="current?.title" width="820px" top="5vh" append-to-body>
      <div class="muted">{{ current?.teacherName }} · {{ current?.termLabel || '未分学期' }} · 更新于 {{ fmtTime(current?.updatedAt) }}</div>
      <div class="plan-body" v-html="safeHtml(current?.content)" />
      <div v-if="current?.docName || current?.pptName" class="atts">
        <el-button v-if="current?.docName" size="small" @click="dl(current, 'doc')">下载教学资料：{{ current.docName }}</el-button>
        <el-button v-if="current?.pptName" size="small" @click="dl(current, 'ppt')">下载课件：{{ current.pptName }}</el-button>
      </div>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import RichEditor from '../../components/RichEditor.vue'
import { api, errMsg } from '../../api/client'
import { http } from '../../api/http'
import { fmtTime, safeHtml } from '../../utils/format'

const scope = ref<'mine' | 'shared'>('mine')
const termId = ref<number>()
const terms = ref<any[]>([])
const list = ref<any[]>([])

const visible = ref(false)
const saving = ref(false)
const form = reactive<any>({})
const doc = ref<File | null>(null)
const ppt = ref<File | null>(null)
const docInput = ref<HTMLInputElement>()
const pptInput = ref<HTMLInputElement>()

const viewVisible = ref(false)
const current = ref<any>(null)

function pick(e: Event): File | null {
  return (e.target as HTMLInputElement).files?.[0] || null
}

async function load() {
  list.value = await api.get('/teacher/lesson-plans', { shared: scope.value === 'shared', termId: termId.value })
}

function open(r?: any) {
  doc.value = null
  ppt.value = null
  if (docInput.value) docInput.value.value = ''
  if (pptInput.value) pptInput.value.value = ''
  Object.assign(form, r
    ? { id: r.id, title: r.title, termId: r.termId, content: r.content || '', shared: r.shared, docName: r.docName, pptName: r.pptName }
    : { id: null, title: '', termId: terms.value.find(t => t.current)?.id, content: '', shared: false, docName: null, pptName: null })
  visible.value = true
}

async function save() {
  const fd = new FormData()
  fd.append('title', form.title || '')
  fd.append('termId', form.termId == null ? '' : String(form.termId))
  fd.append('content', form.content || '')
  fd.append('shared', String(!!form.shared))
  if (doc.value) fd.append('doc', doc.value)
  if (ppt.value) fd.append('ppt', ppt.value)
  saving.value = true
  try {
    await http.post(form.id ? `/teacher/lesson-plans/${form.id}` : '/teacher/lesson-plans', fd)
    visible.value = false
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

function view(r: any) {
  current.value = r
  viewVisible.value = true
}

/** 教案打包（原“打包”）：zip 中含教案内容、教学设计与课件，可在其他平台“导入教案包” */
async function pack(r: any) {
  try {
    await api.download(`/teacher/lesson-plans/${r.id}/pack`)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function importPack(o: any) {
  try {
    await api.upload('/teacher/lesson-plans/import', o.file, termId.value ? { termId: termId.value } : undefined)
    ElMessage.success('教案已导入')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function copy(r: any) {
  try {
    await api.post(`/teacher/lesson-plans/${r.id}/copy`)
    ElMessage.success('已复制到我的教案')
    if (scope.value === 'mine') await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(r: any) {
  try {
    await api.del(`/teacher/lesson-plans/${r.id}`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function dl(r: any, kind: 'doc' | 'ppt') {
  try {
    await api.download(`/teacher/lesson-plans/${r.id}/${kind}`, kind === 'doc' ? r.docName : r.pptName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

onMounted(async () => {
  terms.value = await api.get('/teacher/terms')
  await load()
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.w160 { width: 160px; }
.w240 { width: 240px; }
.ml8 { margin-left: 8px; }
.plan-body { margin: 12px 0; padding: 12px; border: 1px solid #ebeef5; border-radius: 4px; min-height: 120px; max-height: 60vh; overflow: auto; }
.atts { display: flex; gap: 8px; flex-wrap: wrap; }
</style>
