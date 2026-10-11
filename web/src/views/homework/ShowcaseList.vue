<template>
  <div>
    <div class="ops">
      <el-select v-model="yearId" clearable placeholder="全部年级" size="small" @change="load">
        <el-option v-for="y in years" :key="y.id" :label="y.label" :value="y.id" />
      </el-select>
    </div>
    <el-empty v-if="!list.length" description="还没有精华作品" />
    <div class="grid">
      <div v-for="w in list" :key="w.id" class="card">
        <div class="ext">{{ ext(w.fileName) }}</div>
        <div class="body">
          <div class="title" :title="w.fileName">{{ w.fileName }}</div>
          <div class="meta">{{ w.className }} · {{ w.studentName }}</div>
          <div class="meta">《{{ w.courseName }}》{{ w.pageTitle }}</div>
          <div v-if="w.description" class="desc">{{ w.description }}</div>
          <div v-if="w.teacherComment" class="comment">老师：{{ w.teacherComment }}</div>
          <el-button size="small" type="primary" plain @click="download(w)">查看作品</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../../api/client'
import { publicApi } from '../../api/auth'

const list = ref<any[]>([])
const years = ref<any[]>([])
const yearId = ref<number | undefined>()

const ext = (n: string) => (n.split('.').pop() || '').toUpperCase().slice(0, 4)

async function load() {
  list.value = await api.get('/homework/showcase', yearId.value ? { yearId: yearId.value } : undefined)
}

function download(w: any) {
  api.download(`/homework/submissions/${w.id}/file`, w.fileName)
}

onMounted(async () => {
  years.value = await publicApi.years()
  await load()
})
</script>

<style scoped>
.ops { margin-bottom: 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.card { display: flex; border: 1px solid #ebeef5; border-radius: 10px; overflow: hidden; background: #fff; }
.ext { width: 64px; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg, #f6d365, #fda085); color: #fff; font-weight: 700; }
.body { padding: 12px; flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.title { font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { font-size: 12px; color: #909399; }
.desc { font-size: 13px; color: #606266; }
.comment { font-size: 12px; color: #e6a23c; }
</style>
