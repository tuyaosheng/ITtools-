<template>
  <div class="page-grid">
    <el-card shadow="never" class="side">
      <template #header>
        <div class="card-head">
          <span>机房模型</span>
          <div>
            <el-button type="primary" size="small" @click="openScheme(null)">按方案新建</el-button>
            <el-button size="small" @click="openCreate">空白新建</el-button>
          </div>
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
          <el-button link type="primary" size="small" @click="openScheme(r)">方案设置</el-button>
          <el-button link type="primary" size="small" @click="openEdit(r)">改名/行列</el-button>
          <el-popconfirm title="删除该机房模型？（班级的机房配置会被清空）" @confirm="remove(r.id)">
            <template #reference><el-button link type="danger" size="small">删除</el-button></template>
          </el-popconfirm>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" class="main">
      <template #header>
        <div class="card-head">
          <span>{{ current ? `座位图 · ${current.name}` : '座位图' }}</span>
          <div v-if="current" class="head-chips">
            <span class="chip">{{ placed.length }} 台机位</span>
            <span v-if="net.enabled" class="chip" :class="net.reachable ? 'ok' : 'bad'">
              {{ net.reachable ? `网络控制器已连接 · ${onlineCount} 台开机` : '连不上网络控制器' }}
            </span>
          </div>
        </div>
      </template>
      <el-empty v-if="!current" description="选择左侧的机房" />
      <template v-else>
        <!-- 平时只看；“管理”才能点选座位做批量操作，“调整机位”才能改哪台电脑放在哪个格子 -->
        <div class="seatbar">
          <template v-if="mode === 'view'">
            <el-button size="small" type="primary" @click="setMode('manage')">管理</el-button>
            <el-button size="small" @click="setMode('edit')">调整机位</el-button>
            <el-button size="small" @click="openScheme(current)">方案设置</el-button>
          </template>
          <template v-else-if="mode === 'manage'">
            <el-button size="small" @click="selectWhere(() => true)">全选</el-button>
            <el-button v-if="net.reachable" size="small" @click="selectWhere(w => isOnline(w))">已开机</el-button>
            <el-button v-if="net.enabled" size="small" @click="selectWhere(w => netOf(w) === 'off')">已断网</el-button>
            <el-button size="small" @click="selected = new Set()">清空</el-button>
            <span class="muted">点座位选中，可多选</span>
          </template>
          <template v-else>
            <el-button size="small" @click="autoFill">按计算机名自动排布</el-button>
            <el-button size="small" type="primary" :loading="saving" @click="saveLayout">保存布局</el-button>
            <el-button size="small" @click="cancelEdit">完成</el-button>
            <span class="muted">在格子里选这台电脑；空格就是过道</span>
          </template>
          <span class="grow" />
          <el-button v-if="net.enabled" link type="primary" size="small" @click="loadNet">刷新网络状态</el-button>
        </div>

        <div class="podium">讲 台</div>
        <div class="seatgrid" :class="{ managing: mode === 'manage' }"
          :style="{ gridTemplateColumns: `repeat(${current.cols}, minmax(${mode === 'edit' ? 104 : 78}px, 1fr))` }">
          <template v-for="r in current.rows" :key="'r' + r">
            <template v-for="c in current.cols" :key="`${r}-${c}`">
              <div v-if="mode === 'edit'" class="cell" :class="{ filled: !!cells[key(r, c)] }">
                <div class="pos">{{ r }}-{{ c }}<span v-if="cells[key(r, c)]" class="ip">{{ ipTail(ws(cells[key(r, c)])) }}</span></div>
                <el-select v-model="cells[key(r, c)]" size="small" clearable filterable placeholder="空" class="cell-select">
                  <el-option v-for="w in workstations" :key="w.id" :label="`${w.pcName}（${w.ipAddress}）`" :value="w.id"
                    :disabled="isUsedElsewhere(w.id, r, c)" />
                </el-select>
              </div>
              <div v-else-if="ws(cells[key(r, c)])" class="seat" :class="seatClass(ws(cells[key(r, c)]))"
                :title="`${ws(cells[key(r, c)]).pcName}  IP ${ws(cells[key(r, c)]).ipAddress}  第${r}排第${c}列`"
                @click="toggleSel(cells[key(r, c)])">
                <div class="no"><span>{{ r }}-{{ c }}</span><span>{{ ipTail(ws(cells[key(r, c)])) }}</span></div>
                <div class="nm">{{ ws(cells[key(r, c)]).pcName }}</div>
                <div class="sub">{{ hostOf(ws(cells[key(r, c)])) }}</div>
                <div class="state">
                  <span v-if="net.reachable" class="pw" :class="isOnline(ws(cells[key(r, c)])) ? 'on' : 'off'">
                    {{ isOnline(ws(cells[key(r, c)])) ? '● 开机' : '○ 关机' }}
                  </span>
                  <span v-if="net.enabled" class="net" :class="netOf(ws(cells[key(r, c)]))">{{ netText(ws(cells[key(r, c)])) }}</span>
                </div>
              </div>
              <div v-else class="seat gap" />
            </template>
          </template>
        </div>
        <div class="legend">
          每个座位：排-列、IP 末段、计算机名<template v-if="net.enabled">、开机状态、网络（🌐已开 ⛔已断 —没下发过）</template>。
          <Hint>网络状态是本平台最后一次给这台电脑下发的指令，不是实时探测；开机状态来自网络控制器。<template v-if="!net.enabled">在“系统设置 → 网络控制器”启用后，这里可以批量开网 / 断网。</template></Hint>
        </div>
        <div v-if="unplaced.length && mode !== 'edit'" class="legend warn">
          还有 {{ unplaced.length }} 台同前缀的机位没放进座位图：{{ unplaced.slice(0, 12).map(w => w.pcName).join('、') }}{{ unplaced.length > 12 ? '…' : '' }}（点“调整机位”放进去）
        </div>

        <div v-if="mode === 'manage' && selected.size" class="batchbar">
          <b>已选 {{ selected.size }} 台</b>
          <el-button size="small" :disabled="!net.enabled" @click="batchNet('cut')">暂停网络</el-button>
          <el-button size="small" type="primary" :disabled="!net.enabled" @click="batchNet('open')">恢复网络</el-button>
          <el-button v-if="selected.size === 2" size="small" @click="swapSelected">交换这两台的 IP</el-button>
          <span class="grow" />
          <el-button size="small" @click="setMode('view')">完成</el-button>
        </div>
      </template>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑机房模型' : '新增机房模型'" width="420px" append-to-body>
      <el-form :model="form" label-width="80px">
        <el-form-item label="名称"><el-input v-model="form.name" placeholder="如 一号机房" /></el-form-item>
        <el-form-item label="行数"><el-input-number v-model="form.rows" :min="1" :max="40" /></el-form-item>
        <el-form-item label="列数"><el-input-number v-model="form.cols" :min="1" :max="40" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submit">确定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="schemeVisible" :title="scheme.id ? `方案设置 · ${scheme.name}` : '按方案新建机房'" width="860px" append-to-body top="4vh">
      <el-form :model="scheme" label-width="96px">
        <div class="two">
          <el-form-item label="机房名称"><el-input v-model="scheme.name" placeholder="如 三楼机房" /></el-form-item>
          <el-form-item label="座位名前缀"><el-input v-model="scheme.prefix" placeholder="如 A 或 PC-" maxlength="12" /></el-form-item>
          <el-form-item label="编号位数">
            <el-select v-model="scheme.pad">
              <el-option :value="0" label="不补零（1、2…10）" />
              <el-option :value="2" label="两位（01、02…）" />
              <el-option :value="3" label="三位（001、002…）" />
            </el-select>
          </el-form-item>
          <el-form-item label="1号座位 IP"><el-input v-model="scheme.baseIp" placeholder="如 192.168.1.101" /></el-form-item>
          <el-form-item label="IP 方向">
            <el-radio-group v-model="scheme.step">
              <el-radio :value="1">递增（2号 = 1号+1）</el-radio>
              <el-radio :value="-1">递减</el-radio>
            </el-radio-group>
          </el-form-item>
        </div>
        <el-divider content-position="left">快速生成排版</el-divider>
        <div class="gen">
          <span>共</span><el-input-number v-model="gen.rows" :min="1" :max="40" size="small" /><span>排，每排</span>
          <el-input-number v-model="gen.perRow" :min="1" :max="40" size="small" /><span>台，每</span>
          <el-input-number v-model="gen.group" :min="0" :max="40" size="small" /><span>台留一个过道（0 = 不留）</span>
          <el-select v-model="gen.order" size="small" class="w240">
            <el-option value="tb" label="1号在第一排（靠讲台），从左往右" />
            <el-option value="tb-rl" label="1号在第一排，从右往左" />
            <el-option value="bt" label="1号在最后一排，从左往右" />
            <el-option value="bt-rl" label="1号在最后一排，从右往左" />
          </el-select>
          <el-button size="small" type="primary" plain @click="generate">按这些生成排版</el-button>
        </div>
        <el-form-item label="排版文本" class="mt">
          <el-input v-model="scheme.layoutText" type="textarea" :rows="8" class="mono"
            placeholder="1 2 3 . 4 5 6" />
        </el-form-item>
        <div><Hint block>可以直接改文本：调整个别座位号、加过道、删掉坏机位。计算机名 = 前缀 + 编号，IP = 1号 IP {{ scheme.step > 0 ? '+' : '−' }}（编号 − 1）。</Hint></div>
        <div v-if="preview.error" class="err">{{ preview.error }}</div>
        <template v-else-if="preview.rows.length">
          <div class="muted">预览：{{ preview.count }} 个座位，{{ preview.rows.length }} 排 × {{ preview.cols }} 列</div>
          <div class="podium sm">讲 台</div>
          <div class="pv" :style="{ gridTemplateColumns: `repeat(${preview.cols}, minmax(54px, 1fr))` }">
            <template v-for="(row, ri) in preview.rows" :key="'pr' + ri">
              <div v-for="ci in preview.cols" :key="`p${ri}-${ci}`" class="pv-cell" :class="{ gap: !row[ci - 1] }">
                <template v-if="row[ci - 1]">
                  <b>{{ seatName(row[ci - 1]) }}</b><small>{{ seatIp(row[ci - 1]) }}</small>
                </template>
              </div>
            </template>
          </div>
        </template>
      </el-form>
      <template #footer>
        <el-button @click="schemeVisible = false">取消</el-button>
        <el-button type="primary" :loading="schemeSaving" :disabled="!!preview.error" @click="saveScheme">保存方案</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { api, errMsg } from '../../api/client'
