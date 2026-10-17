<template>
  <el-dialog :model-value="modelValue" title="个人资料" width="520px" append-to-body @update:model-value="(v: boolean) => emit('update:modelValue', v)" @open="load">
    <div v-if="p" class="profile">
      <div class="left">
        <UserAvatar :user-id="p.id" :name="p.name" :size="96" />
        <template v-if="p.canChangeAvatar">
          <el-upload :show-file-list="false" accept="image/*" :http-request="upload">
            <el-button size="small" :loading="uploading">{{ p.hasAvatar ? '更换头像' : '上传头像' }}</el-button>
          </el-upload>
          <el-button v-if="p.hasAvatar" size="small" link type="danger" @click="removeAvatar">删除头像</el-button>
        </template>
        <span v-else class="muted">头像由老师统一采集</span>
      </div>
      <el-form label-width="70px" class="right">
        <el-form-item label="姓名">
          <el-input v-if="p.canChangeName" v-model="form.name" maxlength="64" />
          <span v-else>{{ p.name }}</span>
        </el-form-item>
        <el-form-item v-if="p.className" label="班级"><span>{{ p.yearLabel }} {{ p.className }}<template v-if="p.studentNo"> · {{ p.studentNo }} 号</template></span></el-form-item>
        <el-form-item v-if="p.xjh" label="学号"><span>{{ p.xjh }}</span></el-form-item>
        <el-form-item v-if="p.loginName" label="登录名"><span>{{ p.loginName }}</span></el-form-item>
        <el-form-item label="性别">
          <el-radio-group v-model="form.gender"><el-radio value="男">男</el-radio><el-radio value="女">女</el-radio><el-radio value="">不填</el-radio></el-radio-group>
        </el-form-item>
        <el-form-item label="生日">
          <el-date-picker v-model="form.birthday" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.bio" type="textarea" :rows="3" maxlength="200" show-word-limit />
        </el-form-item>
      </el-form>
    </div>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="save">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { api, errMsg } from '../api/client'
import { useAuthStore } from '../stores/auth'
import { refreshAvatars } from '../utils/avatar'
import UserAvatar from './UserAvatar.vue'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [boolean] }>()
const auth = useAuthStore()
const p = ref<any>(null)
const form = reactive<any>({ name: '', gender: '', birthday: '', bio: '' })
const saving = ref(false)
const uploading = ref(false)

function fill(d: any) {
  p.value = d
  Object.assign(form, { name: d.name, gender: d.gender || '', birthday: d.birthday ? String(d.birthday).slice(0, 10) : '', bio: d.bio || '' })
}

async function load() { fill(await api.get('/account/profile')) }

async function save() {
  saving.value = true
  try {
    fill(await api.put('/account/profile', form))
    if (auth.user && p.value.canChangeName) auth.user.displayName = p.value.name
    ElMessage.success('已保存')
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}

async function upload(o: UploadRequestOptions) {
  uploading.value = true
  try {
    fill(await api.upload('/account/avatar', o.file))
    refreshAvatars()
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    uploading.value = false
  }
}

async function removeAvatar() {
  try {
    fill(await api.del('/account/avatar'))
    refreshAvatars()
  } catch (err) {
    ElMessage.error(errMsg(err))
  }
}
</script>

<style scoped>
.profile { display: flex; gap: 20px; }
.left { display: flex; flex-direction: column; align-items: center; gap: 8px; width: 110px; }
.right { flex: 1; }
.full { width: 100%; }
.muted { color: var(--el-text-color-secondary); font-size: 12px; text-align: center; }
</style>
