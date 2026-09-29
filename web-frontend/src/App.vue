<script setup>
import { computed, onMounted, ref } from 'vue'

const services = [
  { id: 'order-service', name: '订单服务', description: 'Redis 依赖与订单请求' },
  { id: 'user-service', name: '用户服务', description: '用户接口与运行状态' },
  { id: 'payment-service', name: '支付服务', description: '延迟、错误与支付接口' },
]

const metricNames = {
  http_5xx_rate: 'HTTP 5xx 错误率',
  request_rate: '请求速率',
  avg_latency_seconds: '平均响应时间',
  scrape_up: '监控抓取状态',
  process_cpu_usage: '进程 CPU',
  jvm_heap_used_bytes: 'JVM 堆内存',
}

const service = ref('order-service')
const question = ref('为什么刚才大量 500？')
const serviceState = ref(null)
const diagnosis = ref(null)
const loading = ref(false)
const stateLoading = ref(false)
const error = ref('')
const notice = ref('')

const selectedService = computed(() => services.find((item) => item.id === service.value))
const evidence = computed(() => diagnosis.value?.evidence || [])
const serviceRunning = computed(() => serviceState.value?.length > 0 && serviceState.value.some((item) => item.state === 'running'))

async function request(path, options = {}) {
  const headers = options.body ? { 'Content-Type': 'application/json' } : {}
  const response = await fetch(path, { ...options, headers: { ...headers, ...(options.headers || {}) } })
  let body = null
  try { body = await response.json() } catch { /* empty response */ }
  if (!response.ok) throw new Error(body?.detail || body?.message || `请求失败（${response.status}）`)
  return body
}

async function loadServiceState() {
  stateLoading.value = true
  error.value = ''
  try {
    serviceState.value = await request(`/op/api/operations/services/${service.value}/status`)
  } catch (exception) {
    serviceState.value = null
    error.value = `无法读取服务状态：${exception.message}`
  } finally {
    stateLoading.value = false
  }
}

async function diagnose() {
  if (!question.value.trim()) return
  loading.value = true
  diagnosis.value = null
  error.value = ''
  notice.value = '正在通过 Agent 查询日志和指标...'
  try {
    diagnosis.value = await request('/ai/diagnose', {
      method: 'POST',
      body: JSON.stringify({ service: service.value, question: question.value.trim() }),
    })
    notice.value = '诊断完成，证据已经返回。'
    await loadServiceState()
  } catch (exception) {
    error.value = exception.message
    notice.value = ''
  } finally {
    loading.value = false
  }
}

function selectService(id) {
  service.value = id
  diagnosis.value = null
  loadServiceState()
}

function formatValue(item) {
  const samples = item?.output?.samples
  if (!samples?.length) return '无样本'
  const value = Number(samples[0].value)
  if (item.input?.metric === 'avg_latency_seconds') return `${value.toFixed(3)} 秒`
  if (item.input?.metric === 'http_5xx_rate') return `${(value * 100).toFixed(2)}%`
  if (item.input?.metric === 'jvm_heap_used_bytes') return `${(value / 1024 / 1024).toFixed(1)} MB`
  return value.toFixed(3)
}

function evidenceTitle(tool) {
  if (tool === 'query_logs') return '日志查询'
  if (tool === 'query_metrics') return '指标查询'
  return '服务状态'
}

onMounted(loadServiceState)
</script>

