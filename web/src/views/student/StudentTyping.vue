<template>
  <el-card shadow="never">
    <template #header>指法练习</template>
    <el-tabs v-model="tab">
      <el-tab-pane label="开始练习" name="practice">
        <div class="bar">
          <el-radio-group v-model="level" :disabled="running">
            <el-radio-button v-for="l in typingLevels" :key="l.value" :value="l.value">{{ l.label }}</el-radio-button>
          </el-radio-group>
          <el-select v-model="duration" :disabled="running" class="w120">
            <el-option :value="60" label="1 分钟" /><el-option :value="120" label="2 分钟" /><el-option :value="180" label="3 分钟" />
          </el-select>
          <el-button @click="reset">重新开始</el-button>
        </div>

        <div class="stats">
          <div class="stat"><b>{{ remaining }}</b><span>剩余秒数</span></div>
          <div class="stat"><b>{{ speed }}</b><span>速度（字符/分）</span></div>
          <div class="stat"><b>{{ accuracy }}%</b><span>正确率</span></div>
          <div class="stat"><b>{{ doneWords }}</b><span>完成单词</span></div>
        </div>

        <div class="words" :class="{ finished }">
          <span v-for="(w, i) in visible" :key="offset + i" class="w"
            :class="{ cur: offset + i === index, ok: results[offset + i] === true, bad: results[offset + i] === false }">{{ w }}</span>
        </div>
        <el-input ref="inputRef" v-model="typed" size="large" class="typing-input" :disabled="finished"
          :placeholder="finished ? '时间到！' : '在这里输入上面高亮的单词，空格或回车确认（开始输入即计时）'"
          :class="{ wrong: typed && !current.startsWith(typed.trim()) }" @keydown="onKey" />

        <el-alert v-if="result" type="success" :closable="false" class="mt"
          :title="`本次成绩：${result.speed} 字符/分，正确率 ${result.accuracy}%`"
          :description="`该级别最好成绩 ${result.best ?? '—'}${result.rank ? `，全校第 ${result.rank} 名` : '（正确率达到 90% 才能上榜）'}`" />
        <p class="tip">提示：手指放在基本键位 A S D F 与 J K L ; 上，两个食指找到 F 和 J 键上的小凸起，眼睛看屏幕，不看键盘。</p>
      </el-tab-pane>

      <el-tab-pane label="全校指法英雄榜" name="rank" lazy>
        <TypingLeaderboard ref="board" :initial-level="level" />
      </el-tab-pane>

      <el-tab-pane label="我的记录" name="mine" lazy>
        <el-table :data="mine" size="small" stripe>
          <el-table-column label="级别" width="100"><template #default="{ row }: { row: any }">{{ levelName(row.level) }}</template></el-table-column>
          <el-table-column prop="speed" label="速度" width="90" />
          <el-table-column label="正确率" width="90"><template #default="{ row }: { row: any }">{{ row.accuracy }}%</template></el-table-column>
          <el-table-column prop="words" label="单词" width="80" />
          <el-table-column label="时长" width="80"><template #default="{ row }: { row: any }">{{ row.durationSec }} 秒</template></el-table-column>
          <el-table-column label="时间"><template #default="{ row }: { row: any }">{{ fmtTime(row.createdAt) }}</template></el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { fmtTime } from '../../utils/format'
import { typingLevels, typingWords } from './typingWords'
import TypingLeaderboard from './TypingLeaderboard.vue'

const tab = ref('practice')
const level = ref('PRIMARY')
const duration = ref(60)
const words = ref<string[]>([])
const index = ref(0)
const typed = ref('')
const results = ref<Record<number, boolean>>({})
const correctChars = ref(0)
const typedChars = ref(0)
const startAt = ref(0)
const now = ref(0)
const running = ref(false)
const finished = ref(false)
const result = ref<any>(null)
const mine = ref<any[]>([])
const inputRef = ref()
const board = ref()
let clock: number | undefined

