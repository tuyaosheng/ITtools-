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
        <div class="tip">
          正常：班级+姓名+密码登录；公开课：学生登录后直接进入当前课堂；学籍模式：仅显示学籍号登录；
          固定机位：班级+姓名登录，已启用固定机位的班级须在绑定机位登录（在“固定机位”中配置）。
        </div>
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
        <span class="tip inline">开启后同一学生账号在另一台电脑登录时，原电脑自动下线</span>
      </el-form-item>
      <el-divider content-position="left">教学</el-divider>
      <el-form-item label="允许作业补交">
        <el-switch v-model="allowLate" />
        <span class="tip inline">关闭后，课堂结束的作业不再接收提交</span>
      </el-form-item>
      <el-form-item label="敏感词过滤">
        <el-input v-model="form['interact.sensitiveWords']" type="textarea" :rows="3"
          placeholder="讨论区、论坛中出现的敏感词将被替换为 *，多个用逗号分隔" />
      </el-form-item>

      <el-divider content-position="left">登录页与学生权限</el-divider>
      <el-form-item label="登录页公告">
        <el-input v-model="form['site.notice']" type="textarea" :rows="3" maxlength="1000" show-word-limit placeholder="显示在登录页，如机房使用须知、本周安排（留空不显示）" />
      </el-form-item>
      <el-form-item label="学生改密码">
        <el-switch v-model="studentPassword" />
        <span class="tip inline">关闭后学生不能自行修改密码，由老师或管理员重置</span>
      </el-form-item>
      <el-form-item label="学生传头像">
        <el-switch v-model="studentAvatar" />
        <span class="tip inline">默认关闭：学生头像由管理员或老师统一采集导入</span>
      </el-form-item>

      <el-divider content-position="left">第三方程序登录</el-divider>
      <el-form-item label="开启">
        <el-switch v-model="ssoEnabled" />
        <span class="tip inline">允许学校的其他系统通过签名链接直接登录学生界面（原 weblogin 接口）</span>
      </el-form-item>
      <el-form-item label="签名密钥">
        <el-input :model-value="form['sso.secret'] || '（未生成）'" readonly class="w420">
          <template #append><el-button @click="newSecret">生成新密钥</el-button></template>
        </el-input>
      </el-form-item>
      <el-form-item label="接入说明">
        <div class="doc">
          链接格式：<code>{{ origin }}/api/auth/sso?u=学籍号&amp;t=当前Unix秒&amp;s=签名[&amp;r=/student/页面]</code><br>
          签名：<code>s = HMAC-SHA256(密钥, 学籍号 + "|" + t)</code> 的小写十六进制。链接 5 分钟内有效，只能使用一次；登录后进入学生首页（或 r 指定的学生页面）。<br>
          密钥只保存在服务器和第三方系统中，不要放在网页里；生成新密钥后旧密钥立即失效。
        </div>
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

async function newSecret() {
  try {
    const r = await api.post('/admin/settings/sso-secret')
    form['sso.secret'] = r.secret
    ElMessage.success('已生成新密钥，请同步到第三方系统')
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
.tip { font-size: 12px; color: #909399; line-height: 1.6; margin-top: 4px; }
.tip.inline { margin: 0 0 0 10px; }
.w420 { width: 420px; }
.doc { font-size: 12px; line-height: 1.8; color: var(--el-text-color-regular); }
.doc code { background: var(--el-fill-color-light); padding: 1px 4px; border-radius: 3px; word-break: break-all; }
</style>
