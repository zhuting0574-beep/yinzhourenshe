<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api'

const props = defineProps<{ modelValue: boolean }>()
const emit = defineEmits<{ 'update:modelValue': [value: boolean]; saved: [] }>()
const form = reactive({ currentPassword: '', newPassword: '', confirmPassword: '' })
const submitting = ref(false)
const visible = computed({ get: () => props.modelValue, set: value => emit('update:modelValue', value) })

function hasSequentialRun(password: string) {
  const value = password.toLowerCase()
  for (let i = 0; i + 2 < value.length; i++) {
    const first = value.charCodeAt(i)
    const second = value.charCodeAt(i + 1)
    const third = value.charCodeAt(i + 2)
    const isAsciiLetterOrDigit = (code: number) => code >= 48 && code <= 57 || code >= 97 && code <= 122
    if (isAsciiLetterOrDigit(first) && isAsciiLetterOrDigit(second) && isAsciiLetterOrDigit(third)
      && second - first === third - second && Math.abs(second - first) === 1) return true
  }
  return false
}

watch(visible, value => {
  if (!value) Object.assign(form, { currentPassword: '', newPassword: '', confirmPassword: '' })
})

async function submit() {
  if (form.newPassword.length < 9) return ElMessage.warning('新密码至少为9位')
  if (!/[A-Z]/.test(form.newPassword) || !/[a-z]/.test(form.newPassword) || !/[^A-Za-z0-9]/.test(form.newPassword)) {
    return ElMessage.warning('新密码必须包含大写字母、小写字母和符号')
  }
  if (hasSequentialRun(form.newPassword)) {
    return ElMessage.warning('新密码不能包含连续递增或递减的3位字母或数字')
  }
  if (form.newPassword !== form.confirmPassword) return ElMessage.warning('两次输入的新密码不一致')
  submitting.value = true
  try {
    await api.put('/admin/auth/password', { currentPassword: form.currentPassword, newPassword: form.newPassword })
    ElMessage.success('密码修改成功')
    visible.value = false
    emit('saved')
  } catch (error: any) {
    ElMessage.error(error.message)
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" title="修改登录密码" width="440px" destroy-on-close>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="当前密码"><el-input v-model="form.currentPassword" type="password" show-password autocomplete="current-password" /></el-form-item>
      <el-form-item label="新密码"><el-input v-model="form.newPassword" type="password" show-password autocomplete="new-password" @keyup.enter="submit" /></el-form-item>
      <el-form-item label="确认新密码"><el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" @keyup.enter="submit" /></el-form-item>
      <p class="password-rules">至少9位，包含大写字母、小写字母和符号；不能含连续递增或递减的3位字母或数字。</p>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="submit">确认修改</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.password-rules { margin: -4px 0 0; color: #75899b; font-size: 13px; line-height: 1.6; }
</style>
