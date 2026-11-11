<script setup lang="ts">
import { computed, onBeforeUnmount, shallowRef } from 'vue'
import { Editor, Toolbar } from '@wangeditor/editor-for-vue'
import type { IDomEditor, IEditorConfig, IToolbarConfig } from '@wangeditor/editor'
import { ElMessage } from 'element-plus'
import { uploadAdminImage } from '../utils/imageUpload'
import '@wangeditor/editor/dist/css/style.css'

const props = defineProps<{
  modelValue?: string | null
  uploadPurpose?: string
}>()
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
      try {
        const result = await uploadAdminImage(file, props.uploadPurpose || 'standard')
        insertFn(result.data.url, file.name)
      } catch (error: any) {
        ElMessage.error(error.message || '图片上传失败')
      }
    }
  }
}

function created(editor: IDomEditor) { editorRef.value = editor }
onBeforeUnmount(() => editorRef.value?.destroy())
</script>

<template>
  <div class="rich-editor">
    <p class="rich-editor__tip">图文插图建议宽度：1200 px，高度和比例可按内容需要调整</p>
    <Toolbar :editor="editorRef" :default-config="toolbarConfig" mode="default" />
    <Editor :model-value="editorValue" :default-config="editorConfig" mode="default" @on-created="created" @update:model-value="value => emit('update:modelValue', value)" />
  </div>
</template>

<style scoped>
.rich-editor { width: 100%; border: 1px solid #dcdfe6; border-radius: 6px; overflow: hidden; background: #fff; }
.rich-editor__tip { margin: 0; padding: 8px 12px; color: #8796a5; background: #fafcff; font-size: 12px; line-height: 1.5; }
.rich-editor :deep(.w-e-toolbar) { border-bottom: 1px solid #ebeef5; }
.rich-editor :deep(.w-e-text-container) { min-height: 300px; }
.rich-editor :deep(.w-e-text-container img) { display: block; max-width: 100%; height: auto; margin: 0; padding: 0; box-sizing: border-box; border: 0; vertical-align: top; }
.rich-editor :deep(.w-e-text-container p:has(> img)) { margin: 0; padding: 0; }
</style>
