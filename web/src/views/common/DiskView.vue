<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>{{ isStudent ? '我的网盘' : '教师网盘' }}</span>
        <el-button type="primary" @click="open">上传文件</el-button>
      </div>
    </template>

    <div class="usage">
      <span>已用 {{ fmtSize(used) }}<template v-if="isStudent"> / {{ fmtSize(quota) }}</template></span>
      <el-progress v-if="isStudent" :percentage="percent" :status="percent >= 90 ? 'exception' : undefined" class="bar" />
      <span v-else class="muted">教师网盘不限容量</span>
    </div>

    <el-table :data="files" stripe empty-text="网盘还是空的">
      <el-table-column label="名称" min-width="220">
        <template #default="{ row }">
          <div><b>{{ row.title }}</b></div>
          <div class="muted">{{ row.fileName }}<template v-if="row.note"> · {{ row.note }}</template></div>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="100">
        <template #default="{ row }">{{ fmtSize(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column label="上传时间" width="150">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <el-button link type="primary" @click="download(row)">下载</el-button>
          <el-popconfirm title="删除该文件？" @confirm="remove(row)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="visible" title="上传文件" width="480px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="文件" required><input ref="fileInput" type="file" @change="onFile" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="title" placeholder="留空则使用文件名" maxlength="200" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="note" maxlength="512" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="upload">上传</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'
import { fmtSize, fmtTime } from '../../utils/format'

const auth = useAuthStore()
const isStudent = computed(() => auth.user?.role === 'STUDENT')
const files = ref<any[]>([])
const used = ref(0)
const quota = ref(0)
const percent = computed(() => (quota.value ? Math.min(100, Math.round((used.value * 100) / quota.value)) : 0))

const visible = ref(false)
const saving = ref(false)
const file = ref<File | null>(null)
const fileInput = ref<HTMLInputElement>()
const title = ref('')
const note = ref('')

function apply(d: any) {
  files.value = d.files
  used.value = Number(d.used)
  quota.value = Number(d.quota)
}

async function load() { apply(await api.get('/disk')) }

function open() {
  file.value = null
  title.value = ''
  note.value = ''
  if (fileInput.value) fileInput.value.value = ''
  visible.value = true
}

function onFile(e: Event) { file.value = (e.target as HTMLInputElement).files?.[0] || null }

async function upload() {
  if (!file.value) { ElMessage.warning('请选择文件'); return }
  saving.value = true
  try {
    apply(await api.upload('/disk', file.value, { title: title.value, note: note.value }))
    visible.value = false
  } catch (err) {
    ElMessage.error(errMsg(err, '上传失败'))
  } finally {
    saving.value = false
  }
}

async function remove(r: any) {
  try {
    await api.del(`/disk/${r.id}`)
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function download(r: any) {
  try {
    await api.download(`/disk/${r.id}/file`, r.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.usage { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; font-size: 13px; }
.bar { width: 260px; }
</style>
