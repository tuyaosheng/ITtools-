<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <el-radio-group v-model="scope" @change="load">
          <el-radio-button label="mine">我的资源</el-radio-button>
          <el-radio-button label="shared">共享资源</el-radio-button>
        </el-radio-group>
        <div class="filters">
          <el-select v-model="category" placeholder="全部分类" clearable class="w140" @change="load">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="termId" placeholder="全部学期" clearable class="w160" @change="load">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
          <el-button v-if="scope === 'mine'" type="primary" @click="open()">添加资源</el-button>
        </div>
      </div>
    </template>

    <el-table :data="list" stripe empty-text="暂无资源">
      <el-table-column label="标题" min-width="200">
        <template #default="{ row }">
          <div><b>{{ row.title }}</b></div>
          <div v-if="row.description" class="muted ellipsis">{{ row.description }}</div>
        </template>
      </el-table-column>
      <el-table-column prop="category" label="分类" width="110" />
      <el-table-column prop="termLabel" label="学期" width="140" />
      <el-table-column label="内容" min-width="170">
        <template #default="{ row }">
          <div v-if="row.fileName">{{ row.fileName }} <span class="muted">{{ fmtSize(row.fileSize) }}</span></div>
          <el-link v-if="row.linkUrl" :href="row.linkUrl" target="_blank" type="primary">外部链接</el-link>
        </template>
      </el-table-column>
      <el-table-column v-if="scope === 'shared'" prop="teacherName" label="发布者" width="90" />
      <el-table-column v-else label="分发班级" min-width="140">
        <template #default="{ row }">
          <el-tag v-for="id in row.classIds" :key="id" size="small" class="mr4">{{ className(id) }}</el-tag>
          <span v-if="!row.classIds.length" class="muted">未分发</span>
        </template>
      </el-table-column>
      <el-table-column label="下载" width="60" prop="downloads" />
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.fileName" link type="primary" @click="download(row)">下载</el-button>
          <template v-if="scope === 'mine'">
            <el-button link type="primary" @click="openDistribute(row)">分发</el-button>
            <el-button link type="primary" :disabled="!row.classIds.length" @click="openReading(row)">阅读情况</el-button>
            <el-button link type="primary" @click="open(row)">编辑</el-button>
            <el-popconfirm title="删除该资源？学生将无法再查看" @confirm="remove(row)">
              <template #reference><el-button link type="danger">删除</el-button></template>
            </el-popconfirm>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" :title="form.id ? '编辑资源' : '添加资源'" width="560px" append-to-body>
      <el-form label-width="80px">
        <el-form-item label="标题" required><el-input v-model="form.title" maxlength="200" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" filterable allow-create clearable placeholder="选择或输入新分类" class="full">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="学期">
          <el-select v-model="form.termId" clearable class="full">
            <el-option v-for="t in terms" :key="t.id" :label="t.label" :value="t.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="文件">
          <input ref="fileInput" type="file" @change="onFile" />
          <div v-if="form.fileName && !file" class="muted">当前：{{ form.fileName }}（重新选择将替换）</div>
        </el-form-item>
        <el-form-item label="链接"><el-input v-model="form.linkUrl" placeholder="可选，如 https://..." /></el-form-item>
        <el-form-item label="共享"><el-switch v-model="form.shared" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="readVisible" :title="`阅读情况：${readOf?.title || ''}`" width="620px" append-to-body>
      <div class="read-bar">
        <el-radio-group v-model="readClass" size="small" @change="loadReading">
          <el-radio-button v-for="id in readOf?.classIds || []" :key="id" :value="id">{{ className(id) }}</el-radio-button>
        </el-radio-group>
        <span class="muted">已读 {{ readRows.filter(r => r.views).length }}/{{ readRows.length }} 人</span>
      </div>
      <el-table :data="readRows" size="small" stripe max-height="420">
        <el-table-column prop="studentNo" label="班内学号" width="84" />
        <el-table-column prop="name" label="姓名" width="100" />
        <el-table-column label="阅读时间" width="120" sortable :sort-method="(a: any, b: any) => a.seconds - b.seconds">
          <template #default="{ row }: { row: any }"><span :class="{ muted: !row.views }">{{ row.views ? mins(row.seconds) : '未读' }}</span></template>
        </el-table-column>
        <el-table-column prop="views" label="打开次数" width="90" />
        <el-table-column label="最近阅读"><template #default="{ row }: { row: any }">{{ row.lastAt && row.views ? fmtTime(row.lastAt) : '' }}</template></el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="distVisible" title="分发给班级" width="480px" append-to-body>
      <el-checkbox-group v-model="distClassIds">
        <el-checkbox v-for="k in classes" :key="k.id" :label="k.id">{{ k.name }}</el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="distVisible = false">取消</el-button>
        <el-button type="primary" @click="saveDistribute">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { http } from '../../api/http'
