<template>
  <el-dialog :model-value="modelValue" fullscreen append-to-body class="carousel-dlg" @update:model-value="(v: boolean) => emit('update:modelValue', v)" @open="start" @closed="stop">
    <template #header>
      <div class="bar">
        <b>作品展播</b>
        <span class="muted">{{ idx + 1 }} / {{ works.length }}</span>
        <el-checkbox v-model="onlyEssence" @change="restart">只播精华</el-checkbox>
        <span class="muted">切换时间</span>
        <el-select v-model="seconds" size="small" class="w90" @change="restart">
          <el-option v-for="s in [5, 10, 20, 30]" :key="s" :label="`${s} 秒`" :value="s" />
        </el-select>
        <el-button size="small" @click="paused = !paused">{{ paused ? '继续' : '暂停' }}</el-button>
        <el-button size="small" @click="go(-1)">上一个</el-button>
        <el-button size="small" @click="go(1)">下一个</el-button>
      </div>
    </template>
    <el-empty v-if="!works.length" description="还没有可展播的作品" />
    <div v-else-if="cur" class="stage">
      <img v-if="isImage(cur.fileName)" :src="fileUrl(`/homework/submissions/${cur.id}/file?inline=true`)" class="media" />
      <video v-else-if="isVideo(cur.fileName)" :src="fileUrl(`/homework/submissions/${cur.id}/file?inline=true`)" class="media" controls autoplay muted />
      <iframe v-else-if="isPage(cur.fileName)" :src="fileUrl(`/homework/submissions/${cur.id}/file?inline=true`)" class="media frame" sandbox="" />
      <div v-else class="file">📄 {{ cur.fileName }}<div class="muted">该类型文件不能在网页中直接展示</div></div>
      <div class="caption">
        <b>{{ cur.studentName }}</b><span v-if="cur.title">《{{ cur.title }}》</span>
        <el-tag v-if="cur.essence" type="warning" size="small">精华</el-tag>
        <span v-if="cur.description" class="desc">{{ cur.description }}</span>
      </div>
    </div>
  </el-dialog>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref } from 'vue'
import { fileUrl } from '../../api/client'

const props = defineProps<{ modelValue: boolean; students: any[] }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const idx = ref(0)
const seconds = ref(10)
const paused = ref(false)
const onlyEssence = ref(false)
let timer: number | undefined

const works = computed(() => props.students.filter(s => s.id && (!onlyEssence.value || s.essence)))
const cur = computed(() => works.value[idx.value % Math.max(1, works.value.length)])
const isImage = (n: string) => /\.(png|jpe?g|gif|bmp|webp|svg)$/i.test(n || '')
const isVideo = (n: string) => /\.(mp4|webm)$/i.test(n || '')
const isPage = (n: string) => /\.(html?|pdf|txt)$/i.test(n || '')

function go(d: number) {
  const n = works.value.length
  if (n) idx.value = (idx.value + d + n) % n
}
function start() {
  idx.value = 0
  stop()
  timer = window.setInterval(() => { if (!paused.value) go(1) }, seconds.value * 1000)
}
function stop() { window.clearInterval(timer) }
function restart() { start() }
onBeforeUnmount(stop)
</script>

<style scoped>
.bar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
.muted { color: #909399; font-size: 12px; }
.w90 { width: 90px; }
.stage { display: flex; flex-direction: column; align-items: center; height: calc(100vh - 140px); }
.media { flex: 1; max-width: 100%; max-height: calc(100vh - 200px); object-fit: contain; border: 0; }
.frame { width: 100%; background: #fff; }
.file { margin: auto; font-size: 22px; text-align: center; }
.caption { margin-top: 10px; display: flex; align-items: center; gap: 8px; font-size: 16px; }
.desc { color: #606266; font-size: 14px; }
</style>