import Hint from '../../components/Hint.vue'

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
  selected.value = new Set()
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

// ---- 机房方案（座位名前缀 + 1号IP + 文本排版） ----
const schemeVisible = ref(false)
const schemeSaving = ref(false)
const scheme = reactive<any>({ id: null, name: '', prefix: '', pad: 2, baseIp: '', step: 1, layoutText: '' })
const gen = reactive({ rows: 6, perRow: 8, group: 4, order: 'tb' })

async function openScheme(r: Room | null) {
  Object.assign(scheme, { id: null, name: '', prefix: 'A', pad: 2, baseIp: '192.168.1.101', step: 1, layoutText: '' })
  if (r) {
    try {
      const s: any = await api.get(`/admin/lab/rooms/${r.id}/scheme`)
      if (currentId.value !== r.id) await select(r.id)
      Object.assign(scheme, {
        id: s.id, name: s.name, prefix: s.prefix ?? '', pad: s.pad ?? 2, baseIp: s.baseIp || guessBaseIp(), step: s.step || 1,
        layoutText: s.layoutText || layoutFromCells(r),
      })
    } catch (err) {
      ElMessage.error(errMsg(err))
      return
    }
  } else {
    generate()
  }
  schemeVisible.value = true
}

/** 老机房（只设了行列）没有排版文本：按格子里计算机名末尾的数字反推一个。 */
function layoutFromCells(r: Room) {
  const lines: string[] = []
  for (let i = 1; i <= r.rows; i++) {
    const row: string[] = []
    for (let j = 1; j <= r.cols; j++) {
      const w = workstations.value.find(x => x.id === cells[key(i, j)])
      const m = w ? /(\d+)$/.exec(w.pcName) : null
      row.push(m ? String(Number(m[1])) : '.')
    }
    lines.push(row.join(' '))
  }
  return lines.join('\n')
}

