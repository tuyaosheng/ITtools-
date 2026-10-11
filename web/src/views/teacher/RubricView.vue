<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>评价量规 <span class="muted">（用于作品批阅，按评价项逐项打分）</span></span>
        <el-button type="primary" @click="open()">新建量规</el-button>
      </div>
    </template>
    <el-empty v-if="!list.length" description="还没有评价量规" />
    <div class="grid">
      <el-card v-for="r in list" :key="r.id" shadow="hover" class="rubric">
        <div class="r-head">
          <b>{{ r.name }}</b>
          <el-tag size="small">{{ r.total }} 分</el-tag>
        </div>
        <div class="muted">{{ r.teacherName }}{{ r.shared ? ' · 共享' : '' }}</div>
        <div v-for="it in r.items" :key="it.id" class="item"><span>{{ it.name }}</span><span>{{ it.maxScore }}</span></div>
        <div v-if="r.teacherId === auth.user?.userId" class="r-ops">
          <el-button link type="primary" @click="open(r)">编辑</el-button>
          <el-popconfirm title="删除该量规？" @confirm="remove(r)">
            <template #reference><el-button link type="danger">删除</el-button></template>
          </el-popconfirm>
        </div>
      </el-card>
    </div>

    <el-dialog v-model="visible" :title="form.id ? '编辑量规' : '新建量规'" width="560px" append-to-body>
      <el-form label-width="70px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="共享"><el-switch v-model="form.shared" /></el-form-item>
        <el-form-item label="评价项">
          <div class="items">
            <div v-for="(it, i) in form.items" :key="i" class="item-edit">
              <el-input v-model="it.name" placeholder="评价项，如 内容完整" />
              <el-input-number v-model="it.maxScore" :min="0" :max="100" controls-position="right" class="w110" />
              <el-button link type="danger" @click="form.items.splice(i, 1)">删除</el-button>
            </div>
            <el-button size="small" @click="form.items.push({ name: '', maxScore: 10 })">+ 添加评价项</el-button>
            <div class="muted">合计 {{ form.items.reduce((s: number, i: any) => s + (i.maxScore || 0), 0) }} 分</div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="visible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useAuthStore } from '../../stores/auth'

const auth = useAuthStore()
const list = ref<any[]>([])
const visible = ref(false)
const form = reactive<any>({ id: null, name: '', shared: false, items: [] })

async function load() { list.value = await api.get('/teacher/grading/rubrics') }

function open(r?: any) {
  Object.assign(form, r
    ? { id: r.id, name: r.name, shared: r.shared, items: r.items.map((i: any) => ({ name: i.name, maxScore: i.maxScore })) }
    : { id: null, name: '', shared: false, items: [{ name: '内容', maxScore: 40 }, { name: '技术运用', maxScore: 40 }, { name: '创意', maxScore: 20 }] })
  visible.value = true
}

async function save() {
  try {
    if (form.id) await api.put(`/teacher/grading/rubrics/${form.id}`, form)
    else await api.post('/teacher/grading/rubrics', form)
    visible.value = false
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(r: any) {
  await api.del(`/teacher/grading/rubrics/${r.id}`)
  await load()
}

onMounted(load)
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; }
.muted { color: #909399; font-size: 12px; }
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 14px; }
.r-head { display: flex; justify-content: space-between; align-items: center; }
.item { display: flex; justify-content: space-between; font-size: 13px; padding: 4px 0; border-bottom: 1px dashed #ebeef5; }
.r-ops { margin-top: 8px; }
.items { display: flex; flex-direction: column; gap: 8px; width: 100%; }
.item-edit { display: flex; gap: 6px; align-items: center; }
.w110 { width: 110px; flex-shrink: 0; }
</style>