const levelName = (v: string) => typingLevels.find(l => l.value === v)?.label || v
const current = computed(() => words.value[index.value] || '')
const offset = computed(() => Math.max(0, index.value - 3))
const visible = computed(() => words.value.slice(offset.value, offset.value + 14))
const elapsed = computed(() => (running.value || finished.value ? Math.min(duration.value, (now.value - startAt.value) / 1000) : 0))
const remaining = computed(() => Math.max(0, Math.ceil(duration.value - elapsed.value)))
const speed = computed(() => (elapsed.value >= 1 ? Math.round(correctChars.value * 60 / elapsed.value) : 0))
const accuracy = computed(() => (typedChars.value ? Math.round(correctChars.value * 1000 / typedChars.value) / 10 : 100))
const doneWords = computed(() => Object.keys(results.value).length)

function shuffle(list: string[]) {
  const out: string[] = []
  while (out.length < 400) {
    const copy = [...list]
    for (let i = copy.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1))
      ;[copy[i], copy[j]] = [copy[j], copy[i]]
    }
    out.push(...copy)
  }
  return out
}

function reset() {
  window.clearInterval(clock)
  words.value = shuffle(typingWords[level.value])
  index.value = 0
  typed.value = ''
  results.value = {}
  correctChars.value = 0
  typedChars.value = 0
  running.value = false
  finished.value = false
  result.value = null
  nextTick(() => inputRef.value?.focus())
}

function start() {
  running.value = true
  startAt.value = Date.now()
  now.value = startAt.value
  clock = window.setInterval(() => {
    now.value = Date.now()
    if (elapsed.value >= duration.value) finish()
  }, 200)
}

/** 逐字符比较：同位置相同的字符算正确，多打/漏打算错误；单词后的空格算一个字符 */
function commit() {
  const t = typed.value.trim()
  if (!t) return
  const w = current.value
  let ok = 0
  for (let i = 0; i < Math.min(t.length, w.length); i++) if (t[i] === w[i]) ok++
  correctChars.value += ok + (t === w ? 1 : 0)
  typedChars.value += Math.max(t.length, w.length) + 1
  results.value[index.value] = t === w
  index.value++
  typed.value = ''
}

function onKey(e: KeyboardEvent) {
  if (finished.value) return
  if (!running.value && e.key.length === 1) start()
  if (e.key === ' ' || e.key === 'Enter') {
    e.preventDefault()
    commit()
  }
}

async function finish() {
  window.clearInterval(clock)
  running.value = false
  finished.value = true
  if (typed.value.trim()) commit()
  if (!typedChars.value) return
  try {
    result.value = await api.post('/student/typing', {
      level: level.value, durationSec: duration.value, typedChars: typedChars.value, correctChars: correctChars.value, words: doneWords.value
    })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

watch(level, reset)
watch(tab, async v => {
  if (v === 'mine') mine.value = await api.get('/student/typing')
  if (v === 'rank' && board.value) { board.value.level = level.value; board.value.load() }
})
reset()
onBeforeUnmount(() => window.clearInterval(clock))
</script>

<style scoped>
.bar { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.w120 { width: 120px; }
.stats { display: grid; grid-template-columns: repeat(4, minmax(100px, 1fr)); gap: 10px; margin-bottom: 14px; max-width: 640px; }
.stat { background: var(--el-fill-color-light); border-radius: 8px; padding: 10px; display: flex; flex-direction: column; align-items: center; }
.stat b { font-size: 24px; }
.stat span { font-size: 12px; color: var(--el-text-color-secondary); }
.words { font-family: Consolas, 'Courier New', monospace; font-size: 26px; line-height: 1.8; background: var(--el-fill-color-lighter); border-radius: 8px; padding: 14px 18px; margin-bottom: 12px; min-height: 110px; }
.words.finished { opacity: .5; }
.w { margin-right: 18px; color: var(--el-text-color-regular); }
.w.cur { color: var(--el-color-primary); text-decoration: underline; font-weight: 700; }
.w.ok { color: var(--el-color-success); }
.w.bad { color: var(--el-color-danger); text-decoration: line-through; }
.typing-input :deep(input) { font-family: Consolas, 'Courier New', monospace; font-size: 22px; }
.typing-input.wrong :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px var(--el-color-danger) inset; }
.mt { margin-top: 12px; }
.tip { color: var(--el-text-color-secondary); font-size: 12px; margin-top: 12px; }
@media (max-width: 640px) { .stats { grid-template-columns: repeat(2, 1fr); } }
</style>
