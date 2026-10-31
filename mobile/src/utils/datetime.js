// 后端时间字段有两种形态：业务时间多为本地 ISO 字符串（2026-10-31T09:00:00），
// 少数走 JDBC Timestamp 的字段是毫秒数。列表里直接显示原始值可读性很差，统一在这里格式化。
export function formatDateTime(value) {
  if (value === null || value === undefined || value === '') return ''
  if (typeof value === 'number') {
    const date = new Date(value)
    if (Number.isNaN(date.getTime())) return String(value)
    return withYear(date.getFullYear(), pad(date.getMonth() + 1), pad(date.getDate()), pad(date.getHours()), pad(date.getMinutes()))
  }
  const matched = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2})/.exec(String(value))
  if (!matched) return String(value)
  return withYear(Number(matched[1]), matched[2], matched[3], matched[4], matched[5])
}

function pad(value) {
  return String(value).padStart(2, '0')
}

// 当年订单/消息省略年份，省出空间给正文；跨年保留，避免把去年当成今年
function withYear(year, month, day, hour, minute) {
  const prefix = year === new Date().getFullYear() ? '' : `${year}-`
  return `${prefix}${month}-${day} ${hour}:${minute}`
}
