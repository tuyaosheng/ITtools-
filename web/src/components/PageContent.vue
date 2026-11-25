<template>
  <div class="page-content">
    <div v-if="page.content && page.pageType !== 'LINK'" class="rich" v-html="safeHtml(page.content)" />

    <div v-if="page.pageType === 'FILE' || (page.filePath && page.pageType !== 'VIDEO' && page.pageType !== 'SCRATCH')" class="file-box">
      <template v-if="page.filePath">
        <el-icon class="file-icon"><Document /></el-icon>
        <span class="file-name">{{ page.fileName }}</span>
        <el-button v-if="previewable" size="small" @click="preview = !preview">{{ preview ? '收起' : '显示' }}</el-button>
        <el-button size="small" type="primary" @click="download">下载</el-button>
      </template>
      <el-empty v-else description="暂无附件" :image-size="50" />
    </div>
    <FilePreview
      v-if="preview && previewable && page.filePath"
      :file-name="page.fileName"
      :file-url="fileHref(true)"
      :preview-api="`/classroom/pages/${page.id}/preview`"
    />

    <div v-if="page.pageType === 'VIDEO'" class="video-box">
      <video v-if="page.filePath" controls class="video" :src="fileHref(true)" />
      <video v-else-if="page.linkUrl" controls class="video" :src="page.linkUrl" />
      <el-empty v-else description="暂无视频" :image-size="50" />
    </div>

    <div v-if="page.pageType === 'LINK'" class="link-box">
      <div v-if="page.content" class="rich" v-html="safeHtml(page.content)" />
      <el-link v-if="page.linkUrl" type="primary" :href="page.linkUrl" target="_blank">在新窗口打开：{{ page.linkUrl }}</el-link>
      <iframe v-if="page.linkUrl" class="preview-frame" :src="page.linkUrl" />
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Document } from '@element-plus/icons-vue'
import { api } from '../api/client'
import { safeHtml } from '../utils/format'
import FilePreview from './FilePreview.vue'

const PREVIEW_EXTS = ['ppt', 'pptx', 'pps', 'ppsx', 'doc', 'docx', 'xlsx', 'xls', 'csv', 'et', 'pdf', 'txt', 'html', 'htm',
  'png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg', 'mp3', 'wav', 'ogg', 'm4a']

const props = defineProps<{ page: any }>()
// 能在线显示的课件（PPT、Word、Excel、PDF、图片等）默认直接显示，同时保留下载
const preview = ref(true)

const ext = computed(() => (props.page.fileName || '').split('.').pop()?.toLowerCase() || '')
const previewable = computed(() => PREVIEW_EXTS.includes(ext.value))

function fileHref(inline: boolean) {
  return `/api/classroom/pages/${props.page.id}/file${inline ? '?inline=true' : ''}`
}

function download() {
  api.download(`/classroom/pages/${props.page.id}/file`, props.page.fileName)
}
</script>

<style scoped>
.rich { line-height: 1.8; color: var(--el-text-color-primary); }
.rich :deep(img) { max-width: 100%; }
.file-box { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin: 12px 0; background: var(--el-fill-color-lighter); }
.file-icon { font-size: 22px; color: var(--el-color-primary); }
.file-name { flex: 1; word-break: break-all; }
.link-box .preview-frame { width: 100%; height: 520px; border: 1px solid var(--el-border-color-lighter); border-radius: 8px; margin-top: 8px; background: var(--el-bg-color); }
.video { width: 100%; max-height: 520px; background: #000; border-radius: 8px; }
.link-box { display: flex; flex-direction: column; gap: 10px; }
</style>
