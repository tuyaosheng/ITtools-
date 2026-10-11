<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>学习资源</span>
        <div class="filters">
          <el-select v-model="category" placeholder="全部分类" clearable class="w140">
            <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
          </el-select>
          <el-select v-model="term" placeholder="全部学期" clearable class="w160">
            <el-option v-for="t in termsList" :key="t" :label="t" :value="t" />
          </el-select>
          <el-input v-model="kw" placeholder="搜索标题" clearable class="w180" />
        </div>
      </div>
    </template>

    <el-empty v-if="!shown.length" description="老师还没有给你们班分发资源" />
    <div class="grid">
      <el-card v-for="r in shown" :key="r.id" shadow="hover">
        <div class="r-head">
          <b>{{ r.title }}</b>
          <el-tag v-if="r.category" size="small">{{ r.category }}</el-tag>
        </div>
        <div class="muted">{{ r.teacherName }} · {{ r.termLabel || '未分学期' }} · {{ fmtTime(r.createdAt) }}</div>
        <p v-if="r.description" class="desc">{{ r.description }}</p>
        <div class="ops">
          <template v-if="r.fileName">
            <el-button size="small" type="primary" @click="download(r)">下载 {{ fmtSize(r.fileSize) }}</el-button>
            <el-button v-if="previewable(r.fileName)" size="small" @click="preview(r)">在线查看</el-button>
          </template>
          <el-button v-if="r.linkUrl" size="small" tag="a" :href="r.linkUrl" target="_blank">打开链接</el-button>
        </div>
      </el-card>
    </div>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg, fileUrl } from '../../api/client'
import { fmtSize, fmtTime } from '../../utils/format'

const list = ref<any[]>([])
const category = ref<string>()
const term = ref<string>()
const kw = ref('')

const categories = computed(() => [...new Set(list.value.map(r => r.category).filter(Boolean))])
const termsList = computed(() => [...new Set(list.value.map(r => r.termLabel).filter(Boolean))])
const shown = computed(() => list.value.filter(r =>
  (!category.value || r.category === category.value) &&
  (!term.value || r.termLabel === term.value) &&
  (!kw.value || r.title.includes(kw.value))))

function previewable(name: string) {
  return /\.(pdf|png|jpe?g|gif|bmp|webp|svg|mp4|webm|mp3|txt|html?)$/i.test(name)
}

function preview(r: any) {
  window.open(fileUrl(`/resources/${r.id}/file?inline=true`), '_blank')
}

async function download(r: any) {
  try {
    await api.download(`/resources/${r.id}/file`, r.fileName)
  } catch (err) {
    ElMessage.error(errMsg(err, '下载失败'))
  }
}

onMounted(async () => { list.value = await api.get('/student/resources') })
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.filters { display: flex; gap: 8px; }
.muted { color: #909399; font-size: 12px; }
.w140 { width: 140px; }
.w160 { width: 160px; }
.w180 { width: 180px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 14px; }
.r-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
.desc { font-size: 13px; color: #606266; margin: 8px 0; white-space: pre-wrap; }
.ops { margin-top: 8px; display: flex; gap: 6px; flex-wrap: wrap; }
</style>
