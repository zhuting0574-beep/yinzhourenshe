<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import * as echarts from 'echarts'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { UploadRequestOptions } from 'element-plus'
import { imageUploading as uploading, uploadAdminImage } from '../utils/imageUpload'
import api from '../api'
import AmapLocationPicker from '../components/AmapLocationPicker.vue'
import RichTextEditor from '../components/RichTextEditor.vue'
import QuestionnaireResults from '../components/QuestionnaireResults.vue'
import { useSession } from '../stores/session'

const props = defineProps<{ module: string }>()
const router = useRouter()
const session = useSession()
const loading = ref(false)
const rows = ref<any[]>([])
const selectedOrders = ref<any[]>([])
const ordersTable = ref<any>()
const ordersPhone = ref('')
const activities = ref<any[]>([])
const dashboard = ref<any>({})
const dialog = ref(false)
const dialogMode = ref('')
const editing = reactive<any>({})
const search = ref('')
const qr = ref('')
const qrImageUrl = ref('')
const dashboardDimension = ref('month')
const dashboardTrend = ref<any>(null)
const dashboardChartEl = ref<HTMLElement | null>(null)
let dashboardChart: echarts.ECharts | null = null
const resultsDialog = ref(false)
const resultsQuestionnaire = ref<any | null>(null)
const userDetailDialog = ref(false)
const userDetail = ref<any | null>(null)
const userDetailLoading = ref(false)

const menus = [
  ['dashboard', '数据概览'], ['users', '用户管理'], ['activities', '活动管理'],
  ['questionnaires', '问卷管理'], ['products', '商品与库存'], ['orders', '兑换订单'],
  ['points', '积分明细'], ['content', '展示内容'], ['banners', '首页 banner 配置']
]
const visibleMenus = computed(() => session.orderOnly ? menus.filter(x => x[0] === 'orders') : menus)
const title = computed(() => menus.find(x => x[0] === props.module)?.[1] || '工作台')
const columns = computed(() => {
  const preferred: Record<string, string[]> = {
    users: ['id', 'nickname', 'phone', 'points', 'status', 'created_at'],
    activities: ['id', 'title', 'signup_start', 'start_time', 'checkin_mode', 'status', 'registrations', 'checkins'],
    questionnaires: ['id', 'name', 'start_time', 'end_time', 'status', 'pinned', 'submissions'],
    products: ['id', 'name', 'points_cost', 'stock', 'locked_stock', 'redeemed_stock', 'status'],
    orders: ['order_no', 'product_name', 'nickname', 'phone', 'points', 'expire_at', 'status'],
    points: ['nickname', 'phone', 'amount', 'balance', 'type', 'remark', 'created_at'],
    content: ['title', 'content', 'updated_at'],
    banners: ['image_url', 'start_time', 'end_time', 'activity_title', 'enabled', 'sort_order']
  }
  return preferred[props.module] || Object.keys(rows.value[0] || {})
})
const columnLabels: Record<string, Record<string, string>> = {
  users: { id: '用户编号', nickname: '用户昵称', phone: '手机号', points: '可用积分', status: '账号状态', created_at: '注册时间' },
  activities: { id: '活动编号', title: '活动名称', signup_start: '报名开始', start_time: '活动开始', checkin_mode: '签到方式', status: '活动状态', registrations: '报名人数', checkins: '签到人数' },
  questionnaires: { id: '问卷编号', name: '问卷名称', start_time: '开始时间', end_time: '结束时间', status: '问卷状态', pinned: '是否置顶', submissions: '提交数量' },
  products: { id: '商品编号', name: '商品名称', points_cost: '所需积分', stock: '可用库存', locked_stock: '锁定库存', redeemed_stock: '已兑换数量', status: '商品状态' },
  orders: { order_no: '订单编号', product_name: '商品名称', nickname: '用户昵称', phone: '手机号', points: '消耗积分', expire_at: '领取截止', status: '订单状态' },
  points: { nickname: '用户昵称', phone: '手机号', amount: '积分变动', balance: '积分余额', type: '变动类型', remark: '变动说明', created_at: '记录时间' },
  content: { title: '名称', content: '值', updated_at: '修改时间' },
  banners: { image_url: '图片', start_time: '开始时间', end_time: '结束时间', activity_title: '关联活动（可选）', enabled: '启用', sort_order: '排序' }
}
const valueLabels: Record<string, string> = {
  ACTIVE: '正常', DRAFT: '草稿', PUBLISHED: '已发布', OFFLINE: '已下架', ENDED: '已结束',
  ON_SALE: '已上架', PENDING: '待核销', PICKED_UP: '已核销', CANCELED: '已取消',
  REGISTER: '注册奖励', DAILY_CHECKIN: '每日签到', QUESTIONNAIRE: '问卷奖励', ACTIVITY: '活动奖励',
  EXCHANGE: '兑换扣减', ADMIN_ADD: '管理员增加', ADMIN_SUBTRACT: '管理员扣减', EXPIRED: '过期清零', EXCHANGE_CANCEL: '兑换退还',
  BANNER: '轮播图', ABOUT: '关于我们', RULES: '积分规则', SERVICE: '服务点', PRODUCT_NOTICE: '商品详情温馨提示', ORDER_SUCCESS: '兑换成功提示', ORDER_RECORD: '兑换记录提示',
  QR: '二维码签到', LOCATION: '定位签到', BOTH: '二维码或定位签到'
}
function columnLabel(field: string) { return columnLabels[props.module]?.[field] || field }
function displayValue(field: string, value: any) {
  if (value === null || value === undefined || value === '') return '-'
  if (['enabled', 'pinned'].includes(field)) return value ? '是' : '否'
  if (field === 'content') return String(value).replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim() || '-'
  return valueLabels[String(value)] || value
}
const uploadHeaders = computed(() => ({ Authorization: `Bearer ${localStorage.getItem('adminToken') || ''}` }))

