import { readonly, ref } from 'vue'
import { ElLoading } from 'element-plus'

const uploading = ref(false)
export const imageUploading = readonly(uploading)
const UPLOAD_TIMEOUT_MS = 30_000

function blockKeyboard(event: KeyboardEvent) {
  event.preventDefault()
  event.stopImmediatePropagation()
}

export async function uploadAdminImage(file: File) {
  if (uploading.value) throw new Error('已有图片正在上传，请稍候')
  uploading.value = true
  const controller = new AbortController()
  const loading = ElLoading.service({ fullscreen: true, lock: true, text: '图片上传中，请稍候...' })
  document.addEventListener('keydown', blockKeyboard, true)
  let timedOut = false
  const timer = window.setTimeout(() => {
    timedOut = true
    controller.abort()
  }, UPLOAD_TIMEOUT_MS)
  try {
    const form = new FormData()
    form.append('file', file)
    const response = await fetch('/api/admin/uploads', {
      method: 'POST',
      headers: { Authorization: `Bearer ${localStorage.getItem('adminToken') || ''}` },
      body: form,
      signal: controller.signal
    })
    const result = await response.json()
    if (!response.ok || !result.success || !result.data?.url) {
      throw new Error(result.message || '图片上传失败')
    }
    return result
  } catch (error) {
    if (timedOut) throw new Error('图片上传超时，请重试')
    throw error
  } finally {
    window.clearTimeout(timer)
    document.removeEventListener('keydown', blockKeyboard, true)
    uploading.value = false
    loading.close()
  }
}
