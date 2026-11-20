<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { api, type CaseResult, type CaseReview, type Comparison, type EvalRun, type ReviewVerdict, type RunResults, type RunUsage } from './api'

type View = 'runs' | 'results' | 'compare' | 'review' | 'usage'
const view = ref<View>('runs')
const runs = ref<EvalRun[]>([])
const filters = ref({ sourceProject: '', datasetVersion: '', modelId: '' })
const loadingRuns = ref(false)
const runsError = ref('')
const activeRun = ref<RunResults | null>(null)
const usage = ref<RunUsage | null>(null)
const usageError = ref('')
const loadingUsage = ref(false)
const classifications = ref<Record<string, string[]>>({})
const reviews = ref<CaseReview[]>([])
const selectedCaseId = ref('')
const detailError = ref('')
const loadingDetail = ref(false)
const baselineId = ref('')
const candidateId = ref('')
const comparison = ref<Comparison | null>(null)
const comparisonError = ref('')
const comparing = ref(false)
const reviewer = ref('')
const verdict = ref<ReviewVerdict>('NEEDS_FOLLOWUP')
const note = ref('')
const reviewError = ref('')
const reviewMessage = ref('')
const savingReview = ref(false)

const selectedCase = computed<CaseResult | null>(() =>
  activeRun.value?.cases.find(item => item.caseId === selectedCaseId.value) ?? null,
)
const latestReviews = computed(() => {
  const byCase = new Map<string, CaseReview>()
  for (const review of reviews.value) {
    if (!byCase.has(review.caseId)) byCase.set(review.caseId, review)
  }
  return byCase
})
const caseReviews = computed(() => reviews.value.filter(item => item.caseId === selectedCaseId.value))

const sourceNames: Record<string, string> = {
  LIVE_MODEL: '真实模型',
  OFFLINE_FIXTURE: '离线预置',
  HUMAN_REVIEW: '人工复核',
}
const verdictNames: Record<string, string> = {
  PASS: '通过',
  FAIL: '不通过',
  NEEDS_FOLLOWUP: '待跟进',
}
const failureNames: Record<string, string> = {
  RETRIEVAL_MISS: '检索未命中',
  CITATION_PRECISION_BELOW_ONE: '引用准确率未达 1',
  ANSWER_CONTAINS_FAILED: '答案字面检查未通过',
  REFUSAL_INCORRECT: '拒答检查未通过',
}
const metricNames: Record<string, string> = {
  'qa.hit_at_k': 'Hit@k',
  'qa.mrr_at_k': 'MRR@k',
  'qa.citation_precision': '引用准确率',
  'qa.refusal_accuracy': '拒答正确率',
  'qa.answer_contains_accuracy': '答案包含率',
  'qa.latency_p50_ms': '延迟 P50',
  'qa.latency_p95_ms': '延迟 P95',
  'qa.model_calls_total': '模型调用总数',
  'qa.model_calls_mean': '平均模型调用数',
  'qa.reciprocal_rank': '倒数排名',
  'qa.answer_contains_all': '答案字面包含',
  'qa.refusal_correct': '拒答正确',
}

function shortHash(value: string) {
  return value.startsWith('sha256:') ? `${value.slice(7, 19)}…` : value.length > 20 ? `${value.slice(0, 17)}…` : value
}
function dateTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value))
}
function number(value: number | null, unit = '') {
  if (value === null) return '暂无数据'
  if (unit === 'RATIO') return `${new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value * 100)}%`
  const formatted = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 4 }).format(value)
  return unit === 'MILLISECONDS' ? `${formatted} ms` : formatted
}
function deltaNumber(value: number | null, unit: string) {
  if (value === null) return '暂无数据'
  if (unit === 'RATIO') {
    const points = new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value * 100)
    return `${value > 0 ? '+' : ''}${points} 个百分点`
  }
  return `${value > 0 ? '+' : ''}${number(value, unit)}`
}
function metricName(key: string) { return metricNames[key] ?? key }
function failureName(key: string) { return failureNames[key] ?? key }
function sourceName(key: string) { return sourceNames[key] ?? key }
function verdictName(key: string) { return verdictNames[key] ?? key }

