<template>
  <div class="gp">
    <el-empty v-if="data && !data.enabled" :image-size="50" description="本节课没有设置要完成的任务" />
    <template v-else-if="data">
      <div v-if="!data.joined" class="tip">还没进课堂，先在“我的课堂”点“进入课堂”。</div>
      <div v-else-if="data.requireGroup && !data.groupNo" class="tip warn">还没进组：先到“选择小组”页加入小组。</div>
      <div v-if="data.open" class="tip ok">✔ 全部完成了</div>
      <div v-else-if="data.requireGroup && data.groupNo" class="tip">{{ data.groupLabel || `第 ${data.groupNo} 组` }}：要全组每个人都完成</div>
      <div v-if="data.requirePair && data.pair && !data.open" class="tip">要和搭档组一起完成：{{ data.pair.partners.map((p: any) => p.label).join('、') }}</div>

      <div v-if="data.groupPrograms && data.groupMissing?.length" class="group-miss">
        本组还差（组里有一个人交了、达到要求就算）：<b>{{ data.groupMissing.join('、') }}</b>
      </div>

      <div v-for="m in data.members" :key="m.name" class="mem" :class="{ me: m.me, done: m.done }">
        <div class="mh">
          <b>{{ m.name }}</b><el-tag v-if="m.me" size="small">我</el-tag>
          <span class="st" :class="m.done ? 'ok' : 'bad'">{{ m.done ? '✔ 完成' : '还没完成' }}</span>
        </div>
        <ul v-if="!m.done && m.override !== 'OPEN'">
          <li v-if="!m.quizPassed">
            {{ data.quizTitle || '课后习题' }}：
            <template v-if="m.quizTried">{{ m.quizScore ?? 0 }} 分，没达到 {{ data.passScore }} 分</template>
            <template v-else>还没做</template>
          </li>
          <li v-for="t in m.missingItems" :key="t.title">{{ t.title }}：{{ t.type === 'SURVEY' ? '还没提交' : '还没交，或者分数不够' }}</li>
        </ul>
      </div>
      <!-- 配对的搭档组：一组没完成时，另一组能看到对方还差什么 -->
      <template v-if="data.pair?.partners?.length">
        <div class="pair-title">搭档组（第 {{ data.pair.pairNo }} 对）</div>
        <div v-for="g in data.pair.partners" :key="g.groupNo" class="mem" :class="{ done: g.done }">
          <div class="mh">
            <b>{{ g.label }}</b>
            <span class="st" :class="g.done ? 'ok' : 'bad'">{{ g.done ? '✔ 完成' : g.members.length ? '还没完成' : '还没人进课' }}</span>
          </div>
          <ul v-if="!g.done">
            <li v-for="m in g.members.filter((x: any) => !x.done)" :key="m.name">{{ m.name }}：还差 {{ (m.missing || []).join('、') || '—' }}</li>
            <li v-if="g.groupMissing?.length">本组还差：{{ g.groupMissing.join('、') }}</li>
          </ul>
        </div>
      </template>
      <p class="muted">每 5 秒自动刷新</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { api } from '../../api/client'

const props = defineProps<{ lesson?: any, lessonId?: number | string }>()
const data = ref<any>(null)
let timer: any = null

async function load() {
  const id = props.lessonId ?? props.lesson?.id
  if (!id) return
  try { data.value = await api.get(`/student/lessons/${id}/net-gate/progress`) } catch { /* 下次再试 */ }
}

onMounted(() => { load(); timer = setInterval(load, 5000) })
onBeforeUnmount(() => clearInterval(timer))
</script>

<style scoped>
.tip { padding: 6px 10px; border-radius: 6px; background: var(--el-fill-color-light); margin-bottom: 8px; font-size: 14px; }
.tip.ok { background: var(--el-color-success-light-9); color: var(--el-color-success); font-weight: 700; }
.tip.warn { background: var(--el-color-warning-light-9); color: var(--el-color-warning-dark-2); }
.group-miss { padding: 6px 10px; border: 1px dashed var(--el-color-warning); border-radius: 6px; margin-bottom: 8px; font-size: 14px; }
.mem { border: 1px solid var(--el-border-color-lighter); border-radius: 8px; padding: 6px 10px; margin-bottom: 6px; }
.mem.me { border-color: var(--el-color-primary-light-5); background: var(--el-color-primary-light-9); }
.mh { display: flex; gap: 6px; align-items: center; }
.st { margin-left: auto; font-size: 13px; }
.st.ok { color: var(--el-color-success); }
.st.bad { color: var(--el-color-danger); }
ul { margin: 4px 0 0; padding-left: 18px; font-size: 13px; color: var(--el-text-color-regular); }
.muted { color: var(--el-text-color-secondary); font-size: 12px; }
.pair-title { margin: 12px 0 6px; font-weight: 700; color: var(--el-text-color-regular); }
</style>