function guessBaseIp() {
  const w = workstations.value.find(x => /(^|\D)0*1$/.test(x.pcName))
  return w ? w.ipAddress : '192.168.1.101'
}

function generate() {
  const { rows: R, perRow: C, group: G, order } = gen
  const rtl = order.endsWith('-rl')
  const lines: string[] = []
  for (let k = 0; k < R; k++) {
    const toks: string[] = []
    for (let p = 0; p < C; p++) {
      if (G > 0 && p > 0 && p % G === 0) toks.push('.')
      toks.push(String(k * C + (rtl ? C - 1 - p : p) + 1))
    }
    lines.push(toks.join(' '))
  }
  if (order.startsWith('bt')) lines.reverse()
  scheme.layoutText = lines.join('\n')
}

const preview = computed(() => {
  const out = { rows: [] as number[][], cols: 0, count: 0, error: '' }
  const seen = new Set<number>()
  for (const line of String(scheme.layoutText || '').split(/\r?\n/)) {
    if (!line.trim()) continue
    const row: number[] = []
    for (const t of line.trim().split(/\s+/)) {
      if (t === '.' || t === '0') { row.push(0); continue }
      const n = Number(t)
      if (!Number.isInteger(n) || n < 1 || n > 999) { out.error = `排版里有无法识别的内容：${t}（座位号写数字，过道写 .）`; return out }
      if (seen.has(n)) { out.error = `座位号 ${n} 重复了`; return out }
      seen.add(n)
      row.push(n)
    }
    out.rows.push(row)
    out.cols = Math.max(out.cols, row.length)
  }
  if (out.rows.length > 40 || out.cols > 40) out.error = '最多 40 排、每排 40 个位置'
  out.count = seen.size
  if (!out.error && seen.size) {
    const bad = [...seen].find(n => { const ip = seatIp(n); return !ip || /\.(0|255)$/.test(ip) })
    if (bad) out.error = scheme.baseIp ? `座位 ${bad} 算出的 IP 不可用，请检查 1号座位 IP 和方向` : '请填写 1号座位 IP'
  }
  return out
})

