<script setup lang="ts">
import { reactive, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, type ImportJob } from '../../api/admin'

type Kind = 'students' | 'teachers'
type Phase = 'idle' | 'uploading' | 'running' | 'done' | 'failed'

interface SectionState {
  file: File | null
  phase: Phase
  upload: number
  job: ImportJob | null
  error: string
}

const newState = (): SectionState => ({ file: null, phase: 'idle', upload: 0, job: null, error: '' })
const state = reactive<Record<Kind, SectionState>>({ students: newState(), teachers: newState() })
const timers: Partial<Record<Kind, number>> = {}
let alive = true
onBeforeUnmount(() => {
  alive = false
  Object.values(timers).forEach(t => window.clearTimeout(t))
})

function onFileChange(kind: Kind, uploadFile: any) {
  state[kind].file = uploadFile?.raw ?? null
}

async function downloadTemplate(kind: Kind) {
  try {
    if (kind === 'students') await adminApi.imports.downloadStudentTemplate()
    else await adminApi.imports.downloadTeacherTemplate()
  } catch (err: any) {
    ElMessage.error(err?.response?.data?.message || '下载模板失败')
  }
}

const busy = (kind: Kind) => state[kind].phase === 'uploading' || state[kind].phase === 'running'

async function doImport(kind: Kind) {
  const s = state[kind]
  if (!s.file) {
    ElMessage.warning('请先选择文件')
    return
  }
  s.phase = 'uploading'
  s.upload = 0
  s.job = null
  s.error = ''
  try {
    const onUpload = (p: number) => { s.upload = p }
    s.job = kind === 'students'
      ? await adminApi.imports.startStudentJob(s.file, onUpload)
      : await adminApi.imports.startTeacherJob(s.file, onUpload)
    s.phase = 'running'
    poll(kind)
  } catch (err: any) {
    fail(kind, err?.response?.data?.message || '上传失败')
  }
}

async function poll(kind: Kind) {
  const s = state[kind]
  if (!alive || !s.job) return
  try {
    const job = await adminApi.imports.job(s.job.id)
    s.job = job
    if (job.status === 'DONE') {
      s.phase = 'done'
      ElMessage({ type: job.failed > 0 ? 'warning' : 'success', message: `导入完成：成功 ${job.imported}，失败 ${job.failed}` })
      return
    }
    if (job.status === 'FAILED') {
      fail(kind, job.message || '导入失败')
      return
    }
  } catch (err: any) {
    // a dropped poll is not fatal; keep trying unless the job is gone
    if (err?.response?.status === 400) {
      fail(kind, err?.response?.data?.message || '导入任务丢失')
      return
    }
  }
  timers[kind] = window.setTimeout(() => poll(kind), 600)
}

function fail(kind: Kind, msg: string) {
  state[kind].phase = 'failed'
  state[kind].error = msg
  ElMessage.error(msg)
}

function percent(s: SectionState): number {
  if (s.phase === 'uploading') return s.upload
  const j = s.job
  if (!j) return 0
  if (s.phase === 'done') return 100
  return j.total > 0 ? Math.floor((j.processed / j.total) * 100) : 0
}

function progressText(s: SectionState): string {
  if (s.phase === 'uploading') return `上传中 ${s.upload}%`
  const j = s.job
  if (!j || j.status === 'QUEUED') return '排队中…'
  if (!j.total) return '读取文件…'
  return `正在导入 ${j.processed} / ${j.total}（成功 ${j.imported}，失败 ${j.failed}）`
}

const sections: { kind: Kind; title: string }[] = [
  { kind: 'students', title: '学生导入' },
  { kind: 'teachers', title: '教师导入' }
]
</script>

<template>
  <div class="import-view">
    <el-card v-for="sec in sections" :key="sec.kind" class="import-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">{{ sec.title }}</span>
          <el-button
            :data-test="sec.kind === 'students' ? 'dl-student-template' : 'dl-teacher-template'"
            size="small"
            @click="downloadTemplate(sec.kind)"
          >
            下载模板
          </el-button>
        </div>
      </template>
      <p v-if="sec.kind === 'students'" class="hint">
        支持 .xlsx / .xls / .csv。列：<b>姓名*</b>、<b>学号*</b>、班内学号、<b>年级码*</b>(如 2024)、<b>班级*</b>(如 1班)、初始密码(默认 123456)。
        年级/班级不存在时自动创建；带 * 为必填。
      </p>
      <p v-else class="hint">
        支持 .xlsx / .xls / .csv。列：<b>姓名*</b>、<b>登录名*</b>、初始密码(默认 123456)。带 * 为必填，登录名不可重复。
      </p>
      <el-upload
        :auto-upload="false"
        :limit="1"
        :disabled="busy(sec.kind)"
        accept=".xlsx,.xls,.csv"
        :on-change="(f: any) => onFileChange(sec.kind, f)"
        :on-remove="() => onFileChange(sec.kind, null)"
        :on-exceed="() => ElMessage.warning('一次只能选择一个文件')"
      >
        <el-button type="primary" plain :disabled="busy(sec.kind)">选择文件</el-button>
      </el-upload>
      <div class="actions">
        <el-button type="primary" :loading="busy(sec.kind)" :data-test="`start-${sec.kind}`" @click="doImport(sec.kind)">
          {{ busy(sec.kind) ? '导入中…' : '开始导入' }}
        </el-button>
      </div>

      <div v-if="state[sec.kind].phase !== 'idle'" class="result" :data-test="`result-${sec.kind}`">
        <template v-if="busy(sec.kind)">
          <el-progress
            :percentage="percent(state[sec.kind])"
            :stroke-width="14"
            :indeterminate="state[sec.kind].phase === 'running' && !state[sec.kind].job?.total"
            striped
            striped-flow
          />
          <div class="progress-text">{{ progressText(state[sec.kind]) }}</div>
        </template>

        <template v-else-if="state[sec.kind].phase === 'done' && state[sec.kind].job?.result">
          <el-progress
            :percentage="100"
            :stroke-width="14"
            :status="state[sec.kind].job!.result!.failed > 0 ? 'warning' : 'success'"
          />
          <el-alert
            class="summary"
            :title="`导入完成：共 ${state[sec.kind].job!.result!.total} 行，成功 ${state[sec.kind].job!.result!.imported}，失败 ${state[sec.kind].job!.result!.failed}`"
            :type="state[sec.kind].job!.result!.failed > 0 ? 'warning' : 'success'"
            :closable="false"
            show-icon
          />
          <el-table
            v-if="state[sec.kind].job!.result!.errors.length"
            :data="state[sec.kind].job!.result!.errors"
            size="small"
            border
            max-height="320"
            class="err-table"
          >
            <el-table-column prop="row" label="行号" width="90" />
            <el-table-column prop="message" label="失败原因" />
          </el-table>
        </template>

        <el-alert
          v-else-if="state[sec.kind].phase === 'failed'"
          :title="`导入失败：${state[sec.kind].error}`"
          description="本次没有写入任何数据，修改文件后重新导入即可。"
          type="error"
          :closable="false"
          show-icon
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.import-view {
  display: flex;
  flex-direction: column;
  gap: 20px;
  max-width: 820px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.card-header .title {
  font-weight: 600;
}
.hint {
  color: var(--el-text-color-secondary, var(--el-text-color-secondary));
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.actions {
  margin-top: 12px;
}
.result {
  margin-top: 16px;
}
.progress-text {
  margin-top: 8px;
  font-size: 13px;
  color: var(--el-text-color-regular);
}
.summary {
  margin-top: 12px;
}
.err-table {
  margin-top: 12px;
}
</style>
