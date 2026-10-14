<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>师生答疑</span>
        <el-radio-group v-model="pendingOnly" size="small" @change="load">
          <el-radio-button :label="true">待答复</el-radio-button>
          <el-radio-button :label="false">全部</el-radio-button>
        </el-radio-group>
      </div>
    </template>
    <el-empty v-if="!list.length" :description="pendingOnly ? '没有待答复的问题' : '还没有学生提问'" />
    <div v-for="q in list" :key="q.id" class="q">
      <div class="q-head">
        <b>{{ q.title }}</b>
        <el-tag :type="q.answer ? 'success' : 'warning'" size="small">{{ q.answer ? '已答复' : '待答复' }}</el-tag>
        <span class="muted meta">{{ q.className }} {{ q.studentName }} · {{ fmtTime(q.createdAt) }}</span>
        <el-popconfirm title="删除这个问题？" @confirm="remove(q)">
          <template #reference><el-button link type="danger" size="small">删除</el-button></template>
        </el-popconfirm>
      </div>
      <div class="pre">{{ q.content }}</div>
      <el-input v-model="drafts[q.id]" type="textarea" :rows="3" maxlength="4000" placeholder="写下答复…" class="mt8" />
      <div class="ops">
        <el-checkbox v-model="publics[q.id]">公开到「公开问答」（不显示学生姓名）</el-checkbox>
        <span v-if="q.answeredAt" class="muted">上次答复 {{ fmtTime(q.answeredAt) }}</span>
        <el-button type="primary" size="small" @click="answer(q)">{{ q.answer ? '更新答复' : '答复' }}</el-button>
      </div>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useNoticeStore } from '../../stores/notices'
import { fmtTime } from '../../utils/format'

const notices = useNoticeStore()
const pendingOnly = ref(true)
const list = ref<any[]>([])
const drafts = reactive<Record<number, string>>({})
const publics = reactive<Record<number, boolean>>({})

async function load() {
  list.value = await api.get('/teacher/qa', { pending: pendingOnly.value })
  for (const q of list.value) {
    drafts[q.id] = q.answer || ''
    publics[q.id] = q.isPublic
  }
}

async function answer(q: any) {
  try {
    await api.put(`/teacher/qa/${q.id}/answer`, { answer: drafts[q.id], isPublic: publics[q.id] })
    ElMessage.success('已答复，学生会收到系统消息')
    await load()
    notices.refresh()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(q: any) {
  await api.del(`/teacher/qa/${q.id}`)
  await load()
  notices.refresh()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.q { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 10px 12px; margin-bottom: 10px; }
.q-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 6px; }
.meta { margin-left: auto; }
.pre { white-space: pre-wrap; font-size: 14px; line-height: 1.6; }
.mt8 { margin-top: 8px; }
.ops { display: flex; align-items: center; gap: 10px; margin-top: 6px; justify-content: flex-end; }
</style>