function seatName(n: number) {
  return scheme.prefix + (scheme.pad ? String(n).padStart(scheme.pad, '0') : String(n))
}

function seatIp(n: number) {
  const m = /^(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})$/.exec(String(scheme.baseIp || '').trim())
  if (!m || m.slice(1).some(x => Number(x) > 255)) return ''
  const v = ((+m[1] * 256 + +m[2]) * 256 + +m[3]) * 256 + +m[4] + (n - 1) * scheme.step
  if (v < 0 || v > 0xffffffff) return ''
  return [Math.floor(v / 16777216) % 256, Math.floor(v / 65536) % 256, Math.floor(v / 256) % 256, v % 256].join('.')
}

async function saveScheme() {
  if (!scheme.name.trim()) { ElMessage.warning('请填写机房名称'); return }
  schemeSaving.value = true
  try {
    const body = { name: scheme.name, prefix: scheme.prefix, pad: scheme.pad, baseIp: scheme.baseIp, step: scheme.step, layoutText: scheme.layoutText }
    const saved: any = scheme.id ? await api.put(`/admin/lab/rooms/${scheme.id}/scheme`, body) : await api.post('/admin/lab/rooms/scheme', body)
    schemeVisible.value = false
    ElMessage.success(`方案已保存，共 ${preview.value.count} 台机位`)
    await loadRooms()
    workstations.value = await api.get('/admin/lab-workstations')
    await select(saved.id)
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    schemeSaving.value = false
  }
}

// ---- 座位图：查看 / 管理（多选、批量开断网、交换 IP）/ 调整机位 ----
const mode = ref<'view' | 'manage' | 'edit'>('view')
const selected = ref<Set<number>>(new Set())
const net = reactive<{ enabled: boolean; reachable: boolean; status: Record<string, any>; last: Record<string, boolean> }>(
  { enabled: false, reachable: false, status: {}, last: {} })

