<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <div v-else-if="data" class="pick">
    <div class="hero" :class="{ open: data.joinOpen }">
      <div class="hero-ic">{{ me.groupNo ? me.groupNo : '?' }}</div>
      <div class="hero-text">
        <div class="hero-title">
          <template v-if="me.groupNo">我在{{ me.groupLabel || `第 ${me.groupNo} 组` }}{{ me.isLeader ? ' · 组长 ★' : '' }}</template>
          <template v-else>我还没有小组</template>
        </div>
        <div class="hero-sub">
          <template v-if="!me.joined">先进入课堂签到，才能选组</template>
          <template v-else-if="!data.joinOpen">{{ me.groupNo ? '选组已关闭，小组已确定' : '老师还没有开放自由选组，等一等' }}</template>
          <template v-else-if="data.locked && me.groupNo">本节课开网要全组完成，进组后不能自己换组，需要调整请找老师</template>
          <template v-else-if="me.groupNo">选组开放中，想换组就点别的组的「换到这组」</template>
          <template v-else>选组开放中，挑一个小组加入吧{{ data.maxSize ? `（每组最多 ${data.maxSize} 人）` : '' }}</template>
        </div>
      </div>
      <el-button v-if="canMove && me.groupNo" round @click="join(0)">退出小组</el-button>
    </div>

    <!-- 整组选角色和配对号（组里谁选都算全组的） -->
    <div v-if="data.roleOpen && me.groupNo" class="role-pick">
      <div class="rp-title">我们组做：<b>{{ myGroup?.role ? myGroup.label : '还没选' }}</b></div>
      <div class="rp-row">
        <el-radio-group v-model="rolePick.role">
          <el-radio-button v-for="r in data.roles" :key="r" :value="r">{{ r }}组</el-radio-button>
        </el-radio-group>
        <span>第</span>
        <el-select v-model="rolePick.pairNo" class="rp-no" placeholder="号">
          <el-option v-for="n in data.pairCount" :key="n" :value="n" :label="String(n)" :disabled="!!takenBy(rolePick.role, n)">
            {{ n }}<span v-if="takenBy(rolePick.role, n)" class="muted">（{{ takenBy(rolePick.role, n) }}已选）</span>
          </el-option>
        </el-select>
        <span>号</span>
        <el-button type="primary" :disabled="!rolePick.role || !rolePick.pairNo" @click="pickRole">确定</el-button>
        <el-button v-if="myGroup?.role" link @click="pickRole(true)">撤销</el-button>
      </div>
      <div class="muted">商量好再选：组里任何一个人选了，全组都是这个角色；同一角色同一个号只能一个组选。{{ data.roles.join('组和') }}组同一个号是一对，要一起配合。</div>
    </div>

    <div v-if="data.voteOpen" class="vote-tip">🗳 组长推选进行中：在自己组里点「推选」投给一位同学（每人一票，可以改）</div>
    <el-empty v-if="!data.groups.length" description="老师还没有设置小组" :image-size="80" />

    <div class="board">
      <div v-for="g in data.groups" :key="g.groupNo" class="group" :class="{ mine: g.groupNo === me.groupNo, full: g.full && g.groupNo !== me.groupNo }"
        :style="{ '--gc': groupColor(g.groupNo) }">
        <div class="g-head">
          <span class="g-no">{{ g.groupNo }}</span>
          <b>{{ g.label || `第 ${g.groupNo} 组` }}</b>
          <span v-if="g.groupNo === me.groupNo" class="me-badge">我的组</span>
          <span class="grow" />
          <span class="g-count">{{ g.members.length }}{{ data.maxSize ? ` / ${data.maxSize}` : '' }}</span>
        </div>
        <div v-if="data.maxSize" class="seats">
          <i v-for="n in data.maxSize" :key="n" :class="{ on: n <= g.members.length }" />
        </div>
        <div class="members">
          <span v-if="!g.members.length" class="muted">{{ canMove ? '还没有同学，来当第一个吧' : '还没有同学' }}</span>
          <div v-for="m in g.members" :key="m.studentId" class="member" :class="{ me: m.studentId === me.studentId }">
            <span class="ava">{{ (m.name || '?').slice(-1) }}</span>
            <span class="nm"><span v-if="m.studentId === g.leaderId" class="crown" title="组长">★</span>{{ m.name }}</span>
            <template v-if="data.voteOpen && g.groupNo === me.groupNo && m.studentId !== me.studentId">
              <el-button v-if="me.myVote === m.studentId" size="small" type="warning" link @click="vote(null)">已推选 ✓</el-button>
              <el-button v-else size="small" link type="primary" @click="vote(m.studentId)">推选</el-button>
            </template>
          </div>
        </div>
        <el-button v-if="canMove && g.groupNo !== me.groupNo" class="join" :type="g.full ? 'info' : 'primary'" :plain="!!me.groupNo"
          :disabled="g.full" @click="join(g.groupNo)">
          {{ g.full ? '已满' : me.groupNo ? '换到这组' : '加入这组' }}
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'
import { groupColor } from './groupColors'