async function loadRuns() {
  loadingRuns.value = true
  runsError.value = ''
  try {
    runs.value = await api.runs(filters.value)
    if (!baselineId.value && runs.value.length > 0) baselineId.value = runs.value[0]!.id
    if (!candidateId.value && runs.value.length > 1) candidateId.value = runs.value[1]!.id
  } catch (error) {
    runsError.value = (error as Error).message
    runs.value = []
  } finally {
    loadingRuns.value = false
  }
}

async function openRun(id: string, destination: View = 'results') {
  view.value = destination
  detailError.value = ''
  reviewMessage.value = ''
  activeRun.value = null
  usage.value = null
  selectedCaseId.value = ''
  loadingDetail.value = true
  try {
    const [result, failureList, reviewList, runUsage] = await Promise.all([
      api.results(id), api.classifications(id), api.reviews(id), api.usage(id),
    ])
    activeRun.value = result
    usage.value = runUsage
    classifications.value = Object.fromEntries(failureList.map(item => [item.caseId, item.failures]))
    reviews.value = reviewList
    selectedCaseId.value = result.cases[0]?.caseId ?? ''
  } catch (error) {
    detailError.value = (error as Error).message
  } finally {
    loadingDetail.value = false
  }
}

async function compareRuns() {
  comparison.value = null
  comparisonError.value = ''
  if (!baselineId.value || !candidateId.value) {
    comparisonError.value = '请先选择两次已导入报告的运行。'
    return
  }
  comparing.value = true
  try {
    comparison.value = await api.comparison(baselineId.value, candidateId.value)
  } catch (error) {
    comparisonError.value = (error as Error).message
  } finally {
    comparing.value = false
  }
}

async function openUsage(id: string) {
  view.value = 'usage'
  usage.value = null
  usageError.value = ''
  loadingUsage.value = true
  try {
    usage.value = await api.usage(id)
  } catch (error) {
    usageError.value = (error as Error).message
  } finally {
    loadingUsage.value = false
  }
}

async function saveReview() {
  reviewError.value = ''
  reviewMessage.value = ''
  if (!activeRun.value || !selectedCaseId.value) return
  if (!reviewer.value.trim()) {
    reviewError.value = '请填写审核人姓名或代号。'
    return
  }
  savingReview.value = true
  try {
    await api.createReview(activeRun.value.run.id, selectedCaseId.value, {
      reviewer: reviewer.value.trim(), verdict: verdict.value, note: note.value.trim(),
    })
    reviews.value = await api.reviews(activeRun.value.run.id)
    note.value = ''
    reviewMessage.value = '审核结论已保存，历史记录会保留。'
  } catch (error) {
    reviewError.value = (error as Error).message
  } finally {
    savingReview.value = false
  }
}