<template>
  <div class="app-shell">
    <header class="topbar">
      <div class="brand"><span class="brand-mark small">S</span><span>SmartOps</span><span class="environment">GATEWAY</span></div>
      <div class="topbar-actions"><span class="connection-dot"></span> Spring Cloud Gateway <span class="route-label">/ai · /op</span></div>
    </header>

    <main class="workspace">
      <aside class="sidebar">
        <div class="section-label">业务服务</div>
        <button v-for="item in services" :key="item.id" class="service-item" :class="{ active: service === item.id }" @click="selectService(item.id)">
          <span class="service-status" :class="{ offline: service === item.id && serviceState && !serviceRunning }"></span>
          <span><strong>{{ item.name }}</strong><small>{{ item.id }}</small></span>
        </button>
        <div class="sidebar-divider"></div>
        <div class="section-label">当前路由</div>
        <div class="route-card"><code>/ai/**</code><small>Python Agent 诊断</small></div>
        <div class="route-card"><code>/op/**</code><small>Java 运维查询</small></div>
        <div class="sidebar-footer"><span class="healthy-dot"></span><span>网关配置已加载</span><small>端口 9000 · dev profile</small></div>
      </aside>

      <section class="main-column">
        <div class="page-heading">
          <div><p class="eyebrow">SPRING CLOUD GATEWAY WORKSPACE</p><h1>运维诊断</h1><p class="muted">通过标准网关路由访问 Agent 和运维服务，查看状态、日志、指标与诊断证据。</p></div>
          <div class="time-badge">纯转发模式</div>
        </div>

        <section class="panel service-overview">
          <div><span class="section-label">目标服务</span><h2>{{ selectedService.name }} <code>{{ selectedService.id }}</code></h2><p>{{ selectedService.description }}</p></div>
          <div class="overview-status"><span class="status-ring" :class="{ offline: serviceState && !serviceRunning }"></span><strong>{{ stateLoading ? '读取中' : serviceState ? serviceRunning ? '运行中' : '未运行' : '未知' }}</strong><small>{{ serviceState?.length || 0 }} 个匹配容器</small></div>
        </section>

        <section class="diagnosis-composer panel">
          <div class="composer-header"><div><span class="section-label">自然语言诊断</span><h2>描述你观察到的故障现象</h2></div><span class="read-only">READ ONLY</span></div>
          <textarea v-model="question" maxlength="2000" placeholder="例如：为什么支付服务响应变慢？" @keydown.ctrl.enter="diagnose"></textarea>
          <div class="composer-footer"><span>{{ question.length }}/2000 · Ctrl + Enter 提交</span><button class="primary-button" :disabled="loading || !question.trim()" @click="diagnose">{{ loading ? 'Agent 分析中...' : '开始诊断' }}</button></div>
        </section>

        <p v-if="error" class="error-banner">{{ error }}</p><p v-if="notice" class="notice-banner">{{ notice }}</p>

        <section v-if="diagnosis" class="panel answer-panel">
          <div class="panel-heading"><div><span class="section-label">AI 诊断结论</span><h2>基于本次取证的分析</h2></div><span class="confidence-note">证据已记录</span></div>
          <div class="answer-text">{{ diagnosis.answer }}</div>
        </section>

        <section v-if="evidence.length" class="panel evidence-panel">
          <div class="panel-heading"><div><span class="section-label">Evidence</span><h2>诊断证据</h2></div><span class="evidence-count">{{ evidence.length }} 项工具结果</span></div>
          <div class="evidence-grid">
            <article v-for="(item, index) in evidence" :key="index" class="evidence-card">
              <div class="evidence-title"><strong>{{ evidenceTitle(item.tool) }}</strong><span>{{ item.tool }}</span></div>
              <p v-if="item.tool === 'query_metrics'" class="metric-value">{{ formatValue(item) }}</p>
              <pre v-else>{{ JSON.stringify(item.output, null, 2) }}</pre>
              <small v-if="item.tool === 'query_metrics'">{{ metricNames[item.input?.metric] || item.input?.metric }} · 空样本不代表 0</small>
            </article>
          </div>
        </section>

        <section v-if="diagnosis && !evidence.length" class="panel empty-evidence"><span class="section-label">Evidence</span><h2>没有取得运维证据</h2><p>Agent 无法可靠诊断，请检查 Python Agent、模型配置和 operation-service。</p></section>
      </section>
    </main>
  </div>
</template>
