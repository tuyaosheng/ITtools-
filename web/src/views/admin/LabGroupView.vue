<template>
  <div class="page-grid">
    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>分组模型</span>
          <el-button type="primary" size="small" @click="create">新增</el-button>
        </div>
      </template>
      <el-empty v-if="!models.length" description="暂无分组模型" :image-size="60" />
      <div v-for="m in models" :key="m.id" class="item" :class="{ active: m.id === currentId }" @click="select(m.id)">
        <div class="name">{{ m.name }}</div>
        <div class="meta">{{ m.groupCount }} 个小组 · {{ m.memberCount }} 台机位</div>
        <div @click.stop>
          <el-button link size="small" type="primary" @click="rename(m)">重命名</el-button>
          <el-popconfirm title="删除该分组模型？" @confirm="remove(m.id)">
            <template #reference><el-button link size="small" type="danger">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </el-card>

    <el-card shadow="never">
      <template #header>
        <div class="card-head">
          <span>机位分组{{ current ? ' · ' + current.name : '' }}</span>
          <div v-if="current" class="ops">
            每组
            <el-input-number v-model="autoSize" :min="1" :max="20" size="small" />
            台
            <el-button size="small" @click="autoGroup">自动分组</el-button>
            <el-button size="small" type="primary" @click="save">保存</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!current" description="选择左侧的分组模型" />
      <template v-else>
        <div class="summary">
          <el-tag v-for="g in groupSummary" :key="g.no" class="tag">第 {{ g.no }} 组：{{ g.count }} 台</el-tag>
        </div>
        <el-table :data="members" stripe max-height="560">
          <el-table-column prop="pcName" label="计算机名" />
          <el-table-column prop="ipAddress" label="IP地址" />
          <el-table-column label="组号" width="180">
            <template #default="{ row }: { row: any }">
              <el-input-number v-model="row.groupNo" :min="1" :max="50" size="small" controls-position="right" />
            </template>
          </el-table-column>
        </el-table>
      </template>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'

const models = ref<any[]>([])
const currentId = ref<number | null>(null)
const current = computed(() => models.value.find(m => m.id === currentId.value) || null)
const members = ref<any[]>([])
const autoSize = ref(4)

const groupSummary = computed(() => {
  const map = new Map<number, number>()
  members.value.forEach(m => { if (m.groupNo) map.set(m.groupNo, (map.get(m.groupNo) || 0) + 1) })
  return [...map.entries()].sort((a, b) => a[0] - b[0]).map(([no, count]) => ({ no, count }))
})

async function load() {
  models.value = await api.get('/admin/lab/group-models')
}

async function select(id: number) {
  currentId.value = id
  members.value = await api.get(`/admin/lab/group-models/${id}/members`)
}

async function create() {
  try {
    const { value } = await ElMessageBox.prompt('分组模型名称', '新增分组模型', { inputPattern: /\S+/, inputErrorMessage: '请输入名称' })
    const m = await api.post('/admin/lab/group-models', { name: value })
    await load()
    await select(m.id)
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function rename(m: any) {
  try {
    const { value } = await ElMessageBox.prompt('新名称', '重命名', { inputValue: m.name, inputPattern: /\S+/ })
    await api.put(`/admin/lab/group-models/${m.id}`, { name: value })
    await load()
  } catch (err: any) {
    if (err !== 'cancel' && err !== 'close') ElMessage.error(errMsg(err))
  }
}

async function remove(id: number) {
  await api.del(`/admin/lab/group-models/${id}`)
  if (currentId.value === id) { currentId.value = null; members.value = [] }
  await load()
}

async function autoGroup() {
  if (!currentId.value) return
  members.value = await api.post(`/admin/lab/group-models/${currentId.value}/auto`, null, { size: autoSize.value })
  await load()
  ElMessage.success('已自动分组')
}

async function save() {
  if (!currentId.value) return
  try {
    members.value = await api.put(`/admin/lab/group-models/${currentId.value}/members`, {
      assignments: members.value.map(m => ({ workstationId: m.workstationId, groupNo: m.groupNo ?? null }))
    })
    await load()
    ElMessage.success('保存成功')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  await load()
  if (models.value.length) await select(models.value[0].id)
})
</script>

<style scoped>
.page-grid { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.ops { display: flex; align-items: center; gap: 6px; font-size: 13px; }
.item { padding: 10px 12px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.item:hover { background: var(--el-fill-color-light); }
.item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.name { font-weight: 600; }
.meta { font-size: 12px; color: var(--el-text-color-secondary); }
.summary { margin-bottom: 12px; }
.tag { margin: 0 6px 6px 0; }
@media (max-width: 900px) { .page-grid { grid-template-columns: 1fr; } }
</style>
