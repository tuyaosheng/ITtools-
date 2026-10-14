<template>
  <div class="q" :class="stateClass">
    <div class="q-head">
      <span class="no">{{ index + 1 }}.</span>
      <el-tag size="small" effect="plain">{{ typeLabel[q.qtype] }}</el-tag>
      <span class="score">（{{ q.score }} 分）</span>
      <span v-if="q.gotScore != null" class="got">得 {{ q.gotScore }} 分</span>
    </div>
    <div v-if="q.stem" class="stem" v-html="safeHtml(q.stem)" />
    <el-image v-if="q.hasImage" :src="`/api/quiz/questions/${q.id}/image`" :preview-src-list="[`/api/quiz/questions/${q.id}/image`]"
      fit="contain" class="stem-img" :hide-on-click-modal="true" />

    <!-- 单选 -->
    <el-radio-group v-if="q.qtype === 'SINGLE'" :model-value="value" :disabled="readonly" class="opts" @update:model-value="emitVal">
      <el-radio v-for="(o, i) in q.options" :key="i" :value="letter(i)" class="opt">{{ letter(i) }}{{ o ? '. ' + o : '' }}</el-radio>
    </el-radio-group>
    <!-- 多选 -->
    <el-checkbox-group v-else-if="q.qtype === 'MULTI'" :model-value="multiValue" :disabled="readonly" class="opts"
      @update:model-value="(v: any) => emitVal([...v].sort().join(''))">
      <el-checkbox v-for="(o, i) in q.options" :key="i" :value="letter(i)" class="opt">{{ letter(i) }}{{ o ? '. ' + o : '' }}</el-checkbox>
    </el-checkbox-group>
    <!-- 判断 -->
    <el-radio-group v-else-if="q.qtype === 'JUDGE'" :model-value="value" :disabled="readonly" class="opts" @update:model-value="emitVal">
      <el-radio value="T">对</el-radio>
      <el-radio value="F">错</el-radio>
    </el-radio-group>
    <!-- 填空 -->
    <div v-else-if="q.qtype === 'FILL'" class="blanks">
      <el-input v-for="i in (q.blankCount || 1)" :key="i" :model-value="fillValue[i - 1]" :disabled="readonly"
        :placeholder="`第 ${i} 空`" class="blank" @update:model-value="(v: string) => setBlank(i - 1, v)" />
    </div>

    <div v-if="q.answer !== undefined && showKey" class="key">
      <b>标准答案：</b>{{ answerText(q) }}
      <div v-if="q.analysis" class="analysis"><b>解析：</b>{{ q.analysis }}</div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { safeHtml } from '../../utils/format'

const props = defineProps<{ q: any; index: number; modelValue?: any; readonly?: boolean; showKey?: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: any): void }>()
const typeLabel: Record<string, string> = { JUDGE: '判断题', SINGLE: '单选题', MULTI: '多选题', FILL: '填空题' }
const letter = (i: number) => String.fromCharCode(65 + i)
const value = computed(() => normalizeJudge(props.modelValue))
const multiValue = computed(() => (props.modelValue || '').split('').filter((x: string) => x))
const fillValue = computed<string[]>(() => (Array.isArray(props.modelValue) ? props.modelValue : []))
const stateClass = computed(() => (props.q.correct === true ? 'right' : props.q.correct === false ? 'wrong' : ''))

function normalizeJudge(v: any) {
  if (props.q.qtype !== 'JUDGE' || v == null) return v
  return ['T', '对', 'TRUE', '√'].includes(String(v).toUpperCase()) ? 'T' : ['F', '错', 'FALSE', '×'].includes(String(v).toUpperCase()) ? 'F' : v
}

function emitVal(v: any) {
  emit('update:modelValue', v)
}

function setBlank(i: number, v: string) {
  const arr = [...fillValue.value]
  while (arr.length < (props.q.blankCount || 1)) arr.push('')
  arr[i] = v
  emit('update:modelValue', arr)
}

function answerText(q: any) {
  if (q.qtype === 'JUDGE') return q.answer === 'T' ? '对' : '错'
  if (q.qtype === 'FILL') return (q.answer || []).map((a: string, i: number) => `第${i + 1}空：${a.replace(/\|/g, ' 或 ')}`).join('；')
  return q.answer
}
</script>

<style scoped>
.q { padding: 14px 16px; border: 1px solid var(--el-border-color-lighter); border-radius: 10px; margin-bottom: 12px; background: var(--el-bg-color); }
.q.right { border-color: var(--el-color-success-light-5); background: var(--el-color-success-light-9); }
.q.wrong { border-color: var(--el-color-danger-light-5); background: var(--el-color-danger-light-9); }
.q-head { display: flex; align-items: center; gap: 8px; margin-bottom: 8px; }
.no { font-weight: 700; }
.score { color: var(--el-text-color-secondary); font-size: 12px; }
.got { margin-left: auto; font-weight: 600; color: var(--el-color-primary); }
.stem { margin-bottom: 10px; line-height: 1.7; }
.stem-img { display: block; max-width: 100%; max-height: 420px; margin-bottom: 10px; border: 1px solid var(--el-border-color-lighter); border-radius: 6px; cursor: zoom-in; }
.opts { display: flex; flex-direction: column; align-items: flex-start; gap: 6px; }
.opt { height: auto; white-space: normal; }
.blanks { display: flex; gap: 8px; flex-wrap: wrap; }
.blank { width: 200px; }
.key { margin-top: 10px; padding: 8px 12px; background: var(--el-fill-color-light); border-radius: 6px; font-size: 13px; }
.analysis { margin-top: 4px; color: var(--el-text-color-regular); }
</style>
