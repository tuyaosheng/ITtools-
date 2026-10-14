<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <el-empty v-else-if="data && !data.groups.length" description="还没有小组，请先在「分组管理」中分组" />
  <div v-else-if="data" class="grid">
    <el-card v-for="g in data.groups" :key="g.groupNo" shadow="never">
      <div class="g-head">
        <b>第 {{ g.groupNo }} 组</b>
        <span class="topic">{{ g.topic || '（未设主题）' }}</span>
        <el-tag v-if="g.reviewCount" type="success" size="small" class="avg">互评 {{ g.reviewAvg }} 分 / {{ g.reviewCount }} 人</el-tag>
      </div>
      <el-table :data="g.members" size="small">
        <el-table-column label="组员" width="100">
          <template #default="{ row }"><span v-if="row.studentId === g.leaderId" class="crown">★</span>{{ row.name }}</template>
        </el-table-column>
        <el-table-column prop="task" label="分工" show-overflow-tooltip />
        <el-table-column label="组内评价" width="100">
          <template #default="{ row }">
            <span v-if="row.peerCount">{{ row.peerAvg }}★（{{ row.peerCount }}）</span>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div class="sec">成果：<span class="pre">{{ g.resultText || '（未提交）' }}</span></div>
      <div v-if="g.files.length" class="sec">
        资源（{{ g.files.length }}）：
        <span v-for="f in g.files" :key="f.id" class="file">
          <el-link type="primary" @click="download(f)">{{ f.title }}</el-link>
          <el-tag v-if="f.id === g.resultFileId" size="small" type="success">成果</el-tag>
        </span>
      </div>
      <el-collapse v-if="g.reviews.length">
        <el-collapse-item :title="`组间评价（${g.reviews.length}）`">
          <div v-for="(r, i) in g.reviews" :key="i" class="rv">{{ r.reviewerName }}：<b>{{ r.score }}</b> 分 <span class="muted">{{ r.comment }}</span></div>
        </el-collapse-item>
      </el-collapse>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'

const props = defineProps<{ lessonId: number; page?: any }>()
const data = ref<any>(null)
const error = ref('')
let timer: number | undefined

async function load() {
  try {
    data.value = await api.get(`/teacher/groups/lessons/${props.lessonId}/work`)
    error.value = ''
  } catch (err) {
    error.value = errMsg(err, '加载失败')
  }
}

async function download(f: any) {
  try {
    await api.download(`/groups/files/${f.id}`, f.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

useLessonChannel(props.lessonId, true, e => {
  if (e.type === 'GROUPS_CHANGED') {
    window.clearTimeout(timer)
    timer = window.setTimeout(load, 500)
  }
})
onMounted(load)
onBeforeUnmount(() => window.clearTimeout(timer))
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(340px, 1fr)); gap: 12px; }
.g-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.topic { color: var(--el-color-primary); font-size: 13px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.avg { margin-left: auto; }
.crown { color: var(--el-color-warning); margin-right: 2px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.sec { font-size: 13px; margin-top: 8px; }
.pre { white-space: pre-wrap; }
.file { margin-right: 8px; }
.rv { font-size: 13px; padding: 2px 0; }
</style>
