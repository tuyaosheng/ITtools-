<template>
  <el-dialog :model-value="modelValue" title="修改密码" width="400px" append-to-body
    @update:model-value="(v: boolean) => emit('update:modelValue', v)">
    <el-form :model="form" label-width="80px">
      <el-form-item label="原密码"><el-input v-model="form.oldPassword" type="password" show-password /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password /></el-form-item>
      <el-form-item label="确认密码"><el-input v-model="form.confirm" type="password" show-password /></el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">确定</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { api, errMsg } from '../api/client'

defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ (e: 'update:modelValue', v: boolean): void }>()
const form = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const saving = ref(false)

async function submit() {
  if (form.newPassword !== form.confirm) {
    ElMessage.error('两次输入的新密码不一致')
    return
  }
  saving.value = true
  try {
    await api.post('/account/password', { oldPassword: form.oldPassword, newPassword: form.newPassword })
    ElMessage.success('密码已修改')
    Object.assign(form, { oldPassword: '', newPassword: '', confirm: '' })
    emit('update:modelValue', false)
  } catch (err) {
    ElMessage.error(errMsg(err))
  } finally {
    saving.value = false
  }
}
</script>
