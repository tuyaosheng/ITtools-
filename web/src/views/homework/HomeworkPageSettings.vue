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
      <span class="muted">学生提交后按下列规则自动检测评分（总分 {{ total }}）</span>
    </el-form-item>
    <el-form-item v-if="cfg.autoGrade" label="评分规则">
      <div class="rules">
        <div v-for="(r, i) in rules" :key="i" class="rule">
          <el-select v-model="r.type" placeholder="检测项" class="w200" filterable>
            <el-option v-for="c in catalog" :key="c.type" :label="c.name" :value="c.type">
              <span>{{ c.name }}</span><span class="ext">{{ c.exts === '*' ? '全部' : c.exts }}</span>
            </el-option>
          </el-select>
          <template v-if="meta(r.type)">
            <el-input v-if="meta(r.type).param1" v-model="r.param1" :placeholder="meta(r.type).param1" class="p" />
            <el-input v-if="meta(r.type).param2" v-model="r.param2" :placeholder="meta(r.type).param2" class="p" />
          </template>
          <el-input-number v-model="r.score" :min="0" :max="100" controls-position="right" class="w110" />
          <span class="muted">分</span>
          <el-button link type="danger" @click="rules.splice(i, 1)">删除</el-button>
        </div>
        <el-button size="small" @click="rules.push({ type: 'TEXT_CONTAINS', score: 10 })">+ 添加规则</el-button>
      </div>
    </el-form-item>
    <el-form-item>
      <el-button type="primary" plain @click="save">保存作品设置与评分规则</el-button>
    </el-form-item>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const props = defineProps<{ page: any }>()
const cfg = reactive<any>({ allowedExts: '', peerReview: false, showClassmates: false, autoGrade: true, rubricId: null })
const rules = ref<any[]>([])
const catalog = ref<any[]>([])
const rubrics = ref<any[]>([])
const total = computed(() => rules.value.reduce((s, r) => s + (Number(r.score) || 0), 0))
const meta = (t: string) => catalog.value.find(c => c.type === t)

async function load() {
  Object.assign(cfg, await api.get(`/teacher/homework/pages/${props.page.id}/config`))
  rules.value = await api.get(`/teacher/grading/pages/${props.page.id}/rules`)
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
.w110 { width: 110px; }
.p { width: 200px; }
.muted { color: #909399; font-size: 12px; margin-left: 8px; }
.rules { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.rule { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.ext { float: right; color: #a8abb2; font-size: 12px; margin-left: 12px; }
</style>
