<template>
  <div>
    <el-divider content-position="left">讨论情况</el-divider>
    <div class="ops">
      <el-radio-group v-model="group" size="small" @change="load">
        <el-radio-button :value="-1">全部</el-radio-button>
        <el-radio-button v-for="g in groups" :key="g" :value="g">{{ g === 0 ? '未分组' : `第${g}组` }}</el-radio-button>
      </el-radio-group>
      <el-button size="small" @click="load">刷新</el-button>
    </div>
    <div class="layout">
      <div class="posts">
        <el-empty v-if="!posts.length" description="暂无发言" :image-size="40" />
        <div v-for="p in posts" :key="p.id" class="post" :class="{ conclusion: p.conclusion }">
          <span class="grp">{{ p.groupNo ? `第${p.groupNo}组` : '全班' }}</span>
          <b>{{ p.authorName }}</b><span class="muted"> {{ fmtTime(p.createdAt).slice(11) }}</span>
          <el-tag v-if="p.conclusion" size="small" type="success">结论</el-tag>
          <div class="content">{{ p.content }}</div>
        </div>
        <div class="send">
          <el-input v-model="content" size="small" :placeholder="group > 0 ? `对第${group}组说…` : '对全班说…'" @keyup.enter="post" />
          <el-button size="small" type="primary" :disabled="!content" @click="post">发送</el-button>
        </div>
      </div>
      <div class="concs">
        <div class="sub">各组结论</div>
        <el-empty v-if="!conclusions.length" description="暂无结论" :image-size="30" />
        <div v-for="c in conclusions" :key="c.groupNo" class="conc"><b>第 {{ c.groupNo }} 组</b>（{{ c.authorName }}）<div>{{ c.content }}</div></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'

const props = defineProps<{ lessonId: number; page: any }>()
const group = ref(-1)
const all = ref<any[]>([])
const posts = computed(() => (group.value < 0 ? all.value : all.value.filter(p => p.groupNo === group.value)))
const groups = computed(() => [...new Set(all.value.map(p => p.groupNo))].sort((a, b) => a - b))
const conclusions = ref<any[]>([])
const content = ref('')
let timer: number | undefined

async function load() {
  const d = await api.get(`/teacher/interact/lessons/${props.lessonId}/discuss/${props.page.id}`)
  all.value = d.posts
  conclusions.value = d.conclusions
}

async function post() {
  try {
    await api.post(`/teacher/interact/lessons/${props.lessonId}/discuss/${props.page.id}`, { content: content.value, groupNo: group.value > 0 ? group.value : 0 })
    content.value = ''
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => { await load(); timer = window.setInterval(load, 5000) })
onBeforeUnmount(() => window.clearInterval(timer))
</script>

<style scoped>
.ops { display: flex; gap: 8px; align-items: center; margin-bottom: 10px; flex-wrap: wrap; }
.layout { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; }
.posts { max-height: 420px; overflow-y: auto; }
.post { padding: 6px 0; border-bottom: 1px dashed #ebeef5; font-size: 13px; }
.post.conclusion { background: #f0f9eb; }
.grp { display: inline-block; background: #e5eaf3; border-radius: 4px; padding: 0 6px; margin-right: 6px; font-size: 12px; }
.content { margin-top: 2px; white-space: pre-wrap; }
.muted { color: #909399; font-size: 12px; }
.send { display: flex; gap: 6px; margin-top: 10px; }
.sub { font-weight: 600; margin-bottom: 8px; }
.conc { padding: 6px 8px; background: #f5f7fa; border-radius: 6px; margin-bottom: 6px; font-size: 13px; }
</style>
