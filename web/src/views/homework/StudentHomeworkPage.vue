<template>
  <div v-if="data" class="hw">
    <el-divider content-position="left">提交作品</el-divider>
    <div class="submit-box">
      <el-alert v-if="data.config.allowedExts" type="info" :closable="false" :title="`允许的文件类型：${data.config.allowedExts}`" />
      <el-alert v-if="!data.canSubmit" type="warning" :closable="false" title="课堂已结束，暂不接收提交" />
      <template v-else>
        <el-input v-model="description" placeholder="作品说明（可选）" maxlength="200" />
        <div class="upload-row">
          <input ref="fileInput" type="file" @change="onFile" />
          <el-button type="primary" :loading="uploading" :disabled="!file" @click="submit">
            {{ data.submission ? '重新提交' : '提交作品' }}
          </el-button>
        </div>
      </template>
    </div>

    <el-card v-if="sub" shadow="never" class="result">
      <div class="res-head">
        <div>
          <b>已提交：</b>
          <el-link type="primary" @click="download(sub.id, sub.fileName)">{{ sub.fileName }}</el-link>
          <span class="muted">（{{ fmtSize(sub.fileSize) }} · {{ fmtTime(sub.submittedAt) }} · 第 {{ sub.submitCount }} 次提交{{ sub.late ? ' · 补交' : '' }}）</span>
        </div>
        <el-tag v-if="sub.essence" type="warning" effect="dark">精华作品</el-tag>
      </div>
      <div class="scores">
        <div v-if="sub.autoMax != null" class="score-box"><span>自动评分</span><b>{{ sub.autoScore }}/{{ sub.autoMax }}</b></div>
        <div class="score-box"><span>老师评分</span><b>{{ sub.teacherScore ?? '待批阅' }}</b></div>
        <div v-if="data.config.peerReview" class="score-box"><span>同学互评</span><b>{{ sub.peerAvg ?? '—' }}</b><small>{{ sub.peerCount }} 人评</small></div>
      </div>
      <div v-if="sub.teacherComment" class="comment"><b>老师评语：</b>{{ sub.teacherComment }}</div>
      <div v-if="rubricRows.length" class="rubric">
        <div v-for="r in rubricRows" :key="r.id" class="rubric-row"><span>{{ r.name }}</span><b>{{ r.score ?? '-' }}/{{ r.maxScore }}</b></div>
      </div>
      <AutoDetail v-if="sub.autoDetail" :detail="sub.autoDetail" class="mt" />
      <div v-if="sub.reviews?.length" class="reviews">
        <div class="sub-title">同学评价</div>
        <div v-for="(r, i) in sub.reviews" :key="i" class="review">{{ r.score }} 分 · {{ r.comment || '（无评语）' }}</div>
      </div>
    </el-card>

    <template v-if="data.config.showClassmates || data.config.peerReview">
      <el-divider content-position="left">同学作品{{ data.config.peerReview ? '（可互评）' : '' }}</el-divider>
      <el-button size="small" @click="loadClassmates">刷新</el-button>
      <el-table :data="classmates" size="small" class="mt">
        <el-table-column prop="studentName" label="同学" width="90" />
        <el-table-column label="作品">
          <template #default="{ row }: { row: any }"><el-link type="primary" @click="download(row.id, row.fileName)">{{ row.fileName }}</el-link></template>
        </el-table-column>
        <el-table-column prop="description" label="说明" show-overflow-tooltip />
        <el-table-column prop="peerAvg" label="平均分" width="70" />
        <el-table-column v-if="data.config.peerReview" label="我的评分" width="260">
          <template #default="{ row }: { row: any }">
            <div class="peer">
              <el-input-number v-model="row.myScore" :min="0" :max="100" size="small" controls-position="right" class="w90" />
              <el-input v-model="row.myComment" size="small" placeholder="评语" />
              <el-button size="small" type="primary" @click="review(row)">评</el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtSize, fmtTime } from '../../utils/format'
import AutoDetail from './AutoDetail.vue'

const props = defineProps<{ lessonId: number; page: any }>()
const data = ref<any>(null)
const file = ref<File | null>(null)
const description = ref('')
const uploading = ref(false)
const classmates = ref<any[]>([])
const fileInput = ref<HTMLInputElement>()

const sub = computed(() => data.value?.submission)
const rubricRows = computed(() => {
  if (!data.value?.rubric || !sub.value?.rubricScores) return []
  const scores = JSON.parse(sub.value.rubricScores)
  return data.value.rubric.map((i: any) => ({ ...i, score: scores[i.id] }))
})

const base = () => `/student/homework/lessons/${props.lessonId}/pages/${props.page.id}`

async function load() {
  data.value = await api.get(base())
  description.value = data.value.submission?.description || ''
  if (data.value.config.showClassmates || data.value.config.peerReview) await loadClassmates()
}

async function loadClassmates() {
  classmates.value = await api.get(base() + '/classmates')
}

function onFile(e: Event) {
  file.value = (e.target as HTMLInputElement).files?.[0] ?? null
}

async function submit() {
  if (!file.value) return
  uploading.value = true
  try {
    data.value = await api.upload(base(), file.value, { description: description.value })
    ElMessage.success('提交成功')
    file.value = null
    if (fileInput.value) fileInput.value.value = ''
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    uploading.value = false
  }
}

async function review(row: any) {
  try {
    await api.post(`/student/homework/submissions/${row.id}/review`, { score: row.myScore, comment: row.myComment })
    ElMessage.success('已评价')
    await loadClassmates()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function download(id: number, name: string) {
  api.download(`/homework/submissions/${id}/file`, name)
}

watch(() => props.page.id, load)
onMounted(load)
</script>

<style scoped>
.submit-box { display: flex; flex-direction: column; gap: 10px; }
.upload-row { display: flex; align-items: center; gap: 12px; }
.result { margin-top: 14px; }
.res-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.scores { display: flex; gap: 12px; margin: 12px 0; flex-wrap: wrap; }
.score-box { display: flex; flex-direction: column; align-items: center; padding: 8px 16px; background: #f5f7fa; border-radius: 8px; min-width: 90px; }
.score-box span { font-size: 12px; color: #909399; }
.score-box b { font-size: 20px; color: var(--el-color-primary); }
.score-box small { font-size: 11px; color: #a8abb2; }
.comment { background: #fdf6ec; padding: 8px 12px; border-radius: 6px; margin-bottom: 10px; }
.rubric { display: grid; grid-template-columns: repeat(auto-fill, minmax(160px, 1fr)); gap: 6px; margin-bottom: 10px; }
.rubric-row { display: flex; justify-content: space-between; background: #f5f7fa; padding: 6px 10px; border-radius: 6px; font-size: 13px; }
.mt { margin-top: 10px; }
.reviews { margin-top: 10px; }
.sub-title { font-weight: 600; margin-bottom: 6px; }
.review { font-size: 13px; padding: 4px 0; border-bottom: 1px dashed #ebeef5; }
.peer { display: flex; gap: 4px; }
.w90 { width: 90px; flex-shrink: 0; }
</style>
