<template>
  <div v-if="data">
    <el-empty v-if="!data.items.length" description="教师还没有设置调查题目" />
    <template v-else>
      <el-alert v-if="data.submitted && !editing" type="success" :closable="false" show-icon title="你已提交调查，谢谢参与！" class="mb">
        <el-button v-if="active" size="small" link type="primary" @click="editing = true">修改我的回答</el-button>
      </el-alert>
      <el-form v-if="!data.submitted || editing" label-position="top" class="form">
        <el-form-item v-for="(it, i) in data.items" :key="it.id" :label="`${i + 1}. ${it.stem}`" required>
          <el-radio-group v-if="it.qtype === 'JUDGE'" v-model="answers[it.id]">
            <el-radio value="T">对 / 是</el-radio><el-radio value="F">错 / 否</el-radio>
          </el-radio-group>
          <el-radio-group v-else-if="it.qtype === 'SINGLE'" v-model="answers[it.id]" class="col">
            <el-radio v-for="(o, j) in it.options" :key="j" :value="letter(j)">{{ letter(j) }}. {{ o }}</el-radio>
          </el-radio-group>
          <el-checkbox-group v-else-if="it.qtype === 'MULTI'" v-model="answers[it.id]" class="col">
            <el-checkbox v-for="(o, j) in it.options" :key="j" :value="letter(j)">{{ letter(j) }}. {{ o }}</el-checkbox>
          </el-checkbox-group>
          <el-input v-else v-model="answers[it.id]" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
        <el-button type="primary" :loading="sending" :disabled="!active" @click="submit">{{ data.submitted ? '保存修改' : '提交调查' }}</el-button>
        <el-button v-if="editing" @click="editing = false">取消</el-button>
        <span v-if="!active" class="muted ml">课堂已结束，不能再提交</span>
      </el-form>
      <template v-if="data.results && !editing">
        <el-divider content-position="left">全班调查结果</el-divider>
        <SurveyResults :items="data.items" :results="data.results" />
      </template>
    </template>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import SurveyResults from './SurveyResults.vue'

const props = defineProps<{ lessonId: number; page: any; lesson?: any }>()
const data = ref<any>(null)
const answers = reactive<Record<string, any>>({})
const editing = ref(false)
const sending = ref(false)
const active = computed(() => props.lesson?.status !== 'ENDED')
const letter = (j: number) => String.fromCharCode(65 + j)
const url = () => `/student/activity/lessons/${props.lessonId}/pages/${props.page.id}/survey`

function fill(d: any) {
  data.value = d
  for (const it of d.items) answers[it.id] = d.answers[it.id] ?? (it.qtype === 'MULTI' ? [] : '')
}

async function submit() {
  sending.value = true
  try {
    fill(await api.put(url(), { answers }))
    editing.value = false
    ElMessage.success('已提交')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    sending.value = false
  }
}

onMounted(async () => fill(await api.get(url())))
</script>

<style scoped>
.form { max-width: 720px; }
.col { display: flex; flex-direction: column; align-items: flex-start; }
.mb { margin-bottom: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.ml { margin-left: 8px; }
</style>