const ws = (id: number | null | undefined) => (id ? workstations.value.find(w => w.id === id) : undefined)
const ipTail = (w: any) => (w?.ipAddress ? '.' + w.ipAddress.split('.').pop() : '')
const placed = computed(() => Object.values(cells).filter((v): v is number => !!v).map(id => ws(id)).filter(Boolean) as any[])
const unplaced = computed(() => {
  const ids = new Set(placed.value.map(w => w.id))
  // 只提示和本机房同前缀的机位，别的机房的不算
  const prefix = placed.value[0]?.pcName.replace(/\d+$/, '')
  return prefix ? workstations.value.filter(w => !ids.has(w.id) && w.pcName.replace(/\d+$/, '') === prefix) : []
})
const isOnline = (w: any) => !!net.status[w?.ipAddress]?.online
const onlineCount = computed(() => placed.value.filter(isOnline).length)
function netOf(w: any): 'on' | 'off' | 'unk' {
  const v = net.last[w?.ipAddress]
  return v === true ? 'on' : v === false ? 'off' : 'unk'
}
const netText = (w: any) => ({ on: '🌐已开', off: '⛔已断', unk: '—' }[netOf(w)])
const hostOf = (w: any) => net.status[w?.ipAddress]?.hostname || w?.ipAddress || ''
const seatClass = (w: any) => ({
  sel: selected.value.has(w.id),
  on: net.reachable && isOnline(w),
  cut: netOf(w) === 'off',
})

function setMode(m: 'view' | 'manage' | 'edit') {
  mode.value = m
  selected.value = new Set()
}

async function cancelEdit() {
  if (current.value) await select(current.value.id)   // 没保存的改动丢掉，回到已保存的布局
  setMode('view')
}

function toggleSel(id: number | null) {
  if (mode.value !== 'manage' || !id) return
  const s = new Set(selected.value)
  if (s.has(id)) s.delete(id)
  else s.add(id)
  selected.value = s
}

function selectWhere(fn: (w: any) => boolean) {
  selected.value = new Set(placed.value.filter(fn).map(w => w.id))
}

async function loadNet() {
  try {
    Object.assign(net, await api.get('/admin/lab/network/status'))
  } catch {
    net.enabled = false
  }
}

