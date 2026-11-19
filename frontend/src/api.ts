export type ResultSource = 'LIVE_MODEL' | 'OFFLINE_FIXTURE' | 'HUMAN_REVIEW'
export type ReviewVerdict = 'PASS' | 'FAIL' | 'NEEDS_FOLLOWUP'

export interface EvalRun {
  id: string
  sourceProject: string
  runLabel: string
  datasetVersion: string
  modelId: string | null
  promptVersion: string | null
  resultSource: ResultSource
  status: string
  startedAt: string
  createdAt: string
}

export interface AggregateMetric {
  key: string
  definitionVersion: string
  aggregation: string
  sampleScope: string
  denominator: number
  unit: string
  value: number | null
}

export interface CaseMetric {
  key: string
  definitionVersion: string
  passed: boolean | null
  value: number | null
}

export interface CaseResult {
  caseId: string
  category: string
  sampleScope: string
  latencyMs: number
  modelCalls: number
  metrics: CaseMetric[]
}

export interface RunResults {
  run: EvalRun
  provenance: {
    reportFilename: string
    reportSha256: string
    datasetSha256: string
    predictionsSha256: string
    k: number
    sampleCount: number
    answerableCount: number
    refusalCount: number
  }
  metrics: AggregateMetric[]
  cases: CaseResult[]
}

export interface CaseFailures {
  caseId: string
  failures: string[]
}

export interface CaseReview {
  id: string
  runId: string
  caseId: string
  reviewer: string
  verdict: ReviewVerdict
  note: string | null
  recordedAt: string
}

export interface Comparison {
  baseline: EvalRun
  candidate: EvalRun
  datasetSha256: string
  k: number
  metrics: Array<{
    key: string
    definitionVersion: string
    sampleScope: string
    denominator: number
    unit: string
    baselineValue: number | null
    candidateValue: number | null
    delta: number | null
  }>
  cases: Array<{
    caseId: string
    category: string
    baselineFailures: string[]
    candidateFailures: string[]
    newFailures: string[]
    resolvedFailures: string[]
  }>
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(path, init)
  } catch {
    throw new Error('无法连接后端，请确认 Spring Boot 已启动。')
  }
  if (!response.ok) {
    const messages: Record<number, string> = {
      400: '请求内容无效，请检查填写的字段。',
      404: '记录不存在或尚未导入评分报告。',
      409: '案例集、结果来源或指标口径不一致，不能直接比较。',
    }
    throw new Error(messages[response.status] ?? `请求失败（HTTP ${response.status}）。`)
  }
  return response.json() as Promise<T>
}

export const api = {
  runs(filters: { sourceProject?: string; datasetVersion?: string; modelId?: string }) {
    const params = new URLSearchParams({ limit: '100' })
    Object.entries(filters).forEach(([key, value]) => { if (value) params.set(key, value) })
    return request<EvalRun[]>(`/api/runs?${params}`)
  },
  results(id: string) {
    return request<RunResults>(`/api/runs/${encodeURIComponent(id)}/results`)
  },
  classifications(id: string) {
    return request<CaseFailures[]>(`/api/runs/${encodeURIComponent(id)}/classifications`)
  },
  reviews(id: string) {
    return request<CaseReview[]>(`/api/runs/${encodeURIComponent(id)}/reviews`)
  },
  createReview(runId: string, caseId: string, body: { reviewer: string; verdict: ReviewVerdict; note: string }) {
    return request<CaseReview>(`/api/runs/${encodeURIComponent(runId)}/cases/${encodeURIComponent(caseId)}/reviews`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
    })
  },
  comparison(baselineId: string, candidateId: string) {
    const params = new URLSearchParams({ baselineId, candidateId })
    return request<Comparison>(`/api/comparisons?${params}`)
  },
}