onMounted(loadRuns)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar">
      <div class="brand"><span class="brand-mark">E</span><div><strong>LLM Eval Hub</strong><small>评测工作台</small></div></div>
      <p class="side-caption">工作区</p>
      <nav aria-label="主导航">
        <button :class="{ active: view === 'runs' }" @click="view = 'runs'"><span>▦</span> 运行列表</button>
        <button :class="{ active: view === 'results' }" @click="view = 'results'"><span>≡</span> 逐例结果</button>
        <button :class="{ active: view === 'compare' }" @click="view = 'compare'"><span>⇄</span> 版本对比</button>
        <button :class="{ active: view === 'review' }" @click="view = 'review'"><span>✓</span> 人工复核</button>
        <button :class="{ active: view === 'usage' }" @click="view = 'usage'"><span>◫</span> 调用用量</button>
      </nav>
      <div class="side-note"><span class="status-dot"></span><span>数据来自当前连接的后端<br />不生成模拟评测记录</span></div>
      <div class="side-footer">阶段 04 <span>·</span> 评测与用量</div>
    </aside>

    <main class="main-panel">
      <header class="topbar"><div class="breadcrumb">工作区 <span>/</span> {{ view === 'runs' ? '运行列表' : view === 'results' ? '逐例结果' : view === 'compare' ? '版本对比' : view === 'usage' ? '调用用量' : '人工复核' }}</div><span class="local-badge">本地评测环境</span></header>
      <div class="content">
        <section class="page-heading">
          <div><p class="eyebrow">EVALUATION WORKSPACE</p><h1>{{ view === 'runs' ? '评测运行' : view === 'results' ? '逐例结果' : view === 'compare' ? '同口径版本对比' : view === 'usage' ? '调用用量' : '人工复核' }}</h1><p class="subtitle">{{ view === 'runs' ? '查找已记录的运行，进入报告详情与人工复核。' : view === 'results' ? '查看报告中的原始检查值与自动失败分类。' : view === 'compare' ? '只对案例集、结果来源与指标定义一致的运行计算差异。' : view === 'usage' ? '查看已登记的调用 token、价格版本和费用状态。' : '为单个案例记录人工结论；自动检查与人工结论分别展示。' }}</p></div>
          <span class="stage-pill">第四阶段 · 本地版</span>
        </section>

        <template v-if="view === 'runs'">
          <section class="overview-grid">
            <div class="overview-card"><span>当前查询记录</span><strong>{{ runs.length }}</strong><small>最多返回最近 100 条</small></div>
            <div class="overview-card"><span>真实模型标签</span><strong>{{ runs.filter(item => item.resultSource === 'LIVE_MODEL').length }}</strong><small>由导入者声明，未自动核验</small></div>
            <div class="overview-card muted"><span>用量与费用</span><strong>按运行查看</strong><small>在报告详情中显示真实上报记录</small></div>
          </section>
          <section class="surface filters"><div class="section-title"><div><h2>筛选运行</h2><p>按来源、案例集哈希或模型标识精确匹配</p></div></div><form class="filter-row" @submit.prevent="loadRuns"><label>来源项目<input v-model="filters.sourceProject" placeholder="例如 qa-system" /></label><label>案例集版本<input v-model="filters.datasetVersion" placeholder="sha256:…" /></label><label>模型标识<input v-model="filters.modelId" placeholder="例如 gpt-6-luna" /></label><button class="primary" :disabled="loadingRuns">{{ loadingRuns ? '查询中…' : '查询运行' }}</button></form></section>
          <section class="surface"><div class="section-title"><div><h2>最近运行</h2><p>选择一条记录查看逐例结果或调用用量</p></div><span class="count-badge">{{ runs.length }} 条</span></div><p v-if="runsError" class="alert error">{{ runsError }}</p><p v-else-if="loadingRuns" class="empty">正在读取运行记录…</p><p v-else-if="runs.length === 0" class="empty">当前条件下没有运行。先启动后端并导入知识库问答系统评分报告。</p><div v-else class="table-wrap"><table><thead><tr><th>运行标签</th><th>来源项目</th><th>模型 / 来源</th><th>案例集版本</th><th>记录时间</th><th></th></tr></thead><tbody><tr v-for="run in runs" :key="run.id"><td><strong>{{ run.runLabel }}</strong><small class="row-sub">{{ run.id.slice(0, 8) }}</small></td><td>{{ run.sourceProject }}</td><td>{{ run.modelId ?? '未提供' }}<small class="row-sub">{{ sourceName(run.resultSource) }}</small></td><td><code :title="run.datasetVersion">{{ shortHash(run.datasetVersion) }}</code></td><td>{{ dateTime(run.createdAt) }}</td><td><button class="text-button" @click="openRun(run.id)">查看结果</button> <button class="text-button" @click="openUsage(run.id)">查看用量</button></td></tr></tbody></table></div></section>
        </template>

        <template v-else-if="view === 'results' || view === 'review'">
          <div v-if="!activeRun && !loadingDetail" class="surface empty-state"><div class="empty-symbol">⌗</div><h2>先选择一条已导入报告的运行</h2><p>{{ detailError || '从运行列表进入详情。普通元数据运行没有逐例结果。' }}</p><button class="primary" @click="view = 'runs'">前往运行列表</button></div>
          <div v-else-if="loadingDetail" class="surface empty">正在读取逐例结果…</div>
          <template v-else-if="activeRun">
            <section class="surface report-intro"><div><span class="overline">{{ activeRun.run.sourceProject }} / {{ sourceName(activeRun.run.resultSource) }}</span><h2>{{ activeRun.run.runLabel }}</h2><p>模型 {{ activeRun.run.modelId ?? '未提供' }} · {{ activeRun.provenance.sampleCount }} 个案例 · k = {{ activeRun.provenance.k }}</p></div><div class="report-meta"><span>报告 SHA-256</span><code :title="activeRun.provenance.reportSha256">{{ shortHash(activeRun.provenance.reportSha256) }}</code><span>案例集 SHA-256</span><code :title="activeRun.provenance.datasetSha256">{{ shortHash(activeRun.provenance.datasetSha256) }}</code></div></section>
            <section v-if="view === 'results'" class="surface"><div class="section-title"><div><h2>汇总指标</h2><p>各指标保留独立的样本范围与分母</p></div></div><div class="metric-grid"><div v-for="metric in activeRun.metrics" :key="metric.key" class="metric-card"><span>{{ metricName(metric.key) }}</span><strong>{{ number(metric.value, metric.unit) }}</strong><small>{{ metric.sampleScope }} · n={{ metric.denominator }} · {{ metric.definitionVersion }}</small></div></div></section>
            <section v-if="view === 'results' && usage" class="surface"><div class="section-title"><div><h2>模型调用用量与费用</h2><p>仅依据单次调用的 token 记录与已登记价格版本核算；原报告的模型调用次数不等于 token 用量。</p></div></div><div class="metric-grid"><div class="metric-card"><span>调用记录</span><strong>{{ usage.callCount }}</strong><small>已核算 {{ usage.pricedCallCount }} 条</small></div><div class="metric-card"><span>输入 / 输出 token</span><strong>{{ usage.inputTokens ?? '暂无数据' }} / {{ usage.outputTokens ?? '暂无数据' }}</strong><small>仅汇总已提供用量的调用</small></div><div class="metric-card"><span>运行总费用</span><strong>{{ usage.totalCost === null ? '无法计算' : `${usage.totalCost} ${usage.currency}` }}</strong><small>{{ usage.status === 'NO_CALLS' ? '尚无调用记录' : usage.status === 'INCOMPLETE' ? '部分调用缺少用量或价格' : usage.status === 'MIXED_CURRENCY' ? '存在不同币种' : '全部调用可核算' }}</small></div></div><div v-if="usage.calls.length" class="table-wrap"><table><thead><tr><th>请求 ID</th><th>模型</th><th>输入 / 输出 token</th><th>价格版本</th><th>费用</th></tr></thead><tbody><tr v-for="call in usage.calls" :key="call.id"><td>{{ call.requestId }}</td><td>{{ call.modelId }}</td><td>{{ call.inputTokens ?? '暂无数据' }} / {{ call.outputTokens ?? '暂无数据' }}</td><td>{{ call.priceVersion ?? '未提供' }}</td><td>{{ call.cost === null ? '无法计算' : `${call.cost} ${call.currency}` }}</td></tr></tbody></table></div></section>
            <section class="surface"><div class="section-title"><div><h2>逐例检查</h2><p>自动分类由评分值推导，不代表人工审核结论</p></div><span class="count-badge">{{ activeRun.cases.length }} 例</span></div><div class="table-wrap"><table><thead><tr><th>案例</th><th>题型</th><th>自动失败分类</th><th>最新人工结论</th><th>耗时</th><th></th></tr></thead><tbody><tr v-for="item in activeRun.cases" :key="item.caseId" :class="{ 'selected-row': selectedCaseId === item.caseId }"><td><strong>{{ item.caseId }}</strong><small class="row-sub">{{ item.category }}</small></td><td>{{ item.sampleScope === 'REFUSAL' ? '拒答题' : '可回答题' }}</td><td><span v-if="!classifications[item.caseId]?.length" class="quiet">未触发规则</span><span v-else class="failure-count">{{ classifications[item.caseId]?.length }} 项</span></td><td>{{ latestReviews.get(item.caseId) ? verdictName(latestReviews.get(item.caseId)!.verdict) : '未复核' }}</td><td>{{ number(item.latencyMs, 'MILLISECONDS') }}</td><td><button class="text-button" @click="selectedCaseId = item.caseId">查看</button></td></tr></tbody></table></div></section>
            <section v-if="selectedCase" class="surface case-detail"><div class="section-title"><div><span class="overline">CASE DETAIL</span><h2>{{ selectedCase.caseId }}</h2></div><button v-if="view === 'results'" class="secondary" @click="view = 'review'">审核此案例</button><button v-else class="secondary" @click="view = 'results'">查看完整结果</button></div><div class="detail-grid"><div><h3>自动失败分类</h3><p v-if="!classifications[selectedCase.caseId]?.length" class="quiet">未触发已定义规则</p><div v-else class="tags"><span v-for="code in classifications[selectedCase.caseId]" :key="code" class="failure-tag">{{ failureName(code) }}</span></div><p class="detail-foot">模型调用次数 {{ selectedCase.modelCalls }} · 延迟 {{ number(selectedCase.latencyMs, 'MILLISECONDS') }}</p></div><div><h3>逐题指标</h3><dl class="check-list"><div v-for="metric in selectedCase.metrics" :key="metric.key"><dt>{{ metricName(metric.key) }}</dt><dd>{{ metric.passed === null ? number(metric.value) : metric.passed ? '通过' : '未通过' }}</dd></div></dl></div></div>
              <div v-if="view === 'review'" class="review-layout"><form class="review-form" @submit.prevent="saveReview"><h3>记录审核结论</h3><p>审核人与结论由填写者声明；系统目前没有登录鉴权。</p><label>审核人姓名或代号<input v-model="reviewer" maxlength="128" required placeholder="填写可追溯的名称" /></label><label>结论<select v-model="verdict"><option value="PASS">通过</option><option value="FAIL">不通过</option><option value="NEEDS_FOLLOWUP">待跟进</option></select></label><label>说明<textarea v-model="note" maxlength="1000" rows="4" placeholder="简述判断依据或待跟进事项"></textarea></label><p v-if="reviewError" class="alert error">{{ reviewError }}</p><p v-if="reviewMessage" class="alert success">{{ reviewMessage }}</p><button class="primary" :disabled="savingReview">{{ savingReview ? '保存中…' : '保存结论' }}</button></form><div class="review-history"><h3>审核历史</h3><p v-if="caseReviews.length === 0" class="quiet">尚无人工复核记录。</p><article v-for="item in caseReviews" :key="item.id" class="history-item"><div><strong>{{ verdictName(item.verdict) }}</strong><span>{{ dateTime(item.recordedAt) }}</span></div><p>{{ item.note || '未填写说明' }}</p><small>审核人：{{ item.reviewer }}</small></article></div></div>
            </section>
          </template>
        </template>

        <template v-else-if="view === 'usage'">
          <div v-if="loadingUsage" class="surface empty">正在读取调用用量…</div>
          <div v-else-if="!usage" class="surface empty-state"><h2>先选择一条运行</h2><p>{{ usageError || '从运行列表选择“查看用量”；普通元数据运行也可以查看。' }}</p><button class="primary" @click="view = 'runs'">前往运行列表</button></div>
          <section v-else class="surface"><div class="section-title"><div><h2>运行调用汇总</h2><p>运行 ID：{{ usage.runId }}；报告里的模型调用次数不能代替真实 token。</p></div></div><div class="metric-grid"><div class="metric-card"><span>调用记录</span><strong>{{ usage.callCount }}</strong><small>已核算 {{ usage.pricedCallCount }} 条</small></div><div class="metric-card"><span>输入 / 输出 token</span><strong>{{ usage.inputTokens ?? '暂无数据' }} / {{ usage.outputTokens ?? '暂无数据' }}</strong><small>仅合计已提供用量的调用</small></div><div class="metric-card"><span>运行总费用</span><strong>{{ usage.totalCost === null ? '无法计算' : `${usage.totalCost} ${usage.currency}` }}</strong><small>{{ usage.status === 'NO_CALLS' ? '尚无调用记录' : usage.status === 'INCOMPLETE' ? '部分调用缺少用量或价格' : usage.status === 'MIXED_CURRENCY' ? '存在不同币种' : '全部调用可核算' }}</small></div></div><div v-if="usage.calls.length" class="table-wrap"><table><thead><tr><th>请求 ID</th><th>模型</th><th>输入 / 输出 token</th><th>耗时</th><th>价格版本</th><th>费用 / 状态</th></tr></thead><tbody><tr v-for="call in usage.calls" :key="call.id"><td>{{ call.requestId }}</td><td>{{ call.modelId }}</td><td>{{ call.inputTokens ?? '暂无数据' }} / {{ call.outputTokens ?? '暂无数据' }}</td><td>{{ call.latencyMs === null ? '暂无数据' : `${call.latencyMs} ms` }}</td><td>{{ call.priceVersion ?? '未提供' }}</td><td>{{ call.cost === null ? (call.status === 'MISSING_USAGE' ? '缺少 token' : '缺少价格') : `${call.cost} ${call.currency}` }}</td></tr></tbody></table></div></section>
        </template>

        <template v-else>
          <section class="surface compare-controls"><div class="section-title"><div><h2>选择两次运行</h2><p>候选报告需使用相同案例集、题目、指标定义、分母、k 和结果来源</p></div></div><div class="compare-selects"><label>基准运行<select v-model="baselineId"><option value="">请选择</option><option v-for="run in runs" :key="run.id" :value="run.id">{{ run.runLabel }} · {{ run.modelId ?? '未提供模型' }}</option></select></label><span class="compare-divider">对比</span><label>候选运行<select v-model="candidateId"><option value="">请选择</option><option v-for="run in runs" :key="run.id" :value="run.id">{{ run.runLabel }} · {{ run.modelId ?? '未提供模型' }}</option></select></label><button class="primary" :disabled="comparing" @click="compareRuns">{{ comparing ? '比较中…' : '开始比较' }}</button></div><p v-if="comparisonError" class="alert error">{{ comparisonError }}</p></section>
          <div v-if="!comparison" class="surface empty-state"><div class="empty-symbol">⇄</div><h2>比较结果将在这里显示</h2><p>需要两份已导入、口径一致的报告。当前只有一份报告时，可先导入另一版本。</p></div>
          <template v-else><section class="surface comparison-head"><div><span class="overline">BASELINE</span><h2>{{ comparison.baseline.runLabel }}</h2><p>{{ comparison.baseline.modelId ?? '未提供模型' }}</p></div><span class="comparison-vs">VS</span><div><span class="overline">CANDIDATE</span><h2>{{ comparison.candidate.runLabel }}</h2><p>{{ comparison.candidate.modelId ?? '未提供模型' }}</p></div></section><section class="surface"><div class="section-title"><div><h2>指标差值</h2><p>差值 = 候选值 − 基准值；比例指标的差值单位为百分点</p></div></div><div class="table-wrap"><table><thead><tr><th>指标</th><th>范围 / 分母</th><th>基准</th><th>候选</th><th>差值</th></tr></thead><tbody><tr v-for="metric in comparison.metrics" :key="metric.key"><td><strong>{{ metricName(metric.key) }}</strong><small class="row-sub">{{ metric.definitionVersion }}</small></td><td>{{ metric.sampleScope }} · n={{ metric.denominator }}</td><td>{{ number(metric.baselineValue, metric.unit) }}</td><td>{{ number(metric.candidateValue, metric.unit) }}</td><td><strong>{{ deltaNumber(metric.delta, metric.unit) }}</strong></td></tr></tbody></table></div></section><section class="surface"><div class="section-title"><div><h2>案例失败变化</h2><p>只显示同一案例新增或消失的自动失败分类</p></div></div><div class="table-wrap"><table><thead><tr><th>案例</th><th>新增失败</th><th>已消失失败</th></tr></thead><tbody><tr v-for="item in comparison.cases" :key="item.caseId"><td><strong>{{ item.caseId }}</strong><small class="row-sub">{{ item.category }}</small></td><td><span v-if="item.newFailures.length === 0" class="quiet">无</span><div v-else class="tags"><span v-for="code in item.newFailures" :key="code" class="failure-tag">{{ failureName(code) }}</span></div></td><td><span v-if="item.resolvedFailures.length === 0" class="quiet">无</span><div v-else class="tags"><span v-for="code in item.resolvedFailures" :key="code" class="resolved-tag">{{ failureName(code) }}</span></div></td></tr></tbody></table></div></section></template>
        </template>
      </div>
    </main>
  </div>
</template>
