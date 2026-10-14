<template>
  <el-alert v-if="error" :title="error" type="warning" :closable="false" show-icon />
  <div v-else-if="data">
    <div class="status">
      <el-tag :type="data.joinOpen ? 'success' : 'info'">{{ data.joinOpen ? '可以自由选择小组' : '自由分组未开放' }}</el-tag>
      <el-tag :type="data.voteOpen ? 'warning' : 'info'">{{ data.voteOpen ? '组长推选进行中' : '组长推选未开放' }}</el-tag>
      <span v-if="me.groupNo" class="mine">我在第 {{ me.groupNo }} 组{{ me.isLeader ? '（组长）' : '' }}</span>
      <span v-else class="muted">我还没有加入小组</span>
      <el-button v-if="data.joinOpen && me.groupNo" size="small" link type="danger" @click="join(0)">退出小组</el-button>
    </div>
    <el-alert v-if="!me.joined" title="请先进入课堂签到后再分组" type="info" :closable="false" class="mb" />
    <el-empty v-if="!data.groups.length" description="老师还没有设置小组" />

    <div class="board">
      <div v-for="g in data.groups" :key="g.groupNo" class="group" :class="{ mine: g.groupNo === me.groupNo }">
        <div class="g-head">
          <b>第 {{ g.groupNo }} 组</b>
          <span class="muted">{{ g.members.length }} 人</span>
          <el-button v-if="data.joinOpen && me.joined && g.groupNo !== me.groupNo" size="small" type="primary" plain class="join" @click="join(g.groupNo)">
            加入
          </el-button>
        </div>
        <div v-if="!g.members.length" class="muted">还没有同学</div>
        <div v-for="m in g.members" :key="m.studentId" class="member">
          <span :class="{ me: m.studentId === me.studentId }">
            <span v-if="m.studentId === g.leaderId" class="crown" title="组长">★</span>{{ m.name }}
          </span>
          <template v-if="data.voteOpen && g.groupNo === me.groupNo && m.studentId !== me.studentId">
            <el-button v-if="me.myVote === m.studentId" size="small" type="warning" link @click="vote(null)">已推选（撤回）</el-button>
            <el-button v-else size="small" link type="primary" @click="vote(m.studentId)">推选为组长</el-button>
          </template>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'
import { useLessonChannel } from '../../api/realtime'

const props = defineProps<{ lessonId: number; page?: any }>()
const base = `/student/groups/lessons/${props.lessonId}`
const data = ref<any>(null)
const error = ref('')
const me = computed(() => data.value?.me || {})
let timer: number | undefined

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
  } catch (err) {
    ElMessage.error(errMsg(err))
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
.status { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 12px; }
.mine { color: var(--el-color-primary); font-weight: 600; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.mb { margin-bottom: 12px; }
.board { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 10px; }
.group { border: 1px solid var(--el-border-color-lighter); border-radius: 6px; padding: 8px 10px; }
.group.mine { border-color: var(--el-color-primary); background: var(--el-color-primary-light-9); }
.g-head { display: flex; align-items: center; gap: 6px; margin-bottom: 6px; }
.join { margin-left: auto; }
.member { display: flex; justify-content: space-between; align-items: center; font-size: 13px; padding: 2px 0; }
.me { font-weight: 600; }
.crown { color: var(--el-color-warning); margin-right: 2px; }
</style>