const props = defineProps<{ lessonId: number; page?: any }>()
const base = `/student/groups/lessons/${props.lessonId}`
const data = ref<any>(null)
const error = ref('')
const me = computed(() => data.value?.me || {})
// 开放中、已签到、且（没进组 或 没被开网规则锁定）时可以加入 / 换组 / 退出
const canMove = computed(() => data.value?.joinOpen && me.value.joined && !(data.value.locked && me.value.groupNo))
const myGroup = computed(() => data.value?.groups.find((g: any) => g.groupNo === me.value.groupNo))
const rolePick = ref<{ role: string; pairNo?: number }>({ role: '' })
const takenBy = (role: string, n: number) =>
  data.value?.groups.find((g: any) => g.role === role && g.pairNo === n && g.groupNo !== me.value.groupNo)?.label || ''
let timer: number | undefined

async function pickRole(undo = false) {
  try {
    data.value = await api.post(`${base}/role`, undo === true ? { role: null } : rolePick.value)
    ElMessage.success(undo === true ? '已撤销' : `我们组是${myGroup.value?.label}`)
  } catch (err) {
    ElMessage.error(errMsg(err))
    load()
  }
}

async function load() {
  try {
    data.value = await api.get(base)
    error.value = ''
  } catch (err) {
    error.value = errMsg(err, '加载分组失败')
  }
}

async function join(groupNo: number) {
  try {
    data.value = await api.post(`${base}/join`, { groupNo })
    ElMessage.success(groupNo ? `已加入${data.value.groups.find((g: any) => g.groupNo === groupNo)?.label || `第 ${groupNo} 组`}` : '已退出小组')
  } catch (err) {
    ElMessage.error(errMsg(err))
    load()
  }
}

async function vote(candidateId: number | null) {
  try {
    data.value = await api.post(`${base}/vote`, { candidateId })
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

useLessonChannel(props.lessonId, false, e => {
  if (e.type === 'GROUPS_CHANGED') {
    window.clearTimeout(timer)
    timer = window.setTimeout(load, 300)
  }
})
onMounted(load)
</script>

<style scoped>
.pick { display: flex; flex-direction: column; gap: 12px; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.grow { flex: 1; }

.hero { display: flex; align-items: center; gap: 14px; padding: 14px 16px; border-radius: 14px; border: 2px solid var(--hb-ink);
  background: var(--el-fill-color-light); box-shadow: 4px 4px 0 var(--hb-line); flex-wrap: wrap; }
.hero.open { background: #fff4d6; }
.hero-ic { width: 52px; height: 52px; border-radius: 50%; display: grid; place-items: center; font-size: 26px; font-weight: 900;
  background: var(--el-color-primary); color: #fff; border: 2px solid var(--hb-ink); transform: rotate(-6deg); flex: none; }
.hero-text { flex: 1; min-width: 180px; }
.hero-title { font-family: var(--hb-title-font); font-size: 22px; color: var(--hb-ink-deep); }
.hero-sub { font-size: 13px; color: var(--el-text-color-secondary); margin-top: 2px; }
.role-pick { padding: 12px 14px; border-radius: 12px; border: 2px dashed var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); display: flex; flex-direction: column; gap: 8px; }
.rp-title { font-size: 15px; }
.rp-row { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.rp-no { width: 110px; }
.vote-tip { font-size: 13px; padding: 8px 12px; border-radius: 10px; background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }

.board { display: grid; grid-template-columns: repeat(auto-fill, minmax(210px, 1fr)); gap: 14px; }
.group { display: flex; flex-direction: column; gap: 8px; padding: 12px; border-radius: 14px; background: var(--hb-paper);
  border: 2px solid var(--hb-line); border-top: 6px solid var(--gc); transition: transform .15s, box-shadow .15s; }
.group:hover { transform: translateY(-2px); box-shadow: 3px 3px 0 var(--hb-line); }
.group.mine { border-color: var(--gc); box-shadow: 4px 4px 0 var(--gc); }
.group.full { opacity: .7; }
.g-head { display: flex; align-items: center; gap: 8px; }
.g-no { width: 28px; height: 28px; border-radius: 50%; display: grid; place-items: center; background: var(--gc); color: #fff; font-weight: 900; }
.me-badge { font-size: 11px; padding: 1px 8px; border-radius: 999px; background: var(--gc); color: #fff; }
.g-count { font-size: 12px; color: var(--el-text-color-secondary); font-weight: 700; }
.seats { display: flex; gap: 4px; flex-wrap: wrap; }
.seats i { width: 14px; height: 14px; border-radius: 50%; border: 1.5px dashed var(--el-border-color-darker); }
.seats i.on { background: var(--gc); border: 1.5px solid var(--gc); }
.members { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.member { display: flex; align-items: center; gap: 8px; font-size: 14px; }
.member.me .nm { font-weight: 800; color: var(--hb-ink-deep); }
.ava { width: 24px; height: 24px; border-radius: 50%; display: grid; place-items: center; font-size: 12px; background: var(--el-fill-color); color: var(--hb-ink); flex: none; }
.member.me .ava { background: var(--gc); color: #fff; }
.nm { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.crown { color: var(--el-color-warning); margin-right: 2px; }
.join { width: 100%; font-weight: 700; }
@media (max-width: 600px) { .board { grid-template-columns: 1fr; } }
</style>
