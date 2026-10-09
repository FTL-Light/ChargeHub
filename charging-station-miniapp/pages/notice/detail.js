const { getNoticeDetail } = require('../../api/index.js')

Page({
  data: {
    noticeId: null,
    notice: {},
    loading: true
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ noticeId: options.id })
      this.loadNoticeDetail(options.id)
    }
  },

  // 加载公告详情
  loadNoticeDetail(id) {
    this.setData({ loading: true })
    wx.showLoading({ title: '加载中' })
    getNoticeDetail(id)
      .then((res) => {
        wx.hideLoading()
        const notice = res || {}
        this.setData({
          notice: notice,
          loading: false
        })
        wx.setNavigationBarTitle({ title: notice.title || '公告详情' })
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ loading: false })
      })
  }
})
