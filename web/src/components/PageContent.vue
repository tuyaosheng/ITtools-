<template>
  <div class="page-content">
    <div v-if="page.content && page.pageType !== 'LINK'" class="rich" v-html="safeHtml(page.content)" />

    <div v-if="page.pageType === 'FILE' || (page.filePath && page.pageType !== 'VIDEO' && page.pageType !== 'SCRATCH')" class="file-box">
      <template v-if="page.filePath">
        <el-icon class="file-icon"><Document /></el-icon>
        <span class="file-name">{{ page.fileName }}</span>
        <el-button size="small" type="primary" @click="download">下载</el-button>
        <el-button v-if="previewable" size="small" @click="preview = !preview">{{ preview ? '收起预览' : '在线预览' }}</el-button>
      </template>
      <el-empty v-else description="暂无附件" :image-size="50" />
    </div>
    <iframe v-if="preview && page.filePath" class="preview-frame" :src="fileHref(true)" />
    <img v-if="isImage && page.filePath" class="preview-img" :src="fileHref(true)" />

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

const props = defineProps<{ page: any }>()
const preview = ref(false)

const ext = computed(() => (props.page.fileName || '').split('.').pop()?.toLowerCase() || '')
const previewable = computed(() => ['pdf', 'txt', 'html', 'htm'].includes(ext.value))
const isImage = computed(() => ['png', 'jpg', 'jpeg', 'gif', 'bmp', 'webp', 'svg'].includes(ext.value))

function fileHref(inline: boolean) {
  return `/api/classroom/pages/${props.page.id}/file${inline ? '?inline=true' : ''}`
}

function download() {
  api.download(`/classroom/pages/${props.page.id}/file`, props.page.fileName)
}
</script>

<style scoped>
.rich { line-height: 1.8; color: #303133; }
.rich :deep(img) { max-width: 100%; }
.file-box { display: flex; align-items: center; gap: 10px; padding: 12px 14px; border: 1px solid #ebeef5; border-radius: 8px; margin: 12px 0; background: #fafafa; }
.file-icon { font-size: 22px; color: var(--el-color-primary); }
.file-name { flex: 1; word-break: break-all; }
.preview-frame { width: 100%; height: 520px; border: 1px solid #ebeef5; border-radius: 8px; margin-top: 8px; background: #fff; }
.preview-img { max-width: 100%; border-radius: 8px; margin-top: 8px; }
.video { width: 100%; max-height: 520px; background: #000; border-radius: 8px; }
.link-box { display: flex; flex-direction: column; gap: 10px; }
</style>