async function load() {
  loading.value = true
  try {
    if (props.module === 'dashboard') {
      dashboard.value = await api.get('/admin/dashboard')
      await loadDashboardTrend()
    }
    else {
      if (props.module === 'orders') {
        selectedOrders.value = []
        ordersTable.value?.clearSelection()
      }
      const params = props.module === 'users' ? { phone: search.value } : props.module === 'orders' ? { phone: ordersPhone.value.trim() } : {}
      rows.value = await api.get(`/admin/${props.module}`, { params })
      if (props.module === 'content') rows.value = rows.value.filter((row: any) => row.content_type !== 'BANNER')
      if (props.module === 'banners') activities.value = await api.get('/admin/activities')
    }
  } catch (e: any) { ElMessage.error(e.message) } finally { loading.value = false }
}
async function loadDashboardTrend() {
  dashboardTrend.value = await api.get('/admin/dashboard/trends', { params: { dimension: dashboardDimension.value } })
  await nextTick()
  if (!dashboardChartEl.value || !dashboardTrend.value) return
  dashboardChart ||= echarts.init(dashboardChartEl.value)
  dashboardChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { top: 0, data: ['新增用户数', '活跃用户数', '活动报名人数'] },
    grid: { left: 46, right: 24, top: 48, bottom: 34 },
    xAxis: { type: 'category', boundaryGap: false, data: dashboardTrend.value.labels },
    yAxis: { type: 'value', minInterval: 1 },
    series: [
      { name: '新增用户数', type: 'line', smooth: true, symbol: 'circle', data: dashboardTrend.value.newUsers },
      { name: '活跃用户数', type: 'line', smooth: true, symbol: 'circle', data: dashboardTrend.value.activeUsers },
      { name: '活动报名人数', type: 'line', smooth: true, symbol: 'circle', data: dashboardTrend.value.registrations }
    ]
  })
}
function resizeDashboardChart() { dashboardChart?.resize() }
function go(key: string) { router.push('/' + key) }
function resetEditing() { Object.keys(editing).forEach(k => delete editing[k]) }
function normalizeDateTime(value: unknown) {
  if (!value) return ''
  return String(value).replace('T', ' ').replace(/\.\d+.*$/, '').slice(0, 19)
}
function normalizeRichText(value: unknown) {
  const text = String(value || '').trim()
  if (!text) return '<p><br></p>'
  if (/<[a-z][\s\S]*>/i.test(text)) return text
  const escaped = text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
  return `<p>${escaped.replace(/\n/g, '<br>')}</p>`
}
function create() {
  if (props.module === 'banners' && rows.value.length >= 4) {
    ElMessage.warning('banner最多4个')
    return
  }
  resetEditing()
  Object.assign(editing, { status: props.module === 'activities' ? 'PUBLISHED' : 'DRAFT', enabled: true, sortOrder: 0 })
  if (props.module === 'activities') Object.assign(editing, { checkinMode: 'QR', checkinRadiusM: 200, description: '<p><br></p>' })
  if (props.module === 'questionnaires') Object.assign(editing, { questions: [], pinned: false })
  if (props.module === 'products') Object.assign(editing, { status: 'ON_SALE', stock: 0, pointsCost: 1, description: '<p><br></p>' })
  if (props.module === 'banners') Object.assign(editing, { imageUrl: '', startTime: '', endTime: '', activityId: null, enabled: true, sortOrder: rows.value.length })
  dialogMode.value = 'create'
  dialog.value = true
}
async function edit(row: any) {
  resetEditing()
  let source = row
  if (props.module === 'questionnaires') source = await api.get(`/admin/questionnaires/${row.id}`)
  if (props.module === 'banners') source = await api.get(`/admin/banners/${row.id}`)
  Object.assign(editing, source, {
    coverUrl: source.cover_url, signupStart: normalizeDateTime(source.signup_start), signupEnd: normalizeDateTime(source.signup_end),
    startTime: normalizeDateTime(source.start_time), endTime: normalizeDateTime(source.end_time), placeName: source.place_name,
    pointsCost: source.points_cost, imageUrl: source.image_url, contentType: source.content_type,
    popupImageUrl: source.popup_image_url, popupTitle: source.popup_title, headerTitle: source.header_title,
    pinned: !!source.pinned,
    sortOrder: source.sort_order, activityId: source.activity_id ?? source.activityId, checkinMode: source.checkin_mode || 'QR', checkinRadiusM: source.checkin_radius_m || 200,
    description: ['activities', 'products'].includes(props.module) ? normalizeRichText(source.description) : source.description
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
    if (props.module === 'banners') {
      if (!body.imageUrl) throw new Error('请上传 banner 图片')
      if (!body.startTime || !body.endTime) throw new Error('请选择开始时间和结束时间')
      if (normalizeDateTime(body.startTime) > normalizeDateTime(body.endTime)) throw new Error('结束时间不能早于开始时间')
      // 未选择活动时提交 null，表示该 banner 仅展示，不绑定点击跳转。
      body.activityId = body.activityId === '' || body.activityId === undefined || body.activityId === null
        ? null
        : Number(body.activityId)
    }
    if (props.module === 'activities') {
      body.points = 100
      if (body.checkinMode !== 'QR' && (!Number.isFinite(Number(body.latitude)) || !Number.isFinite(Number(body.longitude)))) throw new Error('定位签到活动必须设置有效经纬度')
      if (body.checkinMode !== 'QR' && Number(body.checkinRadiusM) <= 0) throw new Error('签到范围必须大于 0 米')
    }
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
async function showUserDetail(row: any) {
  userDetailLoading.value = true
  userDetailDialog.value = true
  try { userDetail.value = await api.get(`/admin/users/${row.id}`) } catch (e: any) { userDetailDialog.value = false; ElMessage.error(e.message) } finally { userDetailLoading.value = false }
}
async function removeUser(row: any) {
  try {
    await ElMessageBox.confirm(`删除用户“${row.nickname || row.phone || row.id}”后，其报名、问卷、订单和积分记录都会删除，确定继续吗？`, '删除用户', { type: 'warning', confirmButtonText: '确认删除', cancelButtonText: '取消' })
    await api.delete(`/admin/users/${row.id}`)
    ElMessage.success('用户已删除')
    await load()
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
function isOrderSelectable(row: any) { return row.status === 'PENDING' }
function onOrderSelectionChange(selection: any[]) { selectedOrders.value = selection }
function selectAllPendingOrders() {
  const pending = rows.value.filter(isOrderSelectable)
  if (!pending.length) return
  ordersTable.value?.clearSelection()
  pending.forEach(row => ordersTable.value?.toggleRowSelection(row, true))
}
async function batchPickupOrders() {
  const ids = selectedOrders.value.filter(isOrderSelectable).map(row => row.id)
  if (!ids.length) return ElMessage.warning('请先勾选待核销订单')
  if (ids.length > 500) return ElMessage.warning('一次最多批量核销500个订单，请分批操作')
  try {
    await ElMessageBox.confirm(`已选择 ${ids.length} 个订单，确认批量核销吗？`, '批量核销', { type: 'warning', confirmButtonText: '确认核销', cancelButtonText: '取消' })
    const result: any = await api.post('/admin/orders/pickup/batch', { ids })
    ElMessage.success(`已核销 ${result?.pickedUp ?? ids.length} 个订单`)
    await load()
  } catch (e: any) { if (!['cancel', 'close'].includes(e) && e?.message) ElMessage.error(e.message) }
}
async function batchCancelOrders() {
  const ids = selectedOrders.value.filter(isOrderSelectable).map(row => row.id)
  if (!ids.length) return ElMessage.warning('请先勾选待取消订单')
  if (ids.length > 500) return ElMessage.warning('一次最多批量取消500个订单，请分批操作')
  try {
    const { value: reason } = await ElMessageBox.prompt(`已选择 ${ids.length} 个订单，请填写所有订单共用的取消原因`, '批量取消', {
      inputValidator: value => {
        if (!value?.trim()) return '必须填写取消原因'
        if (Array.from(value).length > 300) return '取消原因不能超过300个字符'
        return true
      },
      inputErrorMessage: '取消原因必填，且不能超过300个字符',
      confirmButtonText: '确认取消',
      cancelButtonText: '返回'
    })
    const result: any = await api.post('/admin/orders/cancel/batch', { ids, reason })
    ElMessage.success(`已取消 ${result?.cancelled ?? ids.length} 个订单，积分已按规则退还`)
    await load()
  } catch (e: any) { if (!['cancel', 'close'].includes(e) && e?.message) ElMessage.error(e.message) }
}
async function showQr(row: any) {
  try {
    resetEditing()
    Object.assign(editing, row)
    const data: any = await api.get(`/admin/activities/${row.id}/qr`)
    const response = await fetch(`/api/admin/activities/${row.id}/qr.png`, { headers: uploadHeaders.value })
    if (!response.ok) throw new Error('二维码加载失败')
    if (qrImageUrl.value) URL.revokeObjectURL(qrImageUrl.value)
    qr.value = data.token
    qrImageUrl.value = URL.createObjectURL(await response.blob())
    dialogMode.value = 'qr'
    dialog.value = true
  } catch (e: any) { ElMessage.error(e.message) }
}
function closeDialog() { if (qrImageUrl.value) URL.revokeObjectURL(qrImageUrl.value); qrImageUrl.value = '' }
function showResults(row: any) { resultsQuestionnaire.value = row; resultsDialog.value = true }
async function exportData() {
  const resource = props.module === 'activities' ? 'participants' : props.module === 'questionnaires' ? 'responses' : 'points'
  const response = await fetch(`/api/admin/exports/${resource}`, { headers: uploadHeaders.value })
  if (!response.ok) return ElMessage.error('导出失败')
  const blob = await response.blob(), url = URL.createObjectURL(blob), a = document.createElement('a')
  a.href = url; a.download = `${resource}.xlsx`; a.click(); URL.revokeObjectURL(url)
}
async function exportQuestionnaire(row: any) {
  try {
    const response = await fetch(`/api/admin/exports/questionnaires/${row.id}`, { headers: uploadHeaders.value })
    if (!response.ok) {
      let message = '导出失败'
      try { message = (await response.json()).message || message } catch {}
      throw new Error(message)
    }
    const blob = await response.blob()
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `questionnaire-${row.id}-statistics.xlsx`
    link.click()
    URL.revokeObjectURL(url)
  } catch (e: any) { ElMessage.error(e.message || '导出失败') }
}
function addQuestion() { (editing.questions || (editing.questions = [])).push({ questionType: 'SINGLE', title: '', options: ['选项一', '选项二'], required: true }) }
function removeQuestion(i: number) { editing.questions.splice(i, 1) }
function addOption(question: any) { (question.options || (question.options = [])).push('新选项') }
function uploadSuccess(result: any) {
  const url = result.data?.url || result.url
  if (props.module === 'questionnaires') editing.popupImageUrl = url
  else if (['content', 'banners'].includes(props.module)) editing.imageUrl = url
  else editing.coverUrl = url
}

async function uploadImage(options: UploadRequestOptions) {
  try {
    const result = await uploadAdminImage(options.file)
    options.onSuccess?.(result)
    return result
  } catch (error: any) {
    options.onError?.(error)
    throw error
  }
}

function imageUploadError(error: any) {
  ElMessage.error(error.message || '图片上传失败')
}
async function removeBanner(row: any) {
  try {
    await ElMessageBox.confirm('删除后该 banner 将不再展示，确定删除吗？', '删除 banner', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await api.delete(`/admin/banners/${row.id}`)
    ElMessage.success('删除成功')
    await load()
  } catch (e: any) { if (!['cancel', 'close'].includes(e) && e?.message) ElMessage.error(e.message) }
}
watch(() => props.module, load)
watch(dashboardDimension, () => { if (props.module === 'dashboard') loadDashboardTrend() })
onMounted(load)
onMounted(() => window.addEventListener('resize', resizeDashboardChart))
onBeforeUnmount(() => { closeDialog(); window.removeEventListener('resize', resizeDashboardChart); dashboardChart?.dispose() })
</script>

<template>
  <el-container class="admin">
    <el-aside width="238px">
      <div class="logo"><span>π</span><div><b>鄞领π</b><small>运营管理平台</small></div></div>
      <el-menu :default-active="module" @select="go"><el-menu-item v-for="m in visibleMenus" :key="m[0]" :index="m[0]">{{ m[1] }}</el-menu-item></el-menu>
      <div class="operator">{{ session.username }}<el-button link @click="session.logout">退出</el-button></div>
    </el-aside>
    <el-container>
      <el-header><span>工作台 / <b>{{ title }}</b></span><span>{{ new Date().toLocaleDateString() }}</span></el-header>
      <el-main v-loading="loading">
        <div class="heading"><div><h1>{{ title }}</h1><p>鄞领π业务数据与运营操作</p></div><div class="toolbar">
          <el-input v-if="module === 'users'" v-model="search" placeholder="手机号精确查询" clearable @keyup.enter="load" />
          <el-input v-if="module === 'orders'" v-model="ordersPhone" placeholder="按手机号搜索" clearable @keyup.enter="load" @clear="load" />
          <el-button v-if="module === 'orders'" @click="load">查询</el-button>
          <el-button v-if="module === 'orders'" :disabled="!rows.some(isOrderSelectable)" @click="selectAllPendingOrders">全选待核销</el-button>
          <el-button v-if="module === 'orders'" type="primary" :disabled="!selectedOrders.length" @click="batchPickupOrders">批量核销{{ selectedOrders.length ? `（${selectedOrders.length}）` : '' }}</el-button>
          <el-button v-if="module === 'orders'" type="danger" plain :disabled="!selectedOrders.length" @click="batchCancelOrders">批量取消{{ selectedOrders.length ? `（${selectedOrders.length}）` : '' }}</el-button>
          <el-button v-if="['activities','points'].includes(module)" @click="exportData">导出 Excel</el-button>
          <el-button v-if="['activities','questionnaires','products','banners'].includes(module)" type="primary" @click="create">新增</el-button>
        </div></div>
        <div v-if="module === 'dashboard'" class="stats">
          <el-card><small>注册用户</small><strong>{{ dashboard.users || 0 }}</strong></el-card><el-card><small>活动数量</small><strong>{{ dashboard.activities || 0 }}</strong></el-card>
          <el-card><small>兑换订单</small><strong>{{ dashboard.orders || 0 }}</strong></el-card><el-card><small>累计发放积分</small><strong>{{ dashboard.pointsIssued || 0 }}</strong></el-card>
        </div>
        <el-card v-if="module === 'dashboard'" class="dashboard-trend" shadow="never">
          <div class="dashboard-trend__head"><div><h2>运营趋势</h2><p>按时间维度查看用户与活动数据</p></div><el-radio-group v-model="dashboardDimension" size="small"><el-radio-button label="year">年</el-radio-button><el-radio-button label="month">月</el-radio-button><el-radio-button label="day">日</el-radio-button></el-radio-group></div>
          <div ref="dashboardChartEl" class="dashboard-chart" v-loading="!dashboardTrend" />
        </el-card>
        <el-card v-else shadow="never"><el-table ref="ordersTable" :data="rows" empty-text="暂无数据" @selection-change="onOrderSelectionChange">
          <el-table-column v-if="module === 'orders'" type="selection" width="48" :selectable="isOrderSelectable" />
          <el-table-column v-for="c in columns" :key="c" :prop="c" :label="columnLabel(c)" :min-width="c === 'image_url' ? 150 : 130" show-overflow-tooltip><template #default="{ row }">
            <el-image v-if="module === 'banners' && c === 'image_url' && row[c]" :src="row[c]" fit="cover" style="width: 120px; height: 54px; border-radius: 6px" :preview-src-list="[row[c]]" preview-teleported />
            <span v-else>{{ displayValue(c, row[c]) }}</span>
          </template></el-table-column>
          <el-table-column label="操作" fixed="right" :width="module === 'questionnaires' ? 360 : module === 'banners' ? 180 : 240"><template #default="{ row }">
            <el-button v-if="['activities','questionnaires','products','content','banners'].includes(module)" link type="primary" @click="edit(row)">{{ module === 'banners' ? '修改' : '编辑' }}</el-button>
            <el-button v-if="module === 'banners'" link type="danger" @click="removeBanner(row)">删除</el-button>
            <el-button v-if="module === 'questionnaires'" link type="success" @click="showResults(row)">统计结果</el-button>
            <el-button v-if="module === 'questionnaires'" link type="success" @click="exportQuestionnaire(row)">导出统计</el-button>
            <el-button v-if="module === 'users'" link type="primary" @click="adjust(row,'points')">调整积分</el-button>
            <el-button v-if="module === 'users'" link type="primary" @click="showUserDetail(row)">详情</el-button>
            <el-button v-if="module === 'users'" link type="danger" @click="removeUser(row)">删除</el-button>
            <el-button v-if="module === 'products'" link type="primary" @click="adjust(row,'stock')">调整库存</el-button>
            <el-button v-if="module === 'activities' && ['QR','BOTH'].includes(row.checkin_mode || 'QR')" link @click="showQr(row)">核销码</el-button>
            <el-button v-if="module === 'orders' && row.status === 'PENDING'" link type="success" @click="orderAction(row,'pickup')">确认核销</el-button>
            <el-button v-if="module === 'orders' && row.status === 'PENDING'" link type="danger" @click="orderAction(row,'cancel')">取消</el-button>
          </template></el-table-column>
        </el-table></el-card>
      </el-main>
    </el-container>
  </el-container>

  <el-dialog v-model="dialog" class="workbench-dialog" destroy-on-close :title="dialogMode === 'qr' ? '活动签到二维码' : (dialogMode === 'create' ? '新增' : '编辑') + title" :width="dialogMode !== 'qr' && ['activities','products'].includes(module) ? '900px' : '680px'" @closed="closeDialog">
    <div v-if="dialogMode === 'qr'" class="qr-panel"><img :src="qrImageUrl" /><code>{{ qr }}</code><p>该二维码长期有效，仅用于当前活动现场签到。</p></div>
    <el-form v-else label-width="110px">
      <template v-if="module === 'activities'">
        <el-form-item label="标题"><el-input v-model="editing.title" /></el-form-item>
        <el-form-item label="封面"><el-input v-model="editing.coverUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" :disabled="uploading" :http-request="uploadImage" :on-success="uploadSuccess" :on-error="imageUploadError"><el-button :loading="uploading" :disabled="uploading">上传图片</el-button></el-upload><span class="upload-size-tip">建议尺寸：1380 × 560 px（约 2.5:1）；主体留出安全边距</span></el-form-item>
        <el-form-item label="报名开始"><el-date-picker v-model="editing.signupStart" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择报名开始时间" /></el-form-item>
        <el-form-item label="报名结束"><el-date-picker v-model="editing.signupEnd" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择报名结束时间" /></el-form-item>
        <el-form-item label="活动开始"><el-date-picker v-model="editing.startTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择活动开始时间" /></el-form-item>
        <el-form-item label="活动结束"><el-date-picker v-model="editing.endTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择活动结束时间" /></el-form-item>
        <el-form-item label="地点"><el-input v-model="editing.placeName" /></el-form-item><el-form-item label="地址"><el-input v-model="editing.address" /></el-form-item>
        <el-form-item label="签到方式"><el-select v-model="editing.checkinMode" class="full-control"><el-option label="二维码签到" value="QR" /><el-option label="定位签到" value="LOCATION" /><el-option label="二维码或定位签到" value="BOTH" /></el-select></el-form-item>
        <template v-if="editing.checkinMode !== 'QR'">
          <el-form-item label="签到位置"><AmapLocationPicker v-model:longitude="editing.longitude" v-model:latitude="editing.latitude" :address="editing.address" /></el-form-item>
          <el-form-item label="签到范围"><el-input-number v-model="editing.checkinRadiusM" :min="1" :precision="0" /><span class="unit">米</span></el-form-item>
        </template>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已下架" value="OFFLINE" /></el-select></el-form-item>
        <el-form-item label="详情"><RichTextEditor v-model="editing.description" /></el-form-item>
      </template>
      <template v-else-if="module === 'questionnaires'">
        <el-form-item label="名称"><el-input v-model="editing.name" /></el-form-item>
        <el-form-item label="弹框图片"><el-input v-model="editing.popupImageUrl" placeholder="上传后自动填写图片地址" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" accept="image/*" :disabled="uploading" :http-request="uploadImage" :on-success="uploadSuccess" :on-error="imageUploadError"><el-button :loading="uploading" :disabled="uploading">上传图片</el-button></el-upload><el-image v-if="editing.popupImageUrl" :src="editing.popupImageUrl" fit="contain" style="width: 160px; height: 100px; margin-left: 12px" /><span class="upload-size-tip">建议尺寸：600 × 600 px（接近方形，主体居中）</span></el-form-item>
        <el-form-item label="弹框标题"><el-input v-model="editing.popupTitle" placeholder="默认：填写调查问卷" maxlength="200" /></el-form-item>
        <el-form-item label="问卷头部"><el-input v-model="editing.headerTitle" placeholder="默认：问卷任务" maxlength="200" /></el-form-item>
        <el-form-item label="置顶问卷"><el-switch v-model="editing.pinned" active-text="置顶" inactive-text="普通" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="editing.startTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择开始时间" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="editing.endTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择结束时间" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="草稿" value="DRAFT" /><el-option label="已发布" value="PUBLISHED" /><el-option label="已结束" value="ENDED" /></el-select></el-form-item>
        <el-form-item label="说明"><el-input v-model="editing.description" type="textarea" /></el-form-item>
        <el-form-item label="题目"><div class="question-editor"><div v-for="(q, i) in editing.questions" :key="i" class="question-row">
          <div class="question-head"><el-select v-model="q.questionType"><el-option label="单选" value="SINGLE" /><el-option label="多选" value="MULTIPLE" /><el-option label="文本" value="TEXT" /></el-select><el-input v-model="q.title" placeholder="题目标题" /><el-switch v-model="q.required" active-text="必填" /><el-button link type="danger" @click="removeQuestion(i)">删除</el-button></div>
          <div v-if="q.questionType !== 'TEXT'" class="option-list"><el-input v-for="(_, oi) in q.options" :key="oi" v-model="q.options[oi]" /><el-button link @click="addOption(q)">+ 选项</el-button></div>
        </div><el-button @click="addQuestion">+ 添加题目</el-button></div></el-form-item>
      </template>
      <template v-else-if="module === 'products'">
        <el-form-item label="名称"><el-input v-model="editing.name" /></el-form-item><el-form-item label="图片"><el-input v-model="editing.coverUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" :disabled="uploading" :http-request="uploadImage" :on-success="uploadSuccess" :on-error="imageUploadError"><el-button :loading="uploading" :disabled="uploading">上传图片</el-button></el-upload><span class="upload-size-tip">建议尺寸：600 × 600 px（接近方形，主体居中）</span></el-form-item>
        <el-form-item label="所需积分"><el-input-number v-model="editing.pointsCost" :min="1" /></el-form-item><el-form-item v-if="dialogMode === 'create'" label="初始库存"><el-input-number v-model="editing.stock" :min="0" /></el-form-item>
        <el-form-item label="状态"><el-select v-model="editing.status"><el-option label="上架" value="ON_SALE" /><el-option label="下架" value="OFFLINE" /></el-select></el-form-item><el-form-item label="说明"><RichTextEditor v-model="editing.description" /></el-form-item>
      </template>
      <template v-else-if="module === 'banners'">
        <el-form-item label="图片"><el-input v-model="editing.imageUrl" placeholder="上传后自动填写图片地址" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" accept="image/*" :disabled="uploading" :http-request="uploadImage" :on-success="uploadSuccess" :on-error="imageUploadError"><el-button :loading="uploading" :disabled="uploading">上传图片</el-button></el-upload><el-image v-if="editing.imageUrl" :src="editing.imageUrl" fit="contain" style="width: 220px; height: 100px; margin-left: 12px" /><span class="upload-size-tip">建议尺寸：1500 × 840 px（约 16:9）</span></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="editing.startTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择开始时间" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="editing.endTime" class="date-time-picker" type="datetime" format="YYYY-MM-DD HH:mm" value-format="YYYY-MM-DD HH:mm:ss" placeholder="选择结束时间" /></el-form-item>
        <el-form-item label="关联活动（可选）"><el-select v-model="editing.activityId" class="full-control" filterable clearable placeholder="不关联活动（纯展示）"><el-option v-for="activity in activities" :key="activity.id" :label="activity.title" :value="activity.id" /></el-select><span class="field-tip">可不选择活动；不关联时 banner 仅展示，不跳转</span></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="editing.sortOrder" :min="0" :precision="0" /></el-form-item>
      </template>
      <template v-else>
        <el-form-item label="内容类型"><el-select v-model="editing.contentType" disabled><el-option v-for="t in ['ABOUT','RULES','SERVICE','PRODUCT_NOTICE','ORDER_SUCCESS','ORDER_RECORD']" :key="t" :value="t" /></el-select></el-form-item><el-form-item label="名称"><el-input v-model="editing.title" disabled /></el-form-item>
        <el-form-item label="图片"><el-input v-model="editing.imageUrl" /><el-upload class="inline-upload" action="/api/admin/uploads" :headers="uploadHeaders" :show-file-list="false" accept="image/*" :disabled="uploading" :http-request="uploadImage" :on-success="uploadSuccess" :on-error="imageUploadError"><el-button :loading="uploading" :disabled="uploading">上传图片</el-button></el-upload><span class="upload-size-tip">建议宽度：1200 px，比例可按内容需要调整</span></el-form-item>
        <el-form-item label="内容"><RichTextEditor v-model="editing.content" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="editing.address" /></el-form-item>
        <el-form-item v-if="editing.contentType === 'SERVICE'" label="地图位置"><AmapLocationPicker v-model:longitude="editing.longitude" v-model:latitude="editing.latitude" :address="editing.address" /></el-form-item>
        <el-form-item v-if="editing.contentType === 'SERVICE'" label="经度"><el-input-number v-model="editing.longitude" :precision="7" :step="0.000001" /></el-form-item>
        <el-form-item v-if="editing.contentType === 'SERVICE'" label="纬度"><el-input-number v-model="editing.latitude" :precision="7" :step="0.000001" /></el-form-item>
        <el-form-item label="启用"><el-switch v-model="editing.enabled" /></el-form-item>
      </template>
    </el-form>
    <template v-if="dialogMode !== 'qr'" #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
  </el-dialog>
  <QuestionnaireResults v-if="module === 'questionnaires'" v-model="resultsDialog" :questionnaire="resultsQuestionnaire" />
  <el-dialog v-model="userDetailDialog" title="用户详情" width="720px">
    <div v-loading="userDetailLoading" class="user-detail" v-if="userDetail">
      <div class="user-detail__summary">
        <el-avatar :size="64" :src="userDetail.avatar_url || undefined">{{ (userDetail.nickname || '用户').slice(0, 1) }}</el-avatar>
        <div><h3>{{ userDetail.nickname || '未设置昵称' }}</h3><p>{{ userDetail.phone || '未授权手机号' }}</p></div>
        <div class="user-detail__points"><small>当前积分</small><strong>{{ userDetail.points || 0 }}</strong></div>
      </div>
      <el-descriptions :column="2" border>
        <el-descriptions-item label="用户编号">{{ userDetail.id }}</el-descriptions-item>
        <el-descriptions-item label="账号状态">{{ displayValue('status', userDetail.status) }}</el-descriptions-item>
        <el-descriptions-item label="真实姓名">{{ userDetail.real_name || '-' }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ userDetail.age || '-' }}</el-descriptions-item>
        <el-descriptions-item label="毕业院校">{{ userDetail.graduation_school || '-' }}</el-descriptions-item>
        <el-descriptions-item label="专业方向">{{ userDetail.major_direction || '-' }}</el-descriptions-item>
        <el-descriptions-item label="所在城市">{{ userDetail.city || '-' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ userDetail.created_at || '-' }}</el-descriptions-item>
      </el-descriptions>
      <h4 class="user-detail__heading">积分明细</h4>
      <el-table :data="(userDetail.pointLogs || []).slice(0, 20)" max-height="260" empty-text="暂无积分记录">
        <el-table-column prop="created_at" label="时间" min-width="160" />
        <el-table-column prop="amount" label="变动" width="90" />
        <el-table-column prop="balance" label="余额" width="90" />
        <el-table-column prop="remark" label="说明" min-width="220" show-overflow-tooltip />
      </el-table>
    </div>
  </el-dialog>
</template>
