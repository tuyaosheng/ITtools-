<template>
  <div class="page-grid">
    <el-card shadow="never" class="side">
      <template #header>
        <div class="card-head">
          <span>机房模型</span>
          <el-button type="primary" size="small" @click="openCreate">新增</el-button>
        </div>
      </template>
      <el-empty v-if="!rooms.length" description="暂无机房模型" :image-size="60" />
      <div
        v-for="r in rooms"
        :key="r.id"
        class="room-item"
        :class="{ active: r.id === currentId }"
        @click="select(r.id)"
      >
        <div class="room-name">{{ r.name }}</div>
        <div class="room-meta">{{ r.rows }} 行 × {{ r.cols }} 列 · 已放置 {{ r.assigned }} 台</div>
        <div class="room-ops" @click.stop>
          <el-button link type="primary" size="small" @click="openEdit(r)">编辑</el-button>
          <el-popconfirm title="删除该机房模型？（班级的机房配置会被清空）" @confirm="remove(r.id)">
            <template #reference><el-button link type="danger" size="small">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="main">
      <template #header>
        <div class="card-head">
          <span>{{ current ? `座位布局 · ${current.name}` : '座位布局' }}</span>
          <div v-if="current">
            <el-button size="small" @click="autoFill">按计算机名自动排布</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveLayout">保存布局</el-button>
          </div>
        </div>
      </template>
      <el-empty v-if="!current" description="请选择左侧的机房模型" />
      <template v-else>
        <div class="podium">讲 台</div>
        <div class="grid" :style="{ gridTemplateColumns: `repeat(${current.cols}, minmax(96px, 1fr))` }">
          <template v-for="r in current.rows" :key="'r' + r">
            <div v-for="c in current.cols" :key="`${r}-${c}`" class="cell" :class="{ filled: !!cells[key(r, c)] }">
              <div class="pos">{{ r }}-{{ c }}</div>
              <el-select
                v-model="cells[key(r, c)]"
                size="small"
                clearable
                filterable
                placeholder="空"
                class="cell-select"
              >
                <el-option
                  v-for="w in workstations"
                  :key="w.id"
                  :label="w.pcName"
                  :value="w.id"
                  :disabled="isUsedElsewhere(w.id, r, c)"
                />
              </el-select>
            </div>
          </template>
        </div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑机房模型' : '新增机房模型'" width="420px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 一号机房" /></el-form-item>
        <el-form-item label="行数"><el-input-number v-model="form.rows" :min="1" :max="30" /></el-form-item>
        <el-form-item label="列数"><el-input-number v-model="form.cols" :min="1" :max="30" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

interface Room { id: number; name: string; rows: number; cols: number; assigned: number }

const rooms = ref<Room[]>([])
const workstations = ref<any[]>([])
const currentId = ref<number | null>(null)
const current = computed(() => rooms.value.find(r => r.id === currentId.value) || null)
const cells = reactive<Record<string, number | null>>({})
const saving = ref(false)

const dialogVisible = ref(false)
const editingId = ref<number | null>(null)
const form = reactive({ name: '', rows: 6, cols: 8 })

const key = (r: number, c: number) => `${r}-${c}`

function isUsedElsewhere(wsId: number, r: number, c: number) {
  return Object.entries(cells).some(([k, v]) => v === wsId && k !== key(r, c))
}

async function loadRooms() {
  rooms.value = await api.get('/admin/lab/rooms')
}

async function select(id: number) {
  currentId.value = id
  Object.keys(cells).forEach(k => delete cells[k])
  const layout: any[] = await api.get(`/admin/lab/rooms/${id}/layout`)
  layout.forEach(s => { cells[key(s.row, s.col)] = s.workstationId })
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', rows: 6, cols: 8 })
  dialogVisible.value = true
}

function openEdit(r: Room) {
  editingId.value = r.id
  Object.assign(form, { name: r.name, rows: r.rows, cols: r.cols })
  dialogVisible.value = true
}

async function submit() {
  try {
    if (editingId.value) {
      await api.put(`/admin/lab/rooms/${editingId.value}`, form)
    } else {
      const created = await api.post('/admin/lab/rooms', form)
      currentId.value = created.id
    }
    dialogVisible.value = false
    ElMessage.success('保存成功')
    await loadRooms()
    if (currentId.value) await select(currentId.value)
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function remove(id: number) {
  try {
    await api.del(`/admin/lab/rooms/${id}`)
    if (currentId.value === id) currentId.value = null
    ElMessage.success('已删除')
    await loadRooms()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function saveLayout() {
  if (!current.value) return
  saving.value = true
  try {
    const list: any[] = []
    for (let r = 1; r <= current.value.rows; r++) {
      for (let c = 1; c <= current.value.cols; c++) {
        list.push({ row: r, col: c, workstationId: cells[key(r, c)] ?? null })
      }
    }
    await api.put(`/admin/lab/rooms/${current.value.id}/layout`, { cells: list })
    ElMessage.success('布局已保存')
    await loadRooms()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function autoFill() {
  if (!current.value) return
  try {
    await api.post(`/admin/lab/rooms/${current.value.id}/auto-fill`)
    await select(current.value.id)
    await loadRooms()
    ElMessage.success('已按计算机名顺序排布')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  await loadRooms()
  workstations.value = await api.get('/admin/lab-workstations')
  if (rooms.value.length) await select(rooms.value[0].id)
})
</script>

<style scoped>
.page-grid { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: center; }
.room-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.room-item:hover { background: #f5f7fa; }
.room-item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.room-name { font-weight: 600; color: #303133; }
.room-meta { font-size: 12px; color: #909399; margin-top: 2px; }
.room-ops { margin-top: 4px; }
.podium { text-align: center; background: #e5eaf3; border-radius: 6px; padding: 6px; margin-bottom: 12px; color: #606266; letter-spacing: 8px; }
.grid { display: grid; gap: 8px; overflow-x: auto; }
.cell { border: 1px dashed #dcdfe6; border-radius: 6px; padding: 6px; background: #fff; }
.cell.filled { border-style: solid; border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.pos { font-size: 11px; color: #a8abb2; margin-bottom: 4px; }
.cell-select { width: 100%; }
@media (max-width: 900px) { .page-grid { grid-template-columns: 1fr; } }
</style>
