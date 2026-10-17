<template>
  <el-card shadow="never">
    <template #header>个人网站</template>
    <el-tabs v-model="tab">
      <el-tab-pane label="我的网站" name="mine">
        <template v-if="data">
          <div v-if="data.site" class="site">
            <div class="site-head">
              <el-input v-model="title" maxlength="100" class="w260" />
              <el-button @click="rename">修改网站名称</el-button>
              <el-button type="primary" tag="a" :href="data.site.url" target="_blank">在新窗口打开</el-button>
              <el-popconfirm title="删除我的网站？" @confirm="remove">
                <template #reference><el-button type="danger" plain>删除</el-button></template>
              </el-popconfirm>
            </div>
            <div class="muted">{{ data.site.fileCount }} 个文件 · {{ fmtSize(data.site.sizeBytes) }} · 更新于 {{ fmtTime(data.site.updatedAt) }}</div>
            <iframe class="preview" :src="data.site.url" />
          </div>
          <el-empty v-else description="还没有个人网站" :image-size="80" />
          <el-divider content-position="left">{{ data.site ? '上传新版本（会替换现在的网站）' : '上传网站' }}</el-divider>
          <div class="upload">
            <el-input v-if="!data.site" v-model="title" placeholder="网站名称，如：我的家乡" maxlength="100" class="w260" />
            <el-upload :show-file-list="false" accept=".zip" :http-request="upload">
              <el-button type="primary" :loading="uploading">选择网站压缩包（zip）</el-button>
            </el-upload>
          </div>
          <div><Hint block>把网站整个文件夹压缩成 zip 上传（不超过 {{ data.maxMb }}MB），首页文件名要用 index.html，系统会自动解压。</Hint></div>
        </template>
      </el-tab-pane>
      <el-tab-pane label="本班同学的网站" name="class" lazy>
        <ClassSites />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtSize, fmtTime } from '../../utils/format'
import ClassSites from './ClassSites.vue'

const tab = ref('mine')
const data = ref<any>(null)
const title = ref('')
const uploading = ref(false)

function fill(d: any) {
  data.value = d
  title.value = d.site?.title || ''
}

async function upload(o: UploadRequestOptions) {
  uploading.value = true
  try {
    fill(await api.upload('/student/site', o.file, { title: title.value || '我的网站' }))
    ElMessage.success('网站已发布')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    uploading.value = false
  }
}

async function rename() {
  try {
    fill(await api.put('/student/site', { title: title.value }))
    ElMessage.success('已修改')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove() {
  try {
    await api.del('/student/site')
    fill(await api.get('/student/site'))
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => fill(await api.get('/student/site')))
</script>

<style scoped>
.site-head, .upload { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 6px; }
.w260 { width: 260px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.preview { width: 100%; height: 420px; border: 1px solid var(--el-border-color); border-radius: 6px; margin-top: 8px; background: var(--el-bg-color); }
</style>
