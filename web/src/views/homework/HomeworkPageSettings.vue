<template>
  <div class="hw-settings">
    <el-divider content-position="left">作品提交设置</el-divider>
    <el-form-item label="图形化编程">
      <el-switch v-model="cfg.blockEditor" />
      <span class="muted bx">学生在这一页里用平台的图形化编程（掌控板，积木和 Mind+ 一样）拼程序、上传到掌控板、点“提交作品”交 .mpcode，程序自动保存，下节课接着做。自动评分可以用 Mind+ 规则（含“MQTT 设置正确”）。</span>
    </el-form-item>
    <el-form-item v-if="cfg.blockEditor" label="旁边显示任务单">
      <el-select v-model="cfg.taskPageTitle" clearable filterable placeholder="不显示（只有积木编辑器）" class="w320">
        <el-option v-for="t in taskPages" :key="t.id" :label="`${t.title}（${t.pageType === 'SURVEY' ? '课堂调查' : '图文'}）`" :value="t.title" />
      </el-select>
      <span class="muted bx">学生在这一页左边拼积木、右边看任务单（步骤、填读数、选择题）并提交，不用来回切换页面。选的是本课程里的“课堂调查”或“图文”页；上课时那一页也要打开，学生才能填写。</span>
      <span v-if="cfg.taskPageTitle && !taskPages.some(t => t.title === cfg.taskPageTitle)" class="warn">课程里找不到标题为“{{ cfg.taskPageTitle }}”的任务单，改过标题的话请重新选择</span>
    </el-form-item>
    <el-form-item v-if="cfg.blockEditor" label="起始程序">
      <el-tag :type="cfg.hasStarter ? 'success' : 'info'" effect="plain">{{ cfg.hasStarter ? '已设置' : '未设置' }}</el-tag>
      <el-button size="small" class="ml8" @click="starterDlg = true">{{ cfg.hasStarter ? '修改起始程序' : '设置起始程序' }}</el-button>
      <el-button v-if="cfg.hasStarter" size="small" link type="danger" @click="clearStarter">清除</el-button>
      <span class="muted bx">学生第一次打开这一页时，可以选“老师给的起始程序”接着拼。在编辑器里直接搭（自动保存），或点编辑器的“打开”载入 .mpcode（Mind+ 做的也行）。不同角色的作品页各配一份，就是按角色不同。复制课程、导出课程包会带上。</span>
    </el-form-item>
    <el-form-item v-if="cfg.blockEditor" label="以前的程序">
      <el-checkbox v-model="cfg.allowHistory">允许学生选用自己以前的程序</el-checkbox>
      <span class="muted bx">学生可以从自己别的课的程序、“图形化编程”随时练习、交过的 .mpcode 作品开始，上节课的接着做。</span>
    </el-form-item>
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
      <span class="muted" style="margin-left: 8px">总分 {{ total }}</span>
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

    <el-dialog v-model="starterDlg" :title="`起始程序：${page.title}`" fullscreen append-to-body destroy-on-close @closed="refreshStarter">
      <iframe :src="`/mpblocks/index.html?starter=${page.id}`" class="starter-frame" />
    </el-dialog>
    <el-dialog v-model="inspectDlg" :title="`文件格式：${inspectName}`" width="900px" append-to-body>
      <OfficeInspectView v-if="inspected" :data="inspected" />
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../../api/client'
import OfficeInspectView from './OfficeInspectView.vue'

const props = defineProps<{ page: any }>()
const cfg = reactive<any>({ allowedExts: '', peerReview: false, showClassmates: false, autoGrade: true, rubricId: null, autoVisibility: 'ALL', practice: false, blockEditor: false, taskPageTitle: null, allowHistory: true, hasStarter: false })
const starterDlg = ref(false)
async function refreshStarter() {
  try {
    const c = await api.get(`/teacher/homework/pages/${props.page.id}/config`)
    cfg.hasStarter = !!c.hasStarter
  } catch { /* 忽略 */ }
}
async function clearStarter() {
  try {
    await ElMessageBox.confirm('清除这一页的起始程序？已经开始做的学生不受影响', '清除起始程序', { type: 'warning' })
  } catch { return }
  try {
    await api.del(`/teacher/mpblocks/pages/${props.page.id}/starter`)
    cfg.hasStarter = false
    ElMessage.success('已清除起始程序')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}
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

const taskPages = ref<any[]>([])
async function loadTaskPages() {
  if (!props.page?.courseId) return
  try {
    const c = await api.get(`/teacher/courses/${props.page.courseId}`)
    taskPages.value = (c.pages || []).filter((p: any) => (p.pageType === 'SURVEY' || p.pageType === 'TEXT') && p.id !== props.page.id)
  } catch {
    taskPages.value = []
  }
}

async function load() {
  loadTaskPages()
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
.ml8 { margin-left: 8px; }
.starter-frame { width: 100%; height: calc(100vh - 90px); border: 0; display: block; }
.w320 { width: 320px; }
.w200 { width: 200px; }
.w150 { width: 150px; }
.w100 { width: 100px; }
.p { width: 190px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; margin-left: 8px; }
.rules { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.rule { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; padding-bottom: 6px; border-bottom: 1px dashed var(--el-border-color-lighter); }
.tools { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
.no { width: 20px; color: var(--el-text-color-secondary); font-size: 12px; text-align: right; }
.res { font-size: 12px; width: 100%; padding-left: 26px; }
.res.ok { color: var(--el-color-success); }
.res.bad { color: var(--el-color-danger); }
.bx { margin-left: 10px; font-size: 12px; line-height: 1.5; }
.warn { display: block; color: var(--el-color-danger); font-size: 12px; margin-top: 4px; }
</style>
