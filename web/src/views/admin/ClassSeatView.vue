<template>
  <el-card shadow="never">
    <template #header>
      <div class="card-head">
        <span>班级机房与固定机位</span>
        <div><Hint block>启用固定机位后，该班学生只能在绑定的机位上登录（原“班级+姓名+固定机位模式”）</Hint></div>
      </div>
    </template>
    <el-table v-loading="loading" :data="configs" stripe>
      <el-table-column prop="className" label="班级" min-width="140" />
      <el-table-column label="上课机房" min-width="200">
        <template #default="{ row }: { row: any }">
          <el-select v-model="row.roomId" clearable placeholder="未设置" size="small" @change="saveConfig(row)">
            <el-option v-for="r in rooms" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="固定机位" width="110">
        <template #default="{ row }: { row: any }">
          <el-switch v-model="row.fixedSeat" @change="saveConfig(row)" />
        </template>
      </el-table-column>
      <el-table-column label="已绑定座位" width="120">
        <template #default="{ row }: { row: any }">{{ row.boundCount }} / {{ row.studentCount }}</template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="{ row }: { row: any }">
          <el-button link type="primary" :disabled="!row.roomId" @click="openSeats(row)">分配座位</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="seatDialog" :title="`分配座位 · ${editing?.className ?? ''}`" width="820px" append-to-body>
      <div class="dialog-ops">
        <el-button size="small" @click="autoAssign">按学号自动分配</el-button>
        <el-button size="small" @click="students.forEach(s => (s.seatId = null))">全部清空</el-button>
      </div>
      <el-table :data="students" max-height="480" size="small">
        <el-table-column prop="studentNo" label="学号" width="100" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column label="座位（行-列 机位）">
          <template #default="{ row }: { row: any }">
            <el-select v-model="row.seatId" clearable filterable size="small" placeholder="未绑定" class="full">
              <el-option
                v-for="s in seats"
                :key="s.seatId"
                :value="s.seatId"
                :label="`${s.row}-${s.col}  ${s.pcName}`"
                :disabled="students.some(o => o !== row && o.seatId === s.seatId)"
              />
            </el-select>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="seatDialog = false">取消</el-button>
        <el-button type="primary" @click="saveSeats">保存</el-button>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const configs = ref<any[]>([])
const rooms = ref<any[]>([])
const loading = ref(false)
const seatDialog = ref(false)
const editing = ref<any>(null)
const students = ref<any[]>([])
const seats = ref<any[]>([])

async function load() {
  loading.value = true
  try {
    configs.value = await api.get('/admin/lab/class-configs')
  } finally {
    loading.value = false
  }
}

async function saveConfig(row: any) {
  try {
    await api.put(`/admin/lab/class-configs/${row.classId}`, { roomId: row.roomId ?? null, fixedSeat: row.fixedSeat })
    ElMessage.success('已保存')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
    await load()
  }
}

async function openSeats(row: any) {
  editing.value = row
  const [studs, layout] = await Promise.all([
    api.get(`/admin/lab/class-configs/${row.classId}/students`),
    api.get(`/admin/lab/rooms/${row.roomId}/layout`)
  ])
  students.value = studs
  seats.value = (layout as any[]).filter(s => s.workstationId)
  seatDialog.value = true
}

async function autoAssign() {
  try {
    students.value = await api.post(`/admin/lab/class-configs/${editing.value.classId}/auto-assign`)
    ElMessage.success('已按学号顺序分配')
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function saveSeats() {
  try {
    await api.put(`/admin/lab/class-configs/${editing.value.classId}/students`, {
      bindings: students.value.map(s => ({ userId: s.userId, seatId: s.seatId ?? null }))
    })
    ElMessage.success('座位已保存')
    seatDialog.value = false
    await load()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  rooms.value = await api.get('/admin/lab/rooms')
  await load()
})
</script>

<style scoped>
.card-head { display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 8px; }
.hint { font-size: 12px; color: var(--el-text-color-secondary); }
.dialog-ops { margin-bottom: 10px; }
.full { width: 100%; }
</style>
