/**
 * 端到端冒烟：真实浏览器 + 真实后端，不依赖测试框架。
 *
 * 运行前需启动：
 *   1) 后端  http://127.0.0.1:8080  （GET /api/health = 200）
 *   2) 前端  http://127.0.0.1:5173  （npm run dev）
 *
 * 覆盖：登录三角色、分页翻页、消息页、家庭授权区、接单开关（可逆写操作，测完还原）、
 *      未登录访问受保护路由的重定向。
 * 不创建订单与评价，避免污染演示数据；写入链路由后端集成测试覆盖。
 *
 * 环境变量：
 *   E2E_BASE   前端地址，默认 http://127.0.0.1:5173
 *   E2E_CHROME Chrome 可执行文件路径，默认取 Windows 常见安装位置
 */
import { chromium } from 'playwright-core'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const repoRoot = path.resolve(here, '..', '..')
const BASE = process.env.E2E_BASE ?? 'http://127.0.0.1:5173'
const CHROME = process.env.E2E_CHROME ?? 'C:/Program Files/Google/Chrome/Application/chrome.exe'

function readEnv(file) {
  const out = {}
  if (!fs.existsSync(file)) return out
  for (const line of fs.readFileSync(file, 'utf8').split(/\r?\n/)) {
    const match = /^\s*([A-Z_]+)\s*=\s*(.*)$/.exec(line)
    if (match) out[match[1]] = match[2].trim().replace(/^['"]|['"]$/g, '')
  }
  return out
}

const env = readEnv(path.join(repoRoot, '.env'))
const ACCOUNTS = {
  user: { username: 'family01', password: env.DEMO_ACCOUNT_PASSWORD, home: '/user' },
  worker: { username: 'worker01', password: env.DEMO_ACCOUNT_PASSWORD, home: '/worker' },
  admin: { username: 'admin', password: env.BOOTSTRAP_ADMIN_PASSWORD, home: '/admin' },
}

const results = []
async function check(name, action) {
  try {
    const detail = await action()
    results.push({ name, ok: true, detail: detail ?? '' })
    console.log(`[PASS] ${name}${detail ? ` · ${detail}` : ''}`)
  } catch (error) {
    results.push({ name, ok: false, detail: error.message })
    console.log(`[FAIL] ${name} · ${error.message}`)
  }
}

function assert(condition, message) {
  if (!condition) throw new Error(message)
}

async function login(browser, account) {
  assert(account.password, `缺少 ${account.username} 的密码，请检查 .env`)
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } })
  const page = await context.newPage()
  await page.goto(`${BASE}/login`, { waitUntil: 'load' })
  await page.fill('#login-username', account.username)
  await page.fill('#login-password', account.password)
  await page.click('button[type=submit]')
  await page.waitForFunction((home) => location.pathname.startsWith(home), account.home, { timeout: 15000 })
  return { context, page }
}

