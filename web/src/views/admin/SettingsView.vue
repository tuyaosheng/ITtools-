<template>
  <el-card shadow="never">
    <template #header><span>系统参数设置</span></template>
    <el-form v-loading="loading" :model="form" label-width="140px" class="form">
      <el-divider content-position="left">基本</el-divider>
      <el-form-item label="学校名称">
        <el-input v-model="form['school.name']" />
      </el-form-item>
      <el-form-item label="工作模式">
        <el-radio-group v-model="form['login.mode']">
          <el-radio value="NORMAL">正常教学模式</el-radio>
          <el-radio value="OPEN_CLASS">公开课模式</el-radio>
          <el-radio value="XJH">学籍+密码模式</el-radio>
          <el-radio value="FIXED_SEAT">班级+姓名+固定机位模式</el-radio>
        </el-radio-group>
        <Hint>正常：班级+姓名+密码登录；公开课：学生登录后直接进入当前课堂；学籍模式：仅显示学籍号登录；
          固定机位：班级+姓名登录，已启用固定机位的班级须在绑定机位登录（在“固定机位”中配置）。</Hint>
      </el-form-item>

      <el-divider content-position="left">文件</el-divider>
      <el-form-item label="禁止上传的扩展名">
        <el-input v-model="form['upload.blockedExtensions']" placeholder="逗号分隔，如 exe,bat" />
      </el-form-item>
      <el-form-item label="单文件上限 (MB)">
        <el-input-number v-model="uploadMax" :min="1" :max="200" />
      </el-form-item>
      <el-form-item label="学生网盘容量 (MB)">
        <el-input-number v-model="diskQuota" :min="1" :max="10240" />
      </el-form-item>

      <el-form-item label="学生单处登录">
        <el-switch v-model="singleLogin" />
      </el-form-item>
      <el-divider content-position="left">教学</el-divider>
      <el-form-item label="允许作业补交">
        <el-switch v-model="allowLate" />
      </el-form-item>
      <el-form-item label="敏感词过滤">
        <el-input v-model="form['interact.sensitiveWords']" type="textarea" :rows="3"
          placeholder="多个用逗号分隔" />
      </el-form-item>

      <el-divider content-position="left">登录页与学生权限</el-divider>
      <el-form-item label="登录页公告">
        <el-input v-model="form['site.notice']" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="显示在登录页" />
      </el-form-item>
      <el-form-item label="学生改密码">
        <el-switch v-model="studentPassword" />
      </el-form-item>
      <el-form-item label="学生传头像">
        <el-switch v-model="studentAvatar" />
      </el-form-item>

      <el-divider content-position="left">第三方程序登录</el-divider>
      <el-form-item label="开启">
        <el-switch v-model="ssoEnabled" />
      </el-form-item>
      <el-form-item label="签名密钥">
        <el-input :model-value="form['sso.secret'] || '（未生成）'" readonly class="w420">
          <template #append><el-button @click="newSecret">生成新密钥</el-button></template>
        </el-input>
      </el-form-item>
      <el-form-item label="接入说明">
        <Hint>
          链接格式：<code>{{ origin }}/api/auth/sso?u=学籍号&amp;t=当前Unix秒&amp;s=签名[&amp;r=/student/页面]</code><br>
          签名：<code>s = HMAC-SHA256(密钥, 学籍号 + "|" + t)</code> 的小写十六进制。链接 5 分钟内有效，只能使用一次；登录后进入学生首页（或 r 指定的学生页面）。<br>
          密钥只保存在服务器和第三方系统中，不要放在网页里；生成新密钥后旧密钥立即失效。
        </Hint>
      </el-form-item>

      <el-divider content-position="left">网络控制器（CTR）</el-divider>
      <el-form-item label="启用">
        <el-switch v-model="netEnabled" />
      </el-form-item>
      <el-form-item label="控制器地址">
        <el-input v-model="form['net.ctrUrl']" placeholder="http://127.0.0.1:8770" class="w420" />
        <Hint>网络控制器主控端的 HTTP 接口地址。本平台与控制器在同一台电脑时用 127.0.0.1；不在同一台时，控制器的 api_host 要设为 0.0.0.0，这里填教师机 IP。</Hint>
      </el-form-item>
      <el-form-item label="访问令牌">
        <el-input v-model="form['net.ctrToken']" type="password" show-password placeholder="未设置可留空" class="w420" />
      </el-form-item>
      <el-form-item label="测试">
        <el-button :loading="testing" @click="testNet">保存并测试连接</el-button>
        <span v-if="netResult" class="tip inline" :class="netResult.ok ? 'ok' : 'bad'">
          {{ netResult.ok ? `连接成功：${netResult.agents} 台被控端，在线 ${netResult.online} 台` : (netResult.error || '连接失败') }}
        </span>
      </el-form-item>

      <el-form-item>
        <el-button type="primary" :loading="saving" @click="save">保存设置</el-button>
      </el-form-item>
    </el-form>
  </el-card>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../../api/client'

const form = reactive<Record<string, string>>({})
const loading = ref(false)
const saving = ref(false)

const num = (k: string) => computed({
  get: () => Number(form[k] || 0),
  set: (v: number) => { form[k] = String(v ?? '') }
})
const uploadMax = num('upload.maxMb')
const diskQuota = num('disk.quotaMb')
const singleLogin = computed({
  get: () => form['login.singleSession'] === 'true',
  set: (v: boolean) => { form['login.singleSession'] = String(v) }
})
const bool = (k: string) => computed({
  get: () => form[k] === 'true',
  set: (v: boolean) => { form[k] = String(v) }
})
const studentPassword = bool('student.allowPasswordChange')
const studentAvatar = bool('student.allowAvatarChange')
const ssoEnabled = bool('sso.enabled')
const origin = window.location.origin
const netEnabled = bool('net.enabled')
const testing = ref(false)
const netResult = ref<any>(null)

async function testNet() {
  testing.value = true
  netResult.value = null
  try {
    Object.assign(form, await api.put('/admin/settings', { ...form }))
    netResult.value = await api.get('/admin/lab/network/test')
  } catch (err) {
    netResult.value = { ok: false, error: errMsg(err) }
  } finally {
    testing.value = false
  }
}

async function newSecret() {
  try {
    const r = await api.post('/admin/settings/sso-secret')
    form['sso.secret'] = r.secret
    ElMessage.success('已生成新密钥')
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}

const allowLate = computed({
  get: () => form['homework.allowLate'] === 'true',
  set: (v: boolean) => { form['homework.allowLate'] = String(v) }
})

async function load() {
  loading.value = true
  try {
    Object.assign(form, await api.get('/admin/settings'))
  } finally {
    loading.value = false
  }
}

async function save() {
  saving.value = true
  try {
    Object.assign(form, await api.put('/admin/settings', { ...form }))
    ElMessage.success('设置已保存')
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.form { max-width: 820px; }
.tip { font-size: 12px; color: var(--el-text-color-secondary); line-height: 1.6; margin-top: 4px; }
.tip.inline { margin: 0 0 0 10px; }
.w420 { width: 420px; }
.ok { color: var(--el-color-success); }
.bad { color: var(--el-color-danger); }
.doc { font-size: 12px; line-height: 1.8; color: var(--el-text-color-regular); }
.doc code { background: var(--el-fill-color-light); padding: 1px 4px; border-radius: 3px; word-break: break-all; }
</style>
