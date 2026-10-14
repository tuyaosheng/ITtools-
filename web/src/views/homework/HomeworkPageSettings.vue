<template>
  <div class="hw-settings">
    <el-divider content-position="left">作品提交设置</el-divider>
    <el-form-item label="允许的类型">
      <el-input v-model="cfg.allowedExts" placeholder="如 docx,pptx（留空不限）" class="w320" />
    </el-form-item>
    <el-form-item label="展示与互评">
      <el-checkbox v-model="cfg.showClassmates">同学可查看彼此作品</el-checkbox>
      <el-checkbox v-model="cfg.peerReview">开启同学互评</el-checkbox>
    </el-form-item>
    <el-form-item label="评价量规">
      <el-select v-model="cfg.rubricId" clearable placeholder="不使用量规（直接打分）" class="w320">
        <el-option v-for="r in rubrics" :key="r.id" :label="`${r.name}（${r.total}分）`" :value="r.id" />
      </el-select>
    </el-form-item>
    <el-form-item label="自动评分">
      <el-switch v-model="cfg.autoGrade" />
      <span class="muted">学生提交后按下列评分点自动检测评分（总分 {{ total }}）</span>
    </el-form-item>
    <template v-if="cfg.autoGrade">
      <el-form-item label="学生看成绩">
        <el-radio-group v-model="cfg.autoVisibility">
          <el-radio value="ALL">分数和各评分点</el-radio>
          <el-radio value="SCORE">只显示分数</el-radio>
          <el-radio value="NONE">不显示</el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="课后练习">
        <el-checkbox v-model="cfg.practice">作为课后操作题开放</el-checkbox>
        <span class="muted">上过本课程的班级学生可在“课后操作题”中下载素材、反复提交并即时得分（保留最好成绩）</span>
      </el-form-item>
      <el-form-item label="评分点">
        <div class="rules">
          <div class="tools">
            <el-upload :show-file-list="false" accept=".docx,.xlsx,.pptx" :http-request="inspect">
              <el-button size="small" :loading="inspecting">读取文件格式</el-button>
            </el-upload>
            <el-upload :show-file-list="false" :http-request="testRules">
              <el-button size="small" :loading="testing" :disabled="!rules.length">用标准答案检验</el-button>
            </el-upload>
            <span class="muted">上传做好的标准答案，查看各段落/单元格/幻灯片的格式，或检验评分点能否全部通过</span>
          </div>
          <div v-for="(r, i) in rules" :key="i" class="rule">
            <span class="no">{{ i + 1 }}</span>
            <el-select v-model="r.type" placeholder="检测项" class="w200" filterable>
              <el-option-group v-for="g in groups" :key="g.label" :label="g.label">
                <el-option v-for="c in g.items" :key="c.type" :label="c.name" :value="c.type" />
              </el-option-group>
            </el-select>
            <template v-if="meta(r.type)">
              <el-input v-if="meta(r.type).param1" v-model="r.param1" :placeholder="meta(r.type).param1" class="p" :title="meta(r.type).param1" />
              <el-input v-if="meta(r.type).param2" v-model="r.param2" :placeholder="meta(r.type).param2" class="p" :title="meta(r.type).param2" />
              <el-input v-if="meta(r.type).param3" v-model="r.param3" :placeholder="meta(r.type).param3" class="p" :title="meta(r.type).param3" />
            </template>
            <el-input v-model="r.note" placeholder="说明（学生可见）" class="w150" />
            <el-input-number v-model="r.score" :min="0" :max="100" controls-position="right" class="w100" />
            <span class="muted">分</span>
            <el-button-group size="small">
              <el-button :disabled="i === 0" @click="move(i, -1)">↑</el-button>
              <el-button :disabled="i === rules.length - 1" @click="move(i, 1)">↓</el-button>
            </el-button-group>
            <el-button link type="danger" @click="rules.splice(i, 1)">删除</el-button>
            <span v-if="testResult" class="res" :class="testResult.details[i]?.passed ? 'ok' : 'bad'">
              {{ testResult.details[i]?.passed ? '✓' : '✗' }} {{ testResult.details[i]?.message }}
            </span>
          </div>
          <div class="tools">
            <el-button size="small" @click="rules.push({ type: defaultType, score: 10 })">+ 添加评分点</el-button>
            <span v-if="testResult" class="muted">检验结果：{{ testResult.score }}/{{ testResult.max }} 分</span>
          </div>
        </div>
      </el-form-item>
    </template>
    <el-form-item>
      <el-button type="primary" plain @click="save">保存作品设置与评分点</el-button>
    </el-form-item>

    <el-dialog v-model="inspectDlg" :title="`文件格式：${inspectName}`" width="900px" append-to-body>
      <OfficeInspectView v-if="inspected" :data="inspected" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import OfficeInspectView from './OfficeInspectView.vue'

