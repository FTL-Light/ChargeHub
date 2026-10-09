const { getOrderDetail } = require('../../api/index.js')

Page({
  data: {
    orderId: null,
    order: {},
    loading: true
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ orderId: options.id })
      this.loadOrderDetail(options.id)
    }
  },

  // 加载订单详情
  loadOrderDetail(id) {
    this.setData({ loading: true })
    wx.showLoading({ title: '加载中' })
    getOrderDetail(id)
      .then((res) => {
        wx.hideLoading()
        const order = res || {}
        // 格式化时长
        if (order.duration != null) {
          order.durationText = this.formatDuration(order.duration)
        }
        this.setData({
          order: order,
          loading: false
        })
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ loading: false })
      })
  },

  // 格式化时长
  formatDuration(seconds) {
    seconds = parseInt(seconds) || 0
    const h = Math.floor(seconds / 3600)
    const m = Math.floor((seconds % 3600) / 60)
    const s = seconds % 60
    if (h > 0) {
      return h + '小时' + m + '分' + s + '秒'
    }
    if (m > 0) {
      return m + '分' + s + '秒'
    }
    return s + '秒'
  },

  // 复制订单号
  onCopyNo() {
    const no = this.data.order.orderNo || this.data.order.id
    if (!no) return
    wx.setClipboardData({
      data: String(no),
      success: () => {
        wx.showToast({ title: '已复制', icon: 'success' })
      }
    })
  }
})