async function main() {
  const health = await fetch('http://127.0.0.1:8080/api/health').catch(() => null)
  assert(health?.ok, '后端未就绪：请先启动 backend（GET /api/health）')
  const front = await fetch(BASE).catch(() => null)
  assert(front?.ok, `前端未就绪：请先 npm run dev（${BASE}）`)
  assert(fs.existsSync(CHROME), `未找到 Chrome：${CHROME}，可用 E2E_CHROME 指定`)

  const browser = await chromium.launch({ executablePath: CHROME, headless: true })
  try {
    // ---- 管理端：分页与页签 ----
    const admin = await login(browser, ACCOUNTS.admin)
    await admin.page.goto(`${BASE}/admin/operations`, { waitUntil: 'load' })
    await admin.page.waitForTimeout(1500)

    await check('管理端分页条显示总数与页码', async () => {
      const info = await admin.page.locator('.pager__info').first().innerText()
      assert(/共 \d+ 条 · 第 \d+ \/ \d+ 页/.test(info), `分页信息异常：${info}`)
      return info.replace(/\s+/g, ' ')
    })

    await check('管理端翻页后行内容变化', async () => {
      const firstCell = admin.page.locator('.table tbody tr td').first()
      const before = await firstCell.innerText()
      const next = admin.page.locator('.pager__btn', { hasText: '下一页' }).first()
      if (await next.isDisabled()) return '仅一页，跳过翻页比对'
      await next.click()
      await admin.page.waitForTimeout(1200)
      const after = await firstCell.innerText()
      assert(before !== after, `翻页后首行未变化：${before}`)
      return `${before} → ${after}`
    })

    await check('服务运营七个页签齐全', async () => {
      const count = await admin.page.locator('[role="tab"]').count()
      assert(count === 7, `页签数量 ${count}`)
      return `${count} 个`
    })
    await admin.context.close()

    // ---- 用户端：订单、评价入口、家庭、消息 ----
    const user = await login(browser, ACCOUNTS.user)
    await check('我的订单页渲染真实数据', async () => {
      await user.page.goto(`${BASE}/user/orders`, { waitUntil: 'load' })
      await user.page.waitForTimeout(1200)
      const body = await user.page.locator('body').innerText()
      assert(!/加载失败|请求失败/.test(body), '页面出现加载失败提示')
      const cards = await user.page.locator('.order-card').count()
      assert(cards > 0, '没有渲染任何订单卡片')
      return `${cards} 张订单卡`
    })

    await check('已完成订单有评价入口且弹层可打开', async () => {
      const button = user.page.locator('button', { hasText: '评价服务' }).first()
      const count = await user.page.locator('button', { hasText: '评价服务' }).count()
      if (count === 0) return '当前没有待评价订单（可能均已评价）'
      await button.click()
      await user.page.waitForTimeout(600)
      const opened = await user.page.locator('.dialog-wrap.is-open').count()
      assert(opened === 1, '评价弹层未打开')
      await user.page.keyboard.press('Escape')
      await user.page.waitForTimeout(300)
      return `${count} 个入口`
    })

    await check('家庭页展示授权区', async () => {
      await user.page.goto(`${BASE}/user/family`, { waitUntil: 'load' })
      await user.page.waitForTimeout(1000)
      assert(await user.page.locator('#sec-grants').count(), '缺少家庭授权区块')
      return '授权区块存在'
    })

    await check('消息页分页条可用', async () => {
      await user.page.goto(`${BASE}/user/messages`, { waitUntil: 'load' })
      await user.page.waitForTimeout(1000)
      const info = await user.page.locator('.pager__info').first().innerText()
      assert(/共 \d+ 条/.test(info), `分页信息异常：${info}`)
      return info.replace(/\s+/g, ' ')
    })
    await user.context.close()

    // ---- 人员端：接单开关（可逆） ----
    const worker = await login(browser, ACCOUNTS.worker)
    await check('接单开关可切换并还原', async () => {
      // 等登录提示消失，避免读到上一条 toast
      await worker.page.locator('.toast').first().waitFor({ state: 'detached', timeout: 6000 }).catch(() => {})
      const toggle = worker.page.locator('section.duty button.switch')
      const before = await toggle.getAttribute('aria-pressed')
      await toggle.click()
      await worker.page.waitForTimeout(900)
      const after = await toggle.getAttribute('aria-pressed')
      assert(before !== after, '接单状态未变化')
      const toast = await worker.page.locator('.toast').first().innerText().catch(() => '')
      await toggle.click()
      await worker.page.waitForTimeout(700)
      const restored = await toggle.getAttribute('aria-pressed')
      assert(restored === before, '未能还原接单状态')
      return `${before} → ${after} → ${restored}${toast ? `，提示：${toast}` : ''}`
    })
    await worker.context.close()

    // ---- 未登录访问受保护路由 ----
    await check('未登录访问 /user 跳转登录并带回跳地址', async () => {
      const context = await browser.newContext({ viewport: { width: 1200, height: 800 } })
      const page = await context.newPage()
      await page.goto(`${BASE}/user/orders`, { waitUntil: 'load' })
      await page.waitForTimeout(1200)
      const url = new URL(page.url())
      assert(url.pathname === '/login', `未跳转登录：${url.pathname}`)
      assert(url.searchParams.get('redirect') === '/user/orders', `缺少回跳参数：${url.search}`)
      await context.close()
      return decodeURIComponent(url.search)
    })
  } finally {
    await browser.close()
  }

  const failed = results.filter((item) => !item.ok)
  console.log(`\n${results.length - failed.length}/${results.length} 项通过`)
  if (failed.length) {
    console.error('失败项：', failed.map((item) => `${item.name}（${item.detail}）`).join('；'))
    process.exitCode = 1
  }
}

main().catch((error) => {
  console.error('[E2E] 运行失败：', error)
  process.exitCode = 1
})