const props = defineProps<{ page: any }>()
const cfg = reactive<any>({ allowedExts: '', peerReview: false, showClassmates: false, autoGrade: true, rubricId: null, autoVisibility: 'ALL', practice: false })
const rules = ref<any[]>([])
const catalog = ref<any[]>([])
const rubrics = ref<any[]>([])
const total = computed(() => rules.value.reduce((s, r) => s + (Number(r.score) || 0), 0))
const meta = (t: string) => catalog.value.find(c => c.type === t)
const inspecting = ref(false)
const inspectDlg = ref(false)
const inspected = ref<any>(null)
const inspectName = ref('')
const testing = ref(false)
const testResult = ref<any>(null)

/** 按允许的类型推荐默认检测项 */
const defaultType = computed(() => {
  const e = String(cfg.allowedExts || '')
  if (e.includes('docx')) return 'DOCX_PARA_FONT'
  if (e.includes('xlsx')) return 'XLSX_CELL_EQUALS'
  if (e.includes('pptx')) return 'PPTX_MIN_SLIDES'
  return 'TEXT_CONTAINS'
})

const groups = computed(() => {
  const g = [
    { label: 'Word', test: (c: any) => c.type.startsWith('DOCX_') },
    { label: 'Excel', test: (c: any) => c.type.startsWith('XLSX_') },
    { label: 'PowerPoint', test: (c: any) => c.type.startsWith('PPTX_') },
    { label: '通用 / 网页 / 文本', test: (c: any) => !/^(DOCX|XLSX|PPTX)_/.test(c.type) }
  ]
  return g.map(x => ({ label: x.label, items: catalog.value.filter(x.test) })).filter(x => x.items.length)
})

function move(i: number, d: number) {
  const [x] = rules.value.splice(i, 1)
  rules.value.splice(i + d, 0, x)
}

async function load() {
  Object.assign(cfg, await api.get(`/teacher/homework/pages/${props.page.id}/config`))
  rules.value = await api.get(`/teacher/grading/pages/${props.page.id}/rules`)
  testResult.value = null
}

async function save() {
  try {
    await api.put(`/teacher/homework/pages/${props.page.id}/config`, cfg)
    await api.put(`/teacher/grading/pages/${props.page.id}/rules`, { rules: rules.value })
    ElMessage.success('作品设置已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function inspect(o: UploadRequestOptions) {
  inspecting.value = true
  try {
    inspected.value = await api.upload('/teacher/grading/inspect', o.file)
    inspectName.value = o.file.name
    inspectDlg.value = true
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    inspecting.value = false
  }
}

async function testRules(o: UploadRequestOptions) {
  testing.value = true
  try {
    testResult.value = await api.upload('/teacher/grading/test', o.file, { rules: JSON.stringify(rules.value) })
    const failed = testResult.value.details.filter((d: any) => !d.passed).length
    if (failed) ElMessage.warning(`有 ${failed} 个评分点未通过，请检查参数或答案文件`)
    else ElMessage.success('标准答案通过全部评分点')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    testing.value = false
  }
}

watch(() => props.page.id, load)
onMounted(async () => {
  catalog.value = await api.get('/teacher/grading/catalog')
  rubrics.value = await api.get('/teacher/grading/rubrics')
  await load()
})
</script>

<style scoped>
.w320 { width: 320px; }
.w200 { width: 200px; }
.w150 { width: 150px; }
.w100 { width: 100px; }
.p { width: 190px; }
.muted { color: #909399; font-size: 12px; margin-left: 8px; }
.rules { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.rule { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; padding-bottom: 6px; border-bottom: 1px dashed var(--el-border-color-lighter); }
.tools { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.no { width: 20px; color: var(--el-text-color-secondary); font-size: 12px; text-align: right; }
.res { font-size: 12px; width: 100%; padding-left: 26px; }
.res.ok { color: var(--el-color-success); }
.res.bad { color: var(--el-color-danger); }
</style>
