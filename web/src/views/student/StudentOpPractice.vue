<template>
  <el-card shadow="never">
    <template #header>课后操作题</template>
    <p class="muted">老师开放的 Word / Excel / PPT 等操作题：下载素材，完成后上传即可自动评分，可以反复提交，保留最好成绩。</p>
    <el-table :data="list" stripe empty-text="暂时没有开放的课后操作题">
      <el-table-column label="题目" min-width="200">
        <template #default="{ row }: { row: any }"><el-link type="primary" @click="open(row)">{{ row.title }}</el-link></template>
      </el-table-column>
      <el-table-column prop="courseName" label="课程" min-width="140" />
      <el-table-column prop="teacherName" label="教师" width="90" />
      <el-table-column label="练习次数" width="90"><template #default="{ row }: { row: any }">{{ row.attempts || 0 }}</template></el-table-column>
      <el-table-column label="最好成绩" width="110">
        <template #default="{ row }: { row: any }">
          <span v-if="row.bestScore != null">{{ row.bestScore }}<span class="muted"> / {{ row.maxScore }}</span></span>
          <span v-else-if="row.attempts" class="muted">已提交</span>
          <span v-else class="muted">未做</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="90"><template #default="{ row }: { row: any }"><el-button link type="primary" @click="open(row)">去做题</el-button></template></el-table-column>
    </el-table>

    <el-drawer v-model="drawer" :title="cur?.title" size="60%" destroy-on-close>
      <template v-if="cur">
        <div class="muted">《{{ cur.courseName }}》</div>
        <div class="content" v-html="cur.content || '<p>按素材要求完成操作。</p>'" />
        <div class="row">
          <el-button v-if="cur.materialName" @click="downloadMaterial">下载素材：{{ cur.materialName }}</el-button>
          <el-upload :show-file-list="false" :http-request="submit" :accept="accept">
            <el-button type="primary" :loading="sending">上传我的作品</el-button>
          </el-upload>
          <span v-if="cur.allowedExts" class="muted">允许：{{ cur.allowedExts }}</span>
        </div>
        <template v-if="cur.mine">
          <el-divider content-position="left">我的成绩</el-divider>
          <div class="scores">
            <div v-if="cur.mine.autoScore != null" class="box"><span>本次</span><b>{{ cur.mine.autoScore }}<small>/{{ cur.mine.autoMax }}</small></b></div>
            <div v-if="cur.mine.bestScore != null" class="box"><span>最好</span><b>{{ cur.mine.bestScore }}</b></div>
            <div class="box"><span>次数</span><b>{{ cur.mine.attempts }}</b></div>
            <div class="box"><span>最近提交</span><b class="small">{{ fmtTime(cur.mine.submittedAt) }}</b></div>
          </div>
          <p v-if="cur.autoVisibility === 'NONE'" class="muted">老师设置为不显示成绩。</p>
          <AutoDetail v-if="cur.mine.autoDetail" :detail="cur.mine.autoDetail" class="mt" />
        </template>
      </template>
    </el-drawer>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import AutoDetail from '../homework/AutoDetail.vue'

const list = ref<any[]>([])
const drawer = ref(false)
const cur = ref<any>(null)
const sending = ref(false)
const accept = computed(() => (cur.value?.allowedExts ? cur.value.allowedExts.split(',').map((e: string) => '.' + e).join(',') : undefined))

async function load() { list.value = await api.get('/student/op-practice') }

async function open(row: any) {
  try {
    cur.value = await api.get(`/student/op-practice/${row.pageId}`)
    drawer.value = true
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

function downloadMaterial() { api.download(`/classroom/pages/${cur.value.id}/file`, cur.value.materialName) }

async function submit(o: UploadRequestOptions) {
  sending.value = true
  try {
    cur.value = await api.upload(`/student/op-practice/${cur.value.id}`, o.file)
    ElMessage.success('已提交并完成评分')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    sending.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.content { margin: 12px 0; line-height: 1.7; }
.row { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
.scores { display: flex; gap: 10px; flex-wrap: wrap; }
.box { background: var(--el-fill-color-light); border-radius: 6px; padding: 8px 14px; display: flex; flex-direction: column; min-width: 90px; }
.box span { font-size: 12px; color: var(--el-text-color-secondary); }
.box b { font-size: 20px; }
.box b.small { font-size: 13px; font-weight: normal; }
.box small { font-size: 12px; color: var(--el-text-color-secondary); }
.mt { margin-top: 10px; }
</style>
