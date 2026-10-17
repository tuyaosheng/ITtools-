<template>
  <div class="rich-editor">
    <div class="toolbar">
      <button type="button" title="加粗" @mousedown.prevent="cmd('bold')"><b>B</b></button>
      <button type="button" title="斜体" @mousedown.prevent="cmd('italic')"><i>I</i></button>
      <button type="button" title="下划线" @mousedown.prevent="cmd('underline')"><u>U</u></button>
      <button type="button" title="标题" @mousedown.prevent="cmd('formatBlock', 'H3')">H</button>
      <button type="button" title="正文" @mousedown.prevent="cmd('formatBlock', 'P')">¶</button>
      <button type="button" title="无序列表" @mousedown.prevent="cmd('insertUnorderedList')">• 列表</button>
      <button type="button" title="有序列表" @mousedown.prevent="cmd('insertOrderedList')">1. 列表</button>
      <button type="button" title="插入链接" @mousedown.prevent="insertLink">链接</button>
      <button type="button" title="插入图片（网址）" @mousedown.prevent="insertImage">图片</button>
      <button type="button" title="清除格式" @mousedown.prevent="cmd('removeFormat')">清除</button>
      <span class="spacer" />
      <button type="button" :class="{ on: source }" @mousedown.prevent="toggleSource">源码</button>
    </div>
    <textarea v-if="source" v-model="html" class="source" @input="emit('update:modelValue', html)" />
    <div v-else ref="area" class="area" contenteditable="true" @input="onInput" />
  </div>
</template>

<script setup lang="ts">
import { nextTick, onMounted, ref, watch } from 'vue'
import { safeHtml } from '../utils/format'

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: string): void }>()
const area = ref<HTMLDivElement>()
const source = ref(false)
const html = ref(props.modelValue || '')

function render() {
  if (area.value && area.value.innerHTML !== html.value) area.value.innerHTML = safeHtml(html.value)
}

watch(() => props.modelValue, v => {
  if ((v || '') !== html.value) {
    html.value = v || ''
    render()
  }
})

onMounted(render)

function onInput() {
  html.value = area.value?.innerHTML || ''
  emit('update:modelValue', html.value)
}

function cmd(name: string, value?: string) {
  area.value?.focus()
  document.execCommand(name, false, value)
  onInput()
}

function insertLink() {
  const url = window.prompt('链接地址', 'https://')
  if (url) cmd('createLink', url)
}

function insertImage() {
  const url = window.prompt('图片地址')
  if (url) cmd('insertImage', url)
}

async function toggleSource() {
  source.value = !source.value
  if (!source.value) {
    await nextTick()
    render()
  }
}
</script>

<style scoped>
.rich-editor { border: 1px solid var(--el-border-color); border-radius: 6px; width: 100%; }
.toolbar { display: flex; flex-wrap: wrap; gap: 4px; padding: 6px; border-bottom: 1px solid var(--el-border-color-lighter); background: var(--el-fill-color-lighter); border-radius: 6px 6px 0 0; }
.toolbar button { border: 1px solid transparent; background: none; padding: 2px 8px; border-radius: 4px; cursor: pointer; font-size: 13px; color: var(--el-text-color-regular); }
.toolbar button:hover, .toolbar button.on { border-color: var(--el-border-color); background: var(--el-bg-color); }
.spacer { flex: 1; }
.area { min-height: 180px; padding: 10px 12px; outline: none; line-height: 1.8; }
.area :deep(img) { max-width: 100%; }
.source { width: 100%; min-height: 180px; border: none; padding: 10px; font-family: monospace; box-sizing: border-box; outline: none; }
</style>
