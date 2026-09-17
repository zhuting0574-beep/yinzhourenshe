<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import '@wangeditor/editor/dist/css/style.css'

const props = defineProps<{ modelValue?: string | null }>()
const emit = defineEmits<{ (event: 'update:modelValue', value: string): void }>()
const editorRef = shallowRef<IDomEditor>()
const editorValue = computed(() => props.modelValue || '<p><br></p>')
const toolbarConfig: Partial<IToolbarConfig> = {}
const editorConfig: Partial<IEditorConfig> = { placeholder: '输入图文详情，可直接粘贴图片' }

editorConfig.MENU_CONF = {
  uploadImage: {
    maxFileSize: 10 * 1024 * 1024,
    allowedFileTypes: ['image/*'],
    async customUpload(file: File, insertFn: (url: string, alt?: string, href?: string) => void) {
      const form = new FormData()
      form.append('file', file)
      const response = await fetch('/api/admin/uploads', {
        method: 'POST',
        headers: { Authorization: `Bearer ${localStorage.getItem('adminToken') || ''}` },
        body: form
      })
      const result = await response.json()
      if (!response.ok || !result.success) throw new Error(result.message || '图片上传失败')
      insertFn(result.data.url, file.name)
    }
  }
}

function created(editor: IDomEditor) { editorRef.value = editor }
onBeforeUnmount(() => editorRef.value?.destroy())
</script>

<template>
  <div class="rich-editor">
    <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" />
    <Editor :model-value="editorValue" :default-config="editorConfig" mode="default" @on-created="created" @update:model-value="value => emit('update:modelValue', value)" />
  </div>
</template>

<style scoped>
.rich-editor { width: 100%; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; background: #fff; }
.rich-editor :deep(.w-e-toolbar) { border-bottom: 1px solid #ebeef5; }
.rich-editor :deep(.w-e-text-container) { min-height: 300px; }
</style>