async function batchNet(action: 'open' | 'cut') {
  const list = placed.value.filter(w => selected.value.has(w.id))
  const label = action === 'open' ? '恢复网络' : '暂停网络'
  try {
    await ElMessageBox.confirm(`对选中的 ${list.length} 台执行「${label}」？` + (list.length <= 8 ? `\n${list.map(w => w.pcName).join('、')}` : ''), label, { type: 'warning' })
  } catch {
    return
  }
  try {
    const r: any = await api.post('/admin/lab/network/batch', { action, ips: list.map(w => w.ipAddress) })
    if (r.failed?.length) ElMessage.warning(`已完成 ${r.count} 台，失败 ${r.failed.length} 台：${r.failed.join('、')}`)
    else ElMessage.success(`已完成 ${r.count} 台`)
    await loadNet()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

async function swapSelected() {
  const [a, b] = [...selected.value]
  const wa = ws(a), wb = ws(b)
  try {
    await ElMessageBox.confirm(`把 ${wa?.pcName}（${wa?.ipAddress}）和 ${wb?.pcName}（${wb?.ipAddress}）的 IP 互换？\n两台电脑实际位置和 IP 对不上时用。`, '交换IP', { type: 'warning' })
  } catch {
    return
  }
  try {
    await api.post('/admin/lab/workstations/swap-ip', { a, b })
    workstations.value = await api.get('/admin/lab-workstations')
    selected.value = new Set()
    ElMessage.success('已交换')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

onMounted(async () => {
  await loadRooms()
  workstations.value = await api.get('/admin/lab-workstations')
  if (rooms.value.length) await select(rooms.value[0].id)
  await loadNet()
})
</script>

<style scoped>
.page-grid { display: grid; grid-template-columns: 280px 1fr; gap: 16px; align-items: start; }
.card-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; flex-wrap: wrap; }
.card-head > span { white-space: nowrap; }
.room-item { padding: 10px 12px; border-radius: 8px; cursor: pointer; border: 1px solid transparent; margin-bottom: 6px; }
.room-item:hover { background: var(--el-fill-color-light); }
.room-item.active { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.room-name { font-weight: 600; color: var(--el-text-color-primary); }
.room-meta { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.room-ops { margin-top: 4px; }
.podium { text-align: center; background: var(--el-fill-color); border-radius: 6px; padding: 6px; margin-bottom: 12px; color: var(--el-text-color-regular); letter-spacing: 8px; }
.head-chips { display: flex; gap: 6px; }
.chip { font-size: 12px; padding: 2px 10px; border-radius: 999px; background: var(--el-fill-color); color: var(--el-text-color-regular); }
.chip.ok { background: var(--el-color-success-light-9); color: var(--el-color-success-dark-2); }
.chip.bad { background: var(--el-color-danger-light-9); color: var(--el-color-danger); }
.seatbar { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-bottom: 12px; }
.seatbar .el-button + .el-button { margin-left: 0; }
.grow { flex: 1; }
.seatgrid { display: grid; gap: 8px; padding: 14px; overflow-x: auto; background: var(--el-fill-color-lighter);
  border: 1.5px dashed var(--hb-line); border-radius: 10px; }
.seat { position: relative; min-height: 78px; padding: 5px 7px; border: 2px solid var(--el-border-color); border-radius: 10px;
  background: var(--hb-paper); font-size: 12px; user-select: none; overflow: hidden; transition: border-color .15s, box-shadow .15s; }
.seat.gap { visibility: hidden; }
.seatgrid.managing .seat:not(.gap) { cursor: pointer; }
.seatgrid.managing .seat:not(.gap):hover { border-color: var(--el-border-color-darker); }
.seat .no { display: flex; justify-content: space-between; font-family: Consolas, monospace; font-size: 11px; color: var(--el-text-color-secondary); }
.seat .nm { margin: 2px 0 1px; font-size: 15px; font-weight: 800; color: var(--hb-ink-deep); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.seat .sub { font-size: 11px; color: var(--el-text-color-secondary); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.seat .state { display: flex; justify-content: space-between; gap: 4px; margin-top: 3px; font-size: 11px; }
.seat .pw.on { color: var(--el-color-success-dark-2); }
.seat .pw.off { color: var(--el-text-color-placeholder); }
.seat .net { font-weight: 700; }
.seat .net.on { color: var(--el-color-success-dark-2); }
.seat .net.off { color: var(--el-color-danger); }
.seat .net.unk { color: var(--el-text-color-placeholder); }
.seat.on { border-color: var(--el-color-success-light-5); background: var(--el-color-success-light-9); }
.seat.cut { background: var(--el-color-danger-light-9); }
.seat.sel { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); box-shadow: 0 0 0 3px var(--el-color-primary-light-5); }
.seat.sel .nm::before { content: "✓ "; color: var(--el-color-primary); }
.legend { margin-top: 8px; font-size: 12px; line-height: 1.8; color: var(--el-text-color-secondary); }
.legend.warn { color: var(--el-color-warning-dark-2); }
.batchbar { position: sticky; bottom: 12px; z-index: 5; display: flex; gap: 8px; align-items: center; flex-wrap: wrap; margin-top: 14px;
  padding: 10px 16px; border-radius: 12px; background: var(--hb-ink-deep); color: var(--hb-paper); box-shadow: 4px 4px 0 var(--hb-line); }
.batchbar .el-button + .el-button { margin-left: 0; }
.cell { border: 1px dashed var(--el-border-color); border-radius: 6px; padding: 6px; background: var(--el-bg-color); }
.cell.filled { border-style: solid; border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.pos { font-size: 11px; color: var(--el-text-color-placeholder); margin-bottom: 4px; }
.cell-select { width: 100%; }
.ip { float: right; color: var(--el-color-primary); }
.mb { margin-bottom: 10px; }
.mt { margin-top: 10px; }
.two { display: grid; grid-template-columns: 1fr 1fr; column-gap: 12px; }
.gen { display: flex; flex-wrap: wrap; gap: 6px; align-items: center; font-size: 13px; }
.w240 { width: 240px; }
.mono :deep(textarea) { font-family: Consolas, monospace; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.err { color: var(--el-color-danger); font-size: 13px; margin-top: 6px; }
.foot-tip { margin-right: 12px; }
.podium.sm { margin: 8px 0 6px; padding: 3px; font-size: 12px; }
.pv { display: grid; gap: 4px; max-height: 300px; overflow: auto; }
.pv-cell { border: 1px solid var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); border-radius: 4px; padding: 2px; text-align: center; line-height: 1.2; }
.pv-cell b { display: block; font-size: 12px; }
.pv-cell small { font-size: 10px; color: var(--el-text-color-secondary); }
.pv-cell.gap { border: none; background: transparent; }
@media (max-width: 900px) { .page-grid { grid-template-columns: 1fr; } }
</style>
