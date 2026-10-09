const { getUserInfo, getUserBalance } = require('../../api/index.js')

const app = getApp()

Page({
  data: {
    userInfo: null,
    isLoggedIn: false,
    balance: '0.00',
    menuList: [
      { id: 'orders', icon: '📋', name: '我的订单', path: '/pages/order/list' },
      { id: 'feedback', icon: '💬', name: '故障反馈', path: '/pages/feedback/index' },
      { id: 'notice', icon: '📢', name: '系统公告', path: '/pages/notice/list' },
      { id: 'ai', icon: '🤖', name: 'AI客服', path: '/pages/ai/index' },
      { id: 'about', icon: 'ℹ️', name: '关于我们', path: '' }
    ]
  },

  onShow() {
    this.checkLoginStatus()
  },

  // 检查登录状态
  checkLoginStatus() {
    const token = app.globalData.token || wx.getStorageSync('token')
    const userInfo = app.globalData.userInfo || wx.getStorageSync('userInfo')
    if (token && userInfo) {
      this.setData({
        isLoggedIn: true,
        userInfo: userInfo
      })
      this.loadUserInfo()
      this.loadBalance()
    } else {
      this.setData({
        isLoggedIn: false,
        userInfo: null,
        balance: '0.00'
      })
    }
  },

  // 加载用户信息
  loadUserInfo() {
    getUserInfo()
      .then((res) => {
        if (res) {
          const userInfo = Object.assign({}, this.data.userInfo, res)
          this.setData({ userInfo: userInfo })
          app.globalData.userInfo = userInfo
          wx.setStorageSync('userInfo', userInfo)
        }
      })
      .catch(() => {})
  },

  // 加载余额
  loadBalance() {
    getUserBalance()
      .then((res) => {
        const balance = (res && res.balance != null) ? res.balance : (typeof res === 'number' ? res : 0)
        this.setData({ balance: parseFloat(balance).toFixed(2) })
      })
      .catch(() => {})
  },

  // 跳转登录
  onLoginTap() {
    wx.navigateTo({
      url: '/pages/login/index'
    })
  },

  // 跳转充值
  onRechargeTap() {
    if (!this.data.isLoggedIn) {
      this.onLoginTap()
      return
    }
    wx.navigateTo({
      url: '/pages/recharge/index'
    })
  },

  // 菜单点击
  onMenuTap(e) {
    const menu = e.currentTarget.dataset.menu
    if (!this.data.isLoggedIn && menu.id !== 'about') {
      this.onLoginTap()
      return
    }
    if (menu.id === 'about') {
      wx.showModal({
        title: '关于我们',
        content: '新能源充电站管理平台\n为您提供便捷的充电服务\n版本：v1.0.0',
        showCancel: false,
        confirmText: '确定'
      })
      return
    }
    if (menu.path) {
      // tabBar 页面用 switchTab
      if (menu.id === 'orders') {
        wx.switchTab({ url: menu.path })
      } else {
        wx.navigateTo({ url: menu.path })
      }
    }
  },

  // 退出登录
  onLogoutTap() {
    wx.showModal({
      title: '提示',
      content: '确定要退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.globalData.token = ''
          app.globalData.userInfo = null
          wx.removeStorageSync('token')
          wx.removeStorageSync('userInfo')
          this.setData({
            isLoggedIn: false,
            userInfo: null,
            balance: '0.00'
          })
          wx.showToast({ title: '已退出登录', icon: 'success' })
        }
      }
    })
  }
})