import { fmtSize, fmtTime } from '../../utils/format'

const scope = ref<'mine' | 'shared'>('mine')
const category = ref<string>()
const termId = ref<number>()
const list = ref<any[]>([])
const categories = ref<string[]>([])
const terms = ref<any[]>([])
const classes = ref<any[]>([])
const readVisible = ref(false)
const readOf = ref<any>(null)
const readClass = ref<number>()
const readRows = ref<any[]>([])
const mins = (s: number) => (s < 60 ? `${s} 秒` : `${Math.floor(s / 60)} 分 ${s % 60} 秒`)

async function openReading(row: any) {
  readOf.value = row
  readClass.value = row.classIds[0]
  readVisible.value = true
  await loadReading()
}

async function loadReading() {
  try {
    readRows.value = await api.get(`/teacher/resources/${readOf.value.id}/reading`, { classId: readClass.value })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const visible = ref(false)
const saving = ref(false)
const form = reactive<any>({})
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()

const distVisible = ref(false)
const distId = ref<number>()
const distClassIds = ref<number[]>([])

function className(id: number) {
  return classes.value.find(k => k.id === id)?.name || '#' + id
}

async function load() {
  list.value = await api.get('/teacher/resources', { shared: scope.value === 'shared', category: category.value || undefined, termId: termId.value })
}

function open(r?: any) {
  file.value = null
  if (fileInput.value) fileInput.value.value = ''
  Object.assign(form, r
    ? { id: r.id, title: r.title, category: r.category, termId: r.termId, description: r.description, linkUrl: r.linkUrl, shared: r.shared, fileName: r.fileName }
    : { id: null, title: '', category: category.value || '', termId: terms.value.find(t => t.current)?.id, description: '', linkUrl: '', shared: false, fileName: null })
  visible.value = true
}

function onFile(e: Event) {
  file.value = (e.target as HTMLInputElement).files?.[0] || null
}

async function save() {
  const fd = new FormData()
  for (const k of ['title', 'category', 'termId', 'description', 'linkUrl', 'shared']) {
    const v = form[k]
    fd.append(k, v == null ? '' : String(v))
  }
  if (file.value) fd.append('file', file.value)
  saving.value = true
  try {
    await http.post(form.id ? `/teacher/resources/${form.id}` : '/teacher/resources', fd)
    visible.value = false
    ElMessage.success('已保存')
    await Promise.all([load(), loadCategories()])
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

function openDistribute(r: any) {
  distId.value = r.id
  distClassIds.value = [...(r.classIds || [])]
  distVisible.value = true
}

async function saveDistribute() {
  try {
    await api.put(`/teacher/resources/${distId.value}/classes`, { classIds: distClassIds.value })
    distVisible.value = false
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(r: any) {
  try {
    await api.del(`/teacher/resources/${r.id}`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function download(r: any) {
  try {
    await api.download(`/resources/${r.id}/file`, r.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

async function loadCategories() { categories.value = await api.get('/teacher/resources/categories') }

onMounted(async () => {
  ;[terms.value, classes.value] = await Promise.all([api.get('/teacher/terms'), api.get('/teacher/classes')])
  await Promise.all([load(), loadCategories()])
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; align-items: center; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ellipsis { max-width: 320px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.w140 { width: 140px; }
.w160 { width: 160px; }
.full { width: 100%; }
.mr4 { margin-right: 4px; }
.ml8 { margin-left: 8px; }
.read-bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
</style>
