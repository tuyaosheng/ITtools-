<template>
  <div>
    <el-divider content-position="left">课件包</el-divider>
    <el-form-item label="上传课件包">
      <div class="col">
        <div class="row">
          <el-upload :show-file-list="false" accept=".zip" :http-request="upload">
            <el-button size="small" :loading="uploading">{{ info.url ? '重新上传' : '选择 zip 文件' }}</el-button>
          </el-upload>
          <span v-if="info.fileName" class="muted">当前：{{ info.fileName }}</span>
        </div>
        <span class="muted">Storyline 发布（HTML5）后的文件夹打包成 zip 上传；也可以上传自己制作的网站。系统自动识别 story.html / index.html 作为主页。</span>
      </div>
    </el-form-item>
    <el-form-item v-if="info.htmlFiles?.length" label="主页文件">
      <el-select :model-value="entry" filterable class="w300" @change="setEntry">
        <el-option v-for="f in info.htmlFiles" :key="f" :label="f" :value="f" />
      </el-select>
    </el-form-item>
    <el-form-item v-if="info.url" label="兼容模式">
      <el-switch :model-value="info.compat" @change="setCompat" />
      <span class="muted ml">默认课件在隔离沙箱中运行；个别课件（需要本地存储进度等）无法正常播放时再开启。开启后课件脚本可以以登录用户身份访问平台，请只对可信来源的课件开启。</span>
    </el-form-item>
    <el-form-item v-if="info.url" label="预览">
      <el-button size="small" @click="preview = !preview">{{ preview ? '收起预览' : '预览课件' }}</el-button>
    </el-form-item>
    <div v-if="preview && info.url" class="pv"><CoursewareFrame :key="info.url" :page="page" teacher /></div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import CoursewareFrame from './CoursewareFrame.vue'

const props = defineProps<{ page: any }>()
const info = ref<any>({})
const uploading = ref(false)
const preview = ref(false)
const entry = computed(() => (info.value.url ? decodeURIComponent(String(info.value.url).split('/').slice(3).join('/')) : ''))

async function load() { info.value = await api.get(`/teacher/courseware/pages/${props.page.id}`) }

async function upload(o: UploadRequestOptions) {
  uploading.value = true
  try {
    info.value = await api.upload(`/teacher/courseware/pages/${props.page.id}`, o.file)
    ElMessage.success('课件包已上传')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    uploading.value = false
  }
}

async function setCompat(v: boolean) {
  try {
    info.value = await api.put(`/teacher/courseware/pages/${props.page.id}/compat`, { compat: v })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function setEntry(v: string) {
  try {
    info.value = await api.put(`/teacher/courseware/pages/${props.page.id}/entry`, { entry: v })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
watch(() => props.page.id, load)
</script>

<style scoped>
.col { display: flex; flex-direction: column; gap: 4px; }
.row { display: flex; align-items: center; gap: 8px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; line-height: 1.6; }
.w300 { width: 300px; }
.ml { margin-left: 8px; }
.pv { margin: 0 0 12px 90px; }
</style>
