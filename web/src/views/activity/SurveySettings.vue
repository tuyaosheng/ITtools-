<template>
  <div>
    <el-divider content-position="left">调查题目</el-divider>
    <el-alert v-if="hasAnswers" type="info" :closable="false" show-icon class="mb"
      title="已有学生作答，题目只能改文字" />
    <div v-for="(it, i) in items" :key="it.id || it.key" class="item">
      <div class="item-head">
        <span class="no">{{ i + 1 }}</span>
        <el-select v-model="it.qtype" size="small" class="w100" :disabled="locked(it)">
          <el-option v-for="(l, k) in typeLabel" :key="k" :label="l" :value="k" />
        </el-select>
        <el-input v-model="it.stem" size="small" placeholder="题目内容" maxlength="500" class="stem" />
        <el-button-group size="small">
          <el-button :disabled="i === 0" @click="move(i, -1)">↑</el-button>
          <el-button :disabled="i === items.length - 1" @click="move(i, 1)">↓</el-button>
          <el-button type="danger" :disabled="locked(it)" @click="items.splice(i, 1)">删除</el-button>
        </el-button-group>
      </div>
      <div v-if="it.qtype === 'SINGLE' || it.qtype === 'MULTI'" class="opts">
        <div v-for="(o, j) in it.options" :key="j" class="opt">
          <span class="letter">{{ String.fromCharCode(65 + j) }}.</span>
          <el-input v-model="it.options[j]" size="small" maxlength="100" />
          <el-button size="small" link type="danger" :disabled="locked(it) || it.options.length <= 2" @click="it.options.splice(j, 1)">×</el-button>
        </div>
        <el-button size="small" link type="primary" :disabled="locked(it) || it.options.length >= 8" @click="it.options.push('')">+ 添加选项</el-button>
      </div>
    </div>
    <el-form-item>
      <el-dropdown @command="add">
        <el-button size="small">+ 添加调查题目</el-button>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item v-for="(l, k) in typeLabel" :key="k" :command="k">{{ l }}</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </el-form-item>
    <el-form-item label="填空显示名字">
      <el-radio-group v-model="nameMode">
        <el-radio value="NONE">不显示</el-radio>
        <el-radio value="TEACHER">仅教师端显示</el-radio>
        <el-radio value="ALL">全部显示</el-radio>
      </el-radio-group>
    </el-form-item>
    <el-form-item label="学生看结果">
      <el-switch v-model="showResult" />
    </el-form-item>
    <el-form-item>
      <el-button size="small" type="primary" @click="save">保存调查题目</el-button>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ page: any }>()
const typeLabel: Record<string, string> = { JUDGE: '判断题', SINGLE: '单选题', MULTI: '多选题', FILL: '填空题' }
const items = ref<any[]>([])
const nameMode = ref('NONE')
const showResult = ref(true)
const hasAnswers = ref(false)
const answered = ref<string[]>([])
let seq = 0

const locked = (it: any) => !!it.id && answered.value.includes(it.id)

async function load() {
  const c = await api.get(`/teacher/activity/pages/${props.page.id}/config`)
  items.value = (c.items || []).map((x: any) => ({ ...x, options: x.options ? [...x.options] : [] }))
  nameMode.value = c.nameMode || 'NONE'
  showResult.value = c.showResult !== false
  hasAnswers.value = c.hasAnswers
  answered.value = c.answeredItems || []
}

function add(qtype: string) {
  items.value.push({ key: `n${++seq}`, qtype, stem: '', options: qtype === 'SINGLE' || qtype === 'MULTI' ? ['', '', ''] : [] })
}

function move(i: number, d: number) {
  const [x] = items.value.splice(i, 1)
  items.value.splice(i + d, 0, x)
}

async function save() {
  try {
    await api.put(`/teacher/activity/pages/${props.page.id}/survey`, {
      nameMode: nameMode.value,
      showResult: showResult.value,
      items: items.value.map(x => ({ id: x.id, qtype: x.qtype, stem: x.stem, options: x.qtype === 'SINGLE' || x.qtype === 'MULTI' ? x.options : undefined }))
    })
    ElMessage.success('调查题目已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(load)
watch(() => props.page.id, load)
</script>

<style scoped>
.item { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 8px; margin: 0 0 8px 90px; }
.item-head { display: flex; align-items: center; gap: 6px; }
.no { width: 22px; height: 22px; border-radius: 50%; background: var(--el-fill-color); display: inline-flex; align-items: center; justify-content: center; font-size: 12px; flex-shrink: 0; }
.stem { flex: 1; }
.opts { margin: 6px 0 0 28px; display: flex; flex-direction: column; gap: 4px; align-items: flex-start; }
.opt { display: flex; align-items: center; gap: 4px; width: 100%; max-width: 420px; }
.letter { width: 18px; color: var(--el-text-color-secondary); }
.w100 { width: 100px; }
.mb { margin: 0 0 8px 90px; width: auto; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.full { width: 100%; }
.ml { margin-left: 8px; }
</style>
