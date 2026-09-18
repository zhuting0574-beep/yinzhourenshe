<script setup lang="ts">
import { nextTick, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts'
import api from '../api'

const props = defineProps<{ questionnaire: any | null }>()
const visible = defineModel<boolean>({ default: false })
const loading = ref(false)
const questions = ref<any[]>([])
const responses = ref<any[]>([])
const charts = new Map<string, echarts.ECharts>()
const chartNodes = new Map<string, HTMLElement>()

function parseAnswers(value: unknown) {
  if (value && typeof value === 'object') return value as Record<string, any>
  try { return JSON.parse(String(value || '{}')) } catch { return {} }
}
function answerValues(value: any): string[] {
  if (Array.isArray(value)) return value.map(String)
  if (value === null || value === undefined || value === '') return []
  return [String(value)]
}
function optionStats(question: any) {
  const options = Array.isArray(question.options) ? question.options : []
  const counts = new Map(options.map((option: any) => [String(option), 0]))
  for (const response of responses.value) {
    for (const answer of answerValues(parseAnswers(response.answers_json)[String(question.id)])) {
      if (counts.has(answer)) counts.set(answer, (counts.get(answer) || 0) + 1)
    }
  }
  return [...counts.entries()].map(([name, value]) => ({ name, value }))
}
function textAnswers(question: any) {
  return responses.value.map((response) => ({
    nickname: response.nickname || '匿名用户',
    phone: response.phone || '',
    submittedAt: response.submitted_at || '',
    answer: answerValues(parseAnswers(response.answers_json)[String(question.id)]).join('、')
  })).filter((item) => item.answer)
}
function setChartNode(id: string, node: HTMLElement | null) {
  if (node) chartNodes.set(id, node)
  else chartNodes.delete(id)
}
function renderChart(question: any) {
  const node = chartNodes.get(String(question.id))
  if (!node) return
  const existing = charts.get(String(question.id))
  const chart = existing || echarts.init(node)
  charts.set(String(question.id), chart)
  chart.setOption({
    tooltip: { trigger: 'item', formatter: '{b}: {c} 票 ({d}%)' },
    legend: { bottom: 0, type: 'scroll' },
    series: [{ type: 'pie', radius: ['38%', '68%'], center: ['50%', '43%'], avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 }, label: { formatter: '{b}\n{d}%' }, data: optionStats(question) }]
  })
}
function renderCharts() { nextTick(() => questions.value.filter(q => q.question_type !== 'TEXT').forEach(renderChart)) }
async function load() {
  if (!props.questionnaire) return
  loading.value = true
  try {
    const detail: any = await api.get(`/admin/questionnaires/${props.questionnaire.id}`)
    questions.value = (detail.questions || []).map((question: any) => ({
      ...question,
      options: typeof question.options_json === 'string' ? JSON.parse(question.options_json || '[]') : (question.options || [])
    }))
    responses.value = await api.get(`/admin/questionnaires/${props.questionnaire.id}/responses`)
    renderCharts()
  } finally { loading.value = false }
}
function resize() { charts.forEach((chart) => chart.resize()) }
watch(visible, (open) => { if (open) load(); else charts.forEach((chart) => chart.clear()) })
watch(responses, renderCharts)
onBeforeUnmount(() => charts.forEach((chart) => chart.dispose()))
</script>

<template>
  <el-dialog v-model="visible" title="问卷统计结果" width="900px" class="results-dialog" destroy-on-close @opened="resize">
    <div v-loading="loading" class="results-body">
      <div v-if="!loading && !responses.length" class="results-empty">暂无答卷</div>
      <section v-for="question in questions" :key="question.id" class="result-question">
        <div class="result-question__title">{{ question.title }}</div>
        <div v-if="question.question_type !== 'TEXT'" :ref="(node: any) => setChartNode(String(question.id), node as HTMLElement)" class="result-chart" />
        <div v-else class="text-answers">
          <div v-for="(item, index) in textAnswers(question)" :key="`${question.id}-${index}`" class="text-answer">
            <div class="text-answer__meta">{{ item.nickname }}<span>{{ item.phone }}</span><time>{{ item.submittedAt }}</time></div>
            <div class="text-answer__value">{{ item.answer }}</div>
          </div>
          <div v-if="!textAnswers(question).length" class="muted">暂无文本回答</div>
        </div>
      </section>
    </div>
  </el-dialog>
</template>
