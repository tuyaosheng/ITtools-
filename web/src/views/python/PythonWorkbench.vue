<template>
  <div class="workbench">
    <div class="left">
      <h3 class="title">{{ view.title }}</h3>
      <div class="desc" v-html="safeHtml(view.description)" />
      <div class="cases">
        <div class="sub-title">测试点（共 {{ view.totalScore }} 分，单点限时 {{ view.timeLimitMs }} 毫秒）</div>
        <div v-for="c in view.cases" :key="c.seq" class="case">
          <div class="case-head">
            <span>#{{ c.seq }} {{ c.name || '' }}</span>
            <span class="muted">{{ c.score }} 分</span>
          </div>
          <template v-if="!c.hidden">
            <div class="io"><span class="muted">输入</span><pre>{{ c.input || '（无）' }}</pre></div>
            <div class="io"><span class="muted">期望输出</span><pre>{{ c.expected }}</pre></div>
          </template>
          <div v-else class="muted">隐藏测试点：提交后只显示是否通过</div>
        </div>
      </div>
      <el-collapse v-if="view.referenceCode" class="ref">
        <el-collapse-item title="参考代码（老师已开放）" name="ref">
          <PyEditor :model-value="view.referenceCode" readonly height="200px" />
        </el-collapse-item>
      </el-collapse>
    </div>

    <div class="right">
      <div class="toolbar">
        <el-button type="primary" plain :loading="running" @click="runOnce">▶ 运行</el-button>
        <el-button :loading="running" :disabled="!visibleCases.length" @click="selfTest">用样例自测</el-button>
        <el-button :loading="saving" @click="save()">保存</el-button>
        <el-popconfirm title="还原为老师提供的初始代码？当前代码将被覆盖" @confirm="restore">
          <template #reference><el-button>还原</el-button></template>
        </el-popconfirm>
        <span class="spacer" />
        <span v-if="savedHint" class="muted">{{ savedHint }}</span>
        <el-button type="success" :loading="submitting" @click="submit">提交评分</el-button>
      </div>
      <PyEditor v-model="code" height="360px" />
      <div class="stdin">
        <span class="muted">标准输入（每行对应一次 input()；注意 input('提示') 里的提示文字也算作输出，评分时可能导致不一致）</span>
        <el-input v-model="stdin" type="textarea" :rows="2" placeholder="运行时提供给程序的输入" />
      </div>
      <div class="console">
        <div class="console-head">
          <span>运行结果 <Hint>浏览器内运行，仅供调试，以提交评分为准</Hint></span>
          <el-button link size="small" @click="output = []">清空</el-button>
        </div>
        <pre class="console-body"><span v-for="(o, i) in output" :key="i" :class="o.kind">{{ o.text }}</span><span v-if="!output.length" class="muted">点击「运行」查看输出</span></pre>
      </div>

      <div v-if="selfResults.length" class="result">
        <div class="sub-title">样例自测（浏览器）</div>
        <el-tag v-for="r in selfResults" :key="r.seq" :type="r.passed ? 'success' : 'danger'" class="mr4">#{{ r.seq }} {{ r.passed ? '通过' : r.status === 'TLE' ? '超时' : '不一致' }}</el-tag>
      </div>

      <div v-if="sub && sub.gradedAt" class="result">
        <div class="score-line">
          <span class="sub-title">评分结果</span>
          <b class="score">{{ num(sub.score) }} / {{ num(sub.maxScore) }}</b>
          <span class="muted">最高 {{ num(sub.bestScore) }} 分 · 已提交 {{ sub.submitCount }} 次 · {{ fmtTime(sub.gradedAt) }}</span>
          <el-tag v-if="sub.stale" type="warning" size="small">题目已更新，请重新提交</el-tag>
        </div>
        <el-table :data="sub.detail" size="small" border>
          <el-table-column type="expand">
            <template #default="{ row }">
              <div v-if="row.hidden" class="muted pad">隐藏测试点不显示输出</div>
              <div v-else class="pad">
                <div class="muted">你的输出</div><pre>{{ row.stdout || '（无输出）' }}</pre>
                <template v-if="row.stderr"><div class="muted">错误信息</div><pre class="err">{{ row.stderr }}</pre></template>
              </div>
            </template>
          </el-table-column>
          <el-table-column label="测试点" width="120">
            <template #default="{ row }">#{{ row.seq }} {{ row.name || '' }}{{ row.hidden ? '（隐藏）' : '' }}</template>
          </el-table-column>
          <el-table-column label="结果" width="110">
            <template #default="{ row }"><el-tag :type="statusTag[row.status]" size="small">{{ statusLabel[row.status] || row.status }}</el-tag></template>
          </el-table-column>
          <el-table-column label="得分" width="90">
            <template #default="{ row }">{{ row.score }} / {{ row.maxScore }}</template>
          </el-table-column>
          <el-table-column label="用时" width="90">
            <template #default="{ row }">{{ row.ms }} ms</template>
          </el-table-column>
        </el-table>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import PyEditor from '../../components/PyEditor.vue'
import { api, errMsg } from '../../api/client'
import { fmtTime, safeHtml } from '../../utils/format'
import { normalizeOutput, run, warmUp } from '../../utils/pyrunner'

