<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <el-empty v-else-if="data && !data.groups.length" description="还没有小组" />
  <div v-else-if="data" class="grid">
    <el-card v-for="g in data.groups" :key="g.groupNo" shadow="hover" :class="{ mine: g.isMine }">
      <div class="g-head">
        <b>第 {{ g.groupNo }} 组</b>
        <el-tag v-if="g.isMine" size="small">本组</el-tag>
        <span class="muted">{{ g.reviewCount }} 人已评</span>
      </div>
      <div class="topic">{{ g.topic || '（未设置研究主题）' }}</div>
      <div class="muted">组长：{{ g.leaderName || '未确定' }} · 组员：{{ g.members.map((m: any) => m.name).join('、') }}</div>
      <div class="result">{{ g.resultText || '（暂无成果说明）' }}</div>
      <div v-if="g.resultFileId" class="file">
        成果文件：{{ g.resultFileName }}
        <el-button link type="primary" @click="download(g)">下载</el-button>
        <el-button v-if="previewable(g.resultFileName)" link type="primary" @click="preview(g)">预览</el-button>
      </div>
      <div v-if="!g.isMine" class="review">
        <el-input-number v-model="scores[g.groupNo]" :min="0" :max="100" size="small" controls-position="right" class="w100" />
        <span class="muted">分</span>
        <el-input v-model="comments[g.groupNo]" size="small" maxlength="200" placeholder="评语（可选）" />
        <el-button size="small" :type="g.myScore != null ? 'default' : 'primary'" @click="review(g)">{{ g.myScore != null ? '更新' : '评分' }}</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg, fileUrl } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'

const props = defineProps<{ lessonId: number; page?: any }>()
const base = `/student/groups/lessons/${props.lessonId}/show`
const data = ref<any>(null)
const error = ref('')
const scores = reactive<Record<number, number | undefined>>({})
const comments = reactive<Record<number, string>>({})
let timer: number | undefined

function apply(d: any, overwrite = true) {
  data.value = d
  for (const g of d.groups) {
    if (overwrite || scores[g.groupNo] === undefined) {
      scores[g.groupNo] = g.myScore ?? undefined
      comments[g.groupNo] = g.myComment || ''
    }
  }
}

async function load(overwrite = true) {
  try {
    apply(await api.get(base), overwrite)
    error.value = ''
  } catch (err) {
    error.value = errMsg(err, '加载失败')
  }
}

async function review(g: any) {
  if (scores[g.groupNo] == null) { ElMessage.warning('请填写分数'); return }
  try {
    apply(await api.put(`${base}/${g.groupNo}`, { score: scores[g.groupNo], comment: comments[g.groupNo] }))
    ElMessage.success('已评分')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function previewable(name: string) {
  return /\.(pdf|png|jpe?g|gif|bmp|webp|svg|mp4|webm|mp3|txt|html?)$/i.test(name || '')
}

function preview(g: any) {
  window.open(fileUrl(`/groups/files/${g.resultFileId}?inline=true`), '_blank')
}

async function download(g: any) {
  try {
    await api.download(`/groups/files/${g.resultFileId}`, g.resultFileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

useLessonChannel(props.lessonId, false, e => {
  if (e.type === 'GROUPS_CHANGED') {
    window.clearTimeout(timer)
    timer = window.setTimeout(() => load(false), 300)
  }
})
onMounted(() => load())
</script>

<style scoped>
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(300px, 1fr)); gap: 12px; }
.mine { border-color: #409eff; }
.g-head { display: flex; align-items: center; gap: 8px; margin-bottom: 6px; }
.g-head .muted { margin-left: auto; }
.muted { color: #909399; font-size: 12px; }
.topic { font-weight: 600; color: #303133; margin-bottom: 4px; }
.result { white-space: pre-wrap; font-size: 13px; margin: 8px 0; padding: 8px; background: #f5f7fa; border-radius: 4px; min-height: 36px; }
.file { font-size: 13px; }
.review { display: flex; align-items: center; gap: 6px; margin-top: 10px; }
.w100 { width: 100px; flex-shrink: 0; }
</style>
