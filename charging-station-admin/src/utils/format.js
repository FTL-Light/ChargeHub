/**
 * 时间格式化
 * @param {string|number|Date} time 时间
 * @param {string} pattern 格式
 */
export function formatTime(time, pattern = '{y}-{m}-{d} {h}:{i}:{s}') {
  if (!time) return '-'
  let date
  if (typeof time === 'object') {
    date = time
  } else {
    if (typeof time === 'string') {
      time = time.replace(/-/g, '/')
    }
    if (typeof time === 'number' && String(time).length === 10) {
      time = time * 1000
    }
    date = new Date(time)
  }
  if (isNaN(date.getTime())) return '-'

  const formatObj = {
    y: date.getFullYear(),
    m: date.getMonth() + 1,
    d: date.getDate(),
    h: date.getHours(),
    i: date.getMinutes(),
    s: date.getSeconds(),
    a: date.getDay()
  }
  const week = ['日', '一', '二', '三', '四', '五', '六']
  return pattern.replace(/{(y|m|d|h|i|s|a)+}/g, (result, key) => {
    let value = formatObj[key]
    if (key === 'a') {
      return ['日', '一', '二', '三', '四', '五', '六'][value]
    }
    if (result.length > 0 && value < 10) {
      value = '0' + value
    }
    return value || 0
  })
}

/**
 * 格式化时长(分钟转小时分)
 * @param {number} minutes 分钟
 */
export function formatDuration(minutes) {
  if (!minutes && minutes !== 0) return '-'
  if (minutes < 60) return minutes + '分钟'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return m > 0 ? `${h}小时${m}分钟` : `${h}小时`
}

/**
 * 金额格式化(分转元)
 */
export function formatMoney(fen) {
  if (fen === null || fen === undefined) return '0.00'
  return (fen / 100).toFixed(2)
}
