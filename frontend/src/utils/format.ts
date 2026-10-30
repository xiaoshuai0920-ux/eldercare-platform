/** 金额统一两位小数并带 ¥ 前缀，配合 tabular-nums 使用；非法值回退占位符 */
export function money(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—'
  const amount = typeof value === 'number' ? value : Number(value)
  return Number.isFinite(amount) ? `¥${amount.toFixed(2)}` : '—'
}

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']

function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 页头展示用的当天日期，取本机真实时间 */
export function today(): string {
  const date = new Date()
  return `${date.getFullYear()} 年 ${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${WEEKDAYS[date.getDay()]}`
}

/** 本地日期（YYYY-MM-DD），用于 date 输入框的默认值与最小可选值 */
export function todayIso(): string {
  const date = new Date()
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 本地时间（YYYY-MM-DDTHH:mm），用于 datetime-local 输入框 */
export function localDateTime(offsetMinutes = 0): string {
  const date = new Date(Date.now() + offsetMinutes * 60_000)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

/** 在 YYYY-MM-DD 上加/减天数，返回同样格式，避免跨月跨年手算 */
export function addDays(isoDate: string, days: number): string {
  const [year, month, day] = isoDate.split('-').map(Number)
  const date = new Date(year, month - 1, day + days)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 当前时间戳，集中一处便于测试与替换 */
export function nowMs(): number {
  return Date.now()
}

/** 判断「日期 + 时间」是否已早于当前时刻，用于提交前的本地校验 */
export function isPast(date: string, time: string): boolean {
  const normalized = time.length === 5 ? `${time}:00` : time
  const target = new Date(`${date}T${normalized}`)
  if (Number.isNaN(target.getTime())) return false
  return target.getTime() < Date.now()
}

/** 时长按小时与分钟组合展示，0 或负数回退占位符 */
export function minutesText(minutes?: number | null): string {
  if (!minutes || minutes <= 0) return '—'
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  if (hours === 0) return `${rest} 分钟`
  return rest === 0 ? `${hours} 小时` : `${hours} 小时 ${rest} 分钟`
}

/** 后端返回 ISO 时间（2026-10-10T09:00:00），统一展示为 10-10 09:00 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  const [date, time = ''] = value.split('T')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return '—'
  return `${date.slice(5)} ${time.slice(0, 5)}`.trim()
}

/** 完整时间，用于详情面板 */
export function formatFullDate(value?: string | null): string {
  if (!value) return '—'
  const [date, time = ''] = value.split('T')
  if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) return '—'
  return `${date} ${time.slice(0, 5)}`.trim()
}

export function toIsoLocal(date: string, time: string): string {
  return `${date}T${time.length === 5 ? `${time}:00` : time}`
}

/** 头像取首字：字段缺失时退回占位符，避免渲染期异常 */
export function initial(name?: string | null): string {
  return (name ?? '').trim().slice(0, 1) || '—'
}
