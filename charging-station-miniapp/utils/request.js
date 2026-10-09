// 请求封装
const app = getApp()

/**
 * 基础请求方法
 * @param {String} url 请求地址（相对路径）
 * @param {Object} options 请求配置 { method, data, header, ... }
 * @returns {Promise}
 */
function request(url, options = {}) {
  const baseUrl = app.globalData.baseUrl
  const token = app.globalData.token || wx.getStorageSync('token') || ''

  return new Promise((resolve, reject) => {
    wx.request({
      url: url.startsWith('http') ? url : baseUrl + url,
      method: options.method || 'GET',
      data: options.data || {},
      header: Object.assign(
        {
          'Content-Type': 'application/json'
        },
        options.header || {},
        token ? { Authorization: 'Bearer ' + token } : {}
      ),
      timeout: options.timeout || 15000,
      success(res) {
        const statusCode = res.statusCode
        const data = res.data

        // HTTP 401 未授权
        if (statusCode === 401) {
          wx.showToast({
            title: '登录已过期，请重新登录',
            icon: 'none',
            duration: 2000
          })
          // 清除本地登录信息
          app.globalData.token = ''
          app.globalData.userInfo = null
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          // 跳转登录页
          setTimeout(() => {
            wx.navigateTo({ url: '/pages/login/index' })
          }, 1500)
          reject(new Error('未授权，请重新登录'))
          return
        }

        // HTTP 非 2xx
        if (statusCode < 200 || statusCode >= 300) {
          const msg = (data && data.msg) || '请求失败：' + statusCode
          wx.showToast({ title: msg, icon: 'none' })
          reject(new Error(msg))
          return
        }

        // 业务层 code 判断（后端统一返回 {code, msg, data}）
        if (data && typeof data === 'object' && 'code' in data) {
          if (data.code === 200) {
            resolve(data.data !== undefined ? data.data : data)
          } else if (data.code === 401) {
            wx.showToast({ title: '登录已过期，请重新登录', icon: 'none' })
            app.globalData.token = ''
            app.globalData.userInfo = null
            wx.removeStorageSync('token')
            wx.removeStorageSync('userInfo')
            setTimeout(() => {
              wx.navigateTo({ url: '/pages/login/index' })
            }, 1500)
            reject(new Error(data.msg || '未授权'))
          } else {
            wx.showToast({
              title: data.msg || '请求失败',
              icon: 'none'
            })
            reject(new Error(data.msg || '请求失败'))
          }
        } else {
          // 直接返回数据
          resolve(data)
        }
      },
      fail(err) {
        wx.showToast({
          title: '网络异常，请稍后重试',
          icon: 'none'
        })
        reject(err)
      }
    })
  })
}

// 快捷方法
function get(url, data, options = {}) {
  return request(url, Object.assign({}, options, { method: 'GET', data }))
}

function post(url, data, options = {}) {
  return request(url, Object.assign({}, options, { method: 'POST', data }))
}

function put(url, data, options = {}) {
  return request(url, Object.assign({}, options, { method: 'PUT', data }))
}

function del(url, data, options = {}) {
  return request(url, Object.assign({}, options, { method: 'DELETE', data }))
}

module.exports = {
  request,
  get,
  post,
  put,
  del,
  BASE_URL: app ? app.globalData.baseUrl : 'http://localhost:8080/api'
}