/** basePath：保存 PUT {basePath}/code，提交 POST {basePath}/submit */
const props = defineProps<{ view: any; basePath: string }>()
const emit = defineEmits<{ (e: 'update', v: any): void }>()

const statusLabel: Record<string, string> = { AC: '通过', WA: '答案错误', RE: '运行出错', TLE: '超时' }
const statusTag: Record<string, string> = { AC: 'success', WA: 'danger', RE: 'warning', TLE: 'warning' }

const code = ref<string>(props.view.submission?.code ?? props.view.starterCode ?? '')
const sub = computed(() => props.view.submission)
const visibleCases = computed(() => (props.view.cases || []).filter((c: any) => !c.hidden))
const stdin = ref<string>(visibleCases.value[0]?.input || '')
const output = ref<{ text: string; kind: string }[]>([])
const selfResults = ref<any[]>([])
const running = ref(false)
const saving = ref(false)
const submitting = ref(false)
const savedHint = ref('')
let lastSaved = code.value
let timer: number | undefined

function num(v: any) {
  return v == null ? '-' : Number(v)
}

async function runOnce() {
  running.value = true
  output.value = []
  try {
    const r = await run(code.value, stdin.value, (text, kind) => output.value.push({ text, kind }))
    if (r.status === 'TLE') output.value.push({ text: '\n运行超过 10 秒已强制停止（可能有死循环）\n', kind: 'err' })
  } finally {
    running.value = false
  }
}

async function selfTest() {
  running.value = true
  selfResults.value = []
  try {
    for (const c of visibleCases.value) {
      let out = ''
      const r = await run(code.value, c.input, (text, kind) => { if (kind === 'out') out += text }, Math.max(3000, props.view.timeLimitMs * 3))
      selfResults.value.push({ seq: c.seq, status: r.status, passed: r.status === 'OK' && normalizeOutput(out) === normalizeOutput(c.expected) })
    }
  } finally {
    running.value = false
  }
}

async function save(silent = false) {
  if (silent && code.value === lastSaved) return
  saving.value = !silent
  try {
    const v = await api.put(`${props.basePath}/code`, { code: code.value })
    lastSaved = code.value
    savedHint.value = '已保存 ' + fmtTime(new Date().toISOString()).slice(11)
    emit('update', v)
  } catch (err) {
    if (!silent) ElMessage.error(errMsg(err, '保存失败'))
  } finally {
    saving.value = false
  }
}

function restore() {
  code.value = props.view.starterCode || ''
}

async function submit() {
  submitting.value = true
  try {
    const v = await api.post(`${props.basePath}/submit`, { code: code.value })
    lastSaved = code.value
    emit('update', v)
    const s = v.submission
    ElMessage({ type: Number(s.score) === Number(s.maxScore) ? 'success' : 'info', message: `得分 ${Number(s.score)} / ${Number(s.maxScore)}` })
  } catch (err) {
    ElMessage.error(errMsg(err, '提交失败'))
  } finally {
    submitting.value = false
  }
}

watch(() => props.view.id, () => {
  code.value = props.view.submission?.code ?? props.view.starterCode ?? ''
  lastSaved = code.value
})

onMounted(() => {
  warmUp()
  // 每 30 秒自动保存一次未保存的修改
  timer = window.setInterval(() => save(true), 30000)
})
onBeforeUnmount(() => {
  window.clearInterval(timer)
  save(true)
})
</script>

<style scoped>
.workbench { display: grid; grid-template-columns: minmax(280px, 2fr) 3fr; gap: 16px; }
@media (max-width: 960px) { .workbench { grid-template-columns: 1fr; } }
.title { margin: 0 0 8px; }
.desc { font-size: 14px; line-height: 1.7; }
.sub-title { font-weight: 600; font-size: 13px; margin: 8px 0; }
.case { border: 1px solid var(--el-border-color-lighter); border-radius: 4px; padding: 8px; margin-bottom: 8px; font-size: 13px; }
.case-head { display: flex; justify-content: space-between; margin-bottom: 4px; }
.io pre, .pad pre { margin: 2px 0 6px; padding: 6px 8px; background: var(--el-fill-color-light); border-radius: 4px; white-space: pre-wrap; font-family: Consolas, monospace; }
.ref { margin-top: 8px; }
.toolbar { display: flex; gap: 6px; align-items: center; margin-bottom: 8px; flex-wrap: wrap; }
.toolbar .el-button + .el-button { margin-left: 0; }
.spacer { flex: 1; }
.stdin { margin-top: 8px; }
.console { margin-top: 8px; border: 1px solid var(--el-border-color); border-radius: 4px; }
.console-head { display: flex; justify-content: space-between; padding: 4px 8px; background: var(--el-fill-color-light); font-size: 13px; }
.console-body { margin: 0; padding: 8px; min-height: 80px; max-height: 240px; overflow: auto; background: #1e1e1e; color: #d4d4d4; font-family: Consolas, monospace; font-size: 13px; white-space: pre-wrap; }
.console-body .err { color: #f48771; }
.result { margin-top: 12px; }
.score-line { display: flex; align-items: center; gap: 10px; margin-bottom: 6px; flex-wrap: wrap; }
.score { font-size: 18px; color: var(--el-color-primary); }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.pad { padding: 4px 12px; }
.err { color: var(--el-color-danger); }
.mr4 { margin-right: 4px; }
</style>
