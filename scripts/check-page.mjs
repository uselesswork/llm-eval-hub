// 用本机 Chrome 无头模式验证 Vue 对比页渲染了实际导入的两次运行。
import { spawnSync } from 'node:child_process'
import { existsSync, mkdtempSync, readFileSync, rmSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const statePath = process.argv[2] ?? 'target/stage6-smoke-state.json'
const web = process.argv[3] ?? 'http://127.0.0.1:5173/'
const state = JSON.parse(readFileSync(statePath, 'utf8'))
const url = new URL(web)
url.searchParams.set('baselineId', state.baseline)
url.searchParams.set('candidateId', state.candidate)

const candidates = process.env.CHROME_BIN
  ? [process.env.CHROME_BIN]
  : process.platform === 'win32'
    ? [
        join(process.env.PROGRAMFILES ?? '', 'Google/Chrome/Application/chrome.exe'),
        join(process.env['PROGRAMFILES(X86)'] ?? '', 'Microsoft/Edge/Application/msedge.exe'),
      ]
    : ['google-chrome', 'google-chrome-stable', 'chromium', 'chromium-browser']
const browser = candidates.find(candidate =>
  (candidate.includes('/') || candidate.includes('\\') ? existsSync(candidate) : true) &&
  spawnSync(candidate, ['--version'], { encoding: 'utf8', timeout: 5000 }).status === 0,
)
if (!browser) throw new Error('未找到 Chrome/Chromium；可设置 CHROME_BIN 指向浏览器可执行文件。')

const profile = mkdtempSync(join(tmpdir(), 'hub-stage6-chrome-'))
try {
  let lastOutput = ''
  let rendered = false
  for (let attempt = 0; attempt < 3; attempt++) {
    const result = spawnSync(browser, [
      '--headless=new', '--no-sandbox', '--disable-gpu', '--disable-dev-shm-usage',
      '--no-first-run', '--no-default-browser-check', `--user-data-dir=${profile}`,
      '--virtual-time-budget=12000', '--dump-dom', url.toString(),
    ], { encoding: 'utf8', timeout: 45000, maxBuffer: 4 * 1024 * 1024 })
    lastOutput = result.stdout ?? ''
    if (result.status === 0 &&
        lastOutput.includes('同口径版本对比') &&
        lastOutput.includes('指标差值') &&
        lastOutput.includes('baseline-2026-11-17-gpt-6-luna') &&
        lastOutput.includes('stage14-regression-2026-11-18-gpt-6-luna')) {
      rendered = true
      break
    }
    if (result.error && attempt === 2) throw result.error
  }
  if (!rendered) throw new Error(`对比页未显示已导入数据；DOM 末尾：${lastOutput.slice(-1000)}`)
  console.log('Chrome 已渲染 Vue 对比页，显示两份报告和指标差值。')
} finally {
  rmSync(profile, { recursive: true, force: true })
}
