<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api'
import { useSession } from '../stores/session'

const props = defineProps<{ module: string }>()
const router = useRouter()
const session = useSession()
const loading = ref(false)
const rows = ref<any[]>([])
const dashboard = ref<any>({})
const dialog = ref(false)
const dialogMode = ref('')
const editing = reactive<any>({})
const search = ref('')
const qr = ref('')
const qrVersion = ref(Date.now())
let qrTimer: number | undefined

const menus = [
  ['dashboard', '数据概览'], ['users', '用户管理'], ['activities', '活动管理'],
  ['questionnaires', '问卷管理'], ['products', '商品与库存'], ['orders', '兑换订单'],
  ['points', '积分明细'], ['content', '展示内容']
]
const title = computed(() => menus.find(x => x[0] === props.module)?.[1] || '工作台')
const columns = computed(() => {
  const preferred: Record<string, string[]> = {
    users: ['id', 'nickname', 'phone', 'points', 'status', 'created_at'],
    activities: ['id', 'title', 'signup_start', 'start_time', 'status', 'registrations', 'checkins'],
    questionnaires: ['id', 'name', 'start_time', 'end_time', 'status', 'pinned', 'submissions'],
    products: ['id', 'name', 'points_cost', 'stock', 'locked_stock', 'redeemed_stock', 'status'],
    orders: ['order_no', 'product_name', 'nickname', 'phone', 'points', 'expire_at', 'status'],
    points: ['nickname', 'phone', 'amount', 'balance', 'type', 'remark', 'created_at'],
    content: ['content_type', 'title', 'sort_order', 'enabled', 'updated_at']
  }
  return preferred[props.module] || Object.keys(rows.value[0] || {})
})
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('adminToken') || ''}` }))

async function load() {
  loading.value = true
  try {
    if (props.module === 'dashboard') dashboard.value = await api.get('/admin/dashboard')
    else rows.value = await api.get(`/admin/${props.module}`, { params: props.module === 'users' ? { phone: search.value } : {} })
  } catch (e: any) { ElMessage.error(e.message) } finally { loading.value = false }
}
function go(key: string) { router.push('/' + key) }
function resetEditing() { Object.keys(editing).forEach(k => delete editing[k]) }
function create() {
  resetEditing()
  Object.assign(editing, { status: 'DRAFT', enabled: true, sortOrder: 0 })
  if (props.module === 'questionnaires') editing.questions = []
  if (props.module === 'products') Object.assign(editing, { status: 'ON_SALE', stock: 0, pointsCost: 1 })
  dialogMode.value = 'create'
  dialog.value = true
}
async function edit(row: any) {
  resetEditing()
  let source = row
  if (props.module === 'questionnaires') source = await api.get(`/admin/questionnaires/${row.id}`)
  Object.assign(editing, source, {
    coverUrl: source.cover_url, signupStart: source.signup_start, signupEnd: source.signup_end,
    startTime: source.start_time, endTime: source.end_time, placeName: source.place_name,
    pointsCost: source.points_cost, imageUrl: source.image_url, contentType: source.content_type,
    sortOrder: source.sort_order
  })
  if (source.questions) editing.questions = source.questions.map((q: any) => ({
    ...q, questionType: q.question_type, options: typeof q.options_json === 'string' ? JSON.parse(q.options_json) : (q.options || [])
  }))
  dialogMode.value = 'edit'
  dialog.value = true
}
async function save() {
  try {
    const body = { ...editing }
    if (props.module === 'activities') body.points = 100
    if (props.module === 'questionnaires') body.rewardPoints = 100
    if (dialogMode.value === 'create') await api.post(`/admin/${props.module}`, body)
    else await api.put(`/admin/${props.module}/${editing.id}`, body)
    dialog.value = false
    ElMessage.success('保存成功')
    load()
  } catch (e: any) { ElMessage.error(e.message) }
}
async function adjust(row: any, type: 'points' | 'stock') {
  try {
    const { value } = await ElMessageBox.prompt(type === 'points' ? '输入积分调整值，负数为扣减' : '输入库存调整值，负数为减少', '调整', { inputPattern: /^-?\d+$/, inputErrorMessage: '请输入整数' })
    await api.post(type === 'points' ? `/admin/users/${row.id}/points` : `/admin/products/${row.id}/stock`, { delta: Number(value), reason: type === 'points' ? '管理员调整' : '库存调整' })
    ElMessage.success('调整成功')
    load()
  } catch (e: any) { if (!['cancel', 'close'].includes(e) && e?.message) ElMessage.error(e.message) }
}
async function orderAction(row: any, action: 'pickup' | 'cancel') {
  try {
    let body = {}
    if (action === 'cancel') {
      const { value } = await ElMessageBox.prompt('请输入取消原因', '取消订单', { inputPattern: /.+/, inputErrorMessage: '必须填写原因' })
      body = { reason: value }
    }
    await api.post(`/admin/orders/${row.id}/${action}`, body)
    ElMessage.success('处理成功')
    load()
  } catch (e: any) { if (!['cancel', 'close'].includes(e) && e?.message) ElMessage.error(e.message) }
}
async function showQr(row: any) {
  resetEditing()
  Object.assign(editing, row)
  const data: any = await api.get(`/admin/activities/${row.id}/qr`)
  qr.value = data.token
  qrVersion.value = Date.now()
  dialogMode.value = 'qr'
  dialog.value = true
  window.clearInterval(qrTimer)
  qrTimer = window.setInterval(async () => {
    if (!dialog.value) return
    const next: any = await api.get(`/admin/activities/${row.id}/qr`)
    qr.value = next.token
    qrVersion.value = Date.now()
  }, 60000)
}
function closeDialog() { window.clearInterval(qrTimer) }
async function exportData() {
  const resource = props.module === 'activities' ? 'participants' : props.module === 'questionnaires' ? 'responses' : 'points'
  const response = await fetch(`/api/admin/exports/${resource}`, { headers: uploadHeaders.value })
  if (!response.ok) return ElMessage.error('导出失败')
  const blob = await response.blob(), url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = `${resource}.xlsx`; a.click(); URL.revokeObjectURL(url)
}
function addQuestion() { (editing.questions || (editing.questions = [])).push({ questionType: 'SINGLE', title: '', options: ['选项一', '选项二'], required: true }) }
function removeQuestion(i: number) { editing.questions.splice(i, 1) }
function addOption(question: any) { (question.options || (question.options = [])).push('新选项') }
function uploadSuccess(result: any) {
  const url = result.data?.url || result.url
  if (props.module === 'content') editing.imageUrl = url
  else editing.coverUrl = url
}
watch(() => props.module, load)
onMounted(load)
onBeforeUnmount(() => window.clearInterval(qrTimer))
</script>

<template>
  <el-container class="admin">
    <el-aside width="238px">
      <div class="logo"><span>π</span><div><b>鄞领π</b><small>运营管理平台</small></div></div>
      <el-menu :default-active="module" @select="go"><el-menu-item v-for="m in menus" :key="m[0]" :index="m[0]">{{ m[1] }}</el-menu-item></el-menu>
      <div class="operator">{{ session.username }}<el-button link @click="session.logout">退出</el-button></div>
    </el-aside>
    <el-container>
      <el-header><span>工作台 / <b>{{ title }}</b></span><span>{{ new Date().toLocaleDateString() }}</span></el-header>
      <el-main v-loading="loading">
        <div class="heading"><div><h1>{{ title }}</h1><p>鄞领π业务数据与运营操作</p></div><div class="toolbar">
          <el-input v-if="module === 'users'" v-model="search" placeholder="手机号精确查询" clearable @keyup.enter="load" />
          <el-button v-if="['activities','questionnaires','points'].includes(module)" @click="exportData">导出 Excel</el-button>
          <el-button v-if="['activities','questionnaires','products','content'].includes(module)" type="primary" @click="create">新增</el-button>
        </div></div>
        <div v-if="module === 'dashboard'" class="stats">
          <el-card><small>注册用户</small><strong>{{ dashboard.users || 0 }}</strong></el-card><el-card><small>活动数量</small><strong>{{ dashboard.activities || 0 }}</strong></el-card>
          <el-card><small>兑换订单</small><strong>{{ dashboard.orders || 0 }}</strong></el-card><el-card><small>累计发放积分</small><strong>{{ dashboard.pointsIssued || 0 }}</strong></el-card>
        </div>
        <el-card v-else shadow="never"><el-table :data="rows" empty-text="暂无数据">
          <el-table-column v-for="c in columns" :key="c" :prop="c" :label="c" min-width="130" show-overflow-tooltip />
          <el-table-column label="操作" fixed="right" width="240"><template #default="{ row }">
            <el-button v-if="['activities','questionnaires','products','content'].includes(module)" link type="primary" @click="edit(row)">编辑</el-button>
            <el-button v-if="module === 'users'" link type="primary" @click="adjust(row,'points')">调整积分</el-button>
            <el-button v-if="module === 'products'" link type="primary" @click="adjust(row,'stock')">调整库存</el-button>
            <el-button v-if="module === 'activities'" link @click="showQr(row)">核销码</el-button>
            <el-button v-if="module === 'orders' && row.status === 'PENDING'" link type="success" @click="orderAction(row,'pickup')">确认领取</el-button>
            <el-button v-if="module === 'orders' && row.status === 'PENDING'" link type="danger" @click="orderAction(row,'cancel')">取消</el-button>
          </template></el-table-column>
        </el-table></el-card>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="dialog" :title="dialogMode === 'qr' ? '活动动态核销码' : (dialogMode === 'create' ? '新增' : '编辑') + title" width="680px" @closed="closeDialog">
    <div v-if="dialogMode === 'qr'" class="qr-panel"><img :src="`/api/admin/activities/${editing.id}/qr.png?v=${qrVersion}`" /><code>{{ qr }}</code><p>二维码每 60 秒自动轮换。</p></div>
    <el-form v-else label-width="110px">
      <template v-if="module === 'activities'">
        <el-form-item label="标题"><el-input v-model="editing.title" /></el-form-item>
        <el-form-item label="封面"><el-input v-model="editing.coverUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" :on-success="uploadSuccess"><el-button>上传图片</el-button></el-upload></el-form-item>
        <el-form-item label="报名开始"><el-input v-model="editing.signupStart" placeholder="2026-09-01T00:00:00" /></el-form-item><el-form-item label="报名结束"><el-input v-model="editing.signupEnd" /></el-form-item>
        <el-form-item label="活动开始"><el-input v-model="editing.startTime" /></el-form-item><el-form-item label="活动结束"><el-input v-model="editing.endTime" /></el-form-item>
        <el-form-item label="地点"><el-input v-model="editing.placeName" /></el-form-item><el-form-item label="地址"><el-input v-model="editing.address" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已下架" value="OFFLINE" /></el-select></el-form-item>
        <el-form-item label="详情"><el-input v-model="editing.description" type="textarea" /></el-form-item>
      </template>
      <template v-else-if="module === 'questionnaires'">
        <el-form-item label="名称"><el-input v-model="editing.name" /></el-form-item><el-form-item label="开始时间"><el-input v-model="editing.startTime" /></el-form-item><el-form-item label="结束时间"><el-input v-model="editing.endTime" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已结束" value="ENDED" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="editing.description" type="textarea" /></el-form-item>
        <el-form-item label="题目"><div class="question-editor"><div v-for="(q, i) in editing.questions" :key="i" class="question-row">
          <div class="question-head"><el-select v-model="q.questionType"><el-option label="单选" value="SINGLE" /><el-option label="多选" value="MULTIPLE" /><el-option label="文本" value="TEXT" /></el-select><el-input v-model="q.title" placeholder="题目标题" /><el-switch v-model="q.required" active-text="必填" /><el-button link type="danger" @click="removeQuestion(i)">删除</el-button></div>
          <div v-if="q.questionType !== 'TEXT'" class="option-list"><el-input v-for="(_, oi) in q.options" :key="oi" v-model="q.options[oi]" /><el-button link @click="addOption(q)">+ 选项</el-button></div>
        </div><el-button @click="addQuestion">+ 添加题目</el-button></div></el-form-item>
      </template>
      <template v-else-if="module === 'products'">
        <el-form-item label="名称"><el-input v-model="editing.name" /></el-form-item><el-form-item label="图片"><el-input v-model="editing.coverUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" :on-success="uploadSuccess"><el-button>上传图片</el-button></el-upload></el-form-item>
        <el-form-item label="所需积分"><el-input-number v-model="editing.pointsCost" :min="1" /></el-form-item><el-form-item v-if="dialogMode === 'create'" label="初始库存"><el-input-number v-model="editing.stock" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="上架" value="ON_SALE" /><el-option label="下架" value="OFFLINE" /></el-select></el-form-item><el-form-item label="说明"><el-input v-model="editing.description" type="textarea" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="内容类型"><el-select v-model="editing.contentType"><el-option v-for="t in ['BANNER','ABOUT','RULES','SERVICE','PICKUP']" :key="t" :value="t" /></el-select></el-form-item><el-form-item label="标题"><el-input v-model="editing.title" /></el-form-item>
        <el-form-item label="图片"><el-input v-model="editing.imageUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" :on-success="uploadSuccess"><el-button>上传图片</el-button></el-upload></el-form-item>
        <el-form-item label="内容"><el-input v-model="editing.content" type="textarea" :rows="6" /></el-form-item><el-form-item label="地址"><el-input v-model="editing.address" /></el-form-item><el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item>
      </template>
    </el-form>
    <template v-if="dialogMode !== 'qr'" #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
  </el-dialog>
</template>
