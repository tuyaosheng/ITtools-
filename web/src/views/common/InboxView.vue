<template>
  <div>
    <div class="bar">
      <span class="muted">共 {{ list.length }} 条，未读 {{ unread }} 条</span>
      <el-button size="small" :disabled="!unread" @click="readAll">全部标为已读</el-button>
    </div>
    <el-empty v-if="!list.length" description="没有消息" />
    <el-collapse v-model="opened" @change="onOpen">
      <el-collapse-item v-for="m in list" :key="m.id" :name="m.id">
        <template #title>
          <div class="title">
            <span v-if="!m.readAt" class="dot" title="未读" />
            <b :class="{ read: m.readAt }">{{ m.title }}</b>
            <span class="muted meta">{{ m.senderName }} · {{ fmtTime(m.createdAt) }}</span>
          </div>
        </template>
        <div class="content">{{ m.content || '（无正文）' }}</div>
        <el-popconfirm title="从收件箱删除这条消息？" @confirm="remove(m)">
          <template #reference><el-button link type="danger" size="small">删除</el-button></template>
        </el-popconfirm>
      </el-collapse-item>
    </el-collapse>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useNoticeStore } from '../../stores/notices'
import { fmtTime } from '../../utils/format'

const notices = useNoticeStore()
const list = ref<any[]>([])
const opened = ref<number[]>([])
const unread = computed(() => list.value.filter(m => !m.readAt).length)

async function load() {
  list.value = await api.get('/messages/inbox')
}

async function onOpen(names: any) {
  for (const id of names as number[]) {
    const m = list.value.find(x => x.id === id)
    if (m && !m.readAt) {
      m.readAt = new Date().toISOString()
      await api.post(`/messages/inbox/${id}/read`).catch(() => undefined)
    }
  }
  notices.refresh()
}

async function readAll() {
  await api.post('/messages/inbox/read-all')
  await load()
  notices.refresh()
}

async function remove(m: any) {
  try {
    await api.del(`/messages/inbox/${m.id}`)
    await load()
    notices.refresh()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
</script>

<style scoped>
.bar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; }
.muted { color: #909399; font-size: 12px; }
.title { display: flex; align-items: center; gap: 8px; width: 100%; overflow: hidden; }
.title b { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.title b.read { font-weight: normal; color: #606266; }
.meta { margin-left: auto; padding-right: 8px; white-space: nowrap; }
.dot { width: 8px; height: 8px; border-radius: 50%; background: #f56c6c; flex-shrink: 0; }
.content { white-space: pre-wrap; font-size: 14px; line-height: 1.7; margin-bottom: 4px; }
</style>
