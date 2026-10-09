const { getNoticeList } = require('../../api/index.js')

Page({
  data: {
    notices: [],
    loading: true,
    page: 1,
    pageSize: 10,
    hasMore: true
  },

  onLoad() {
    this.loadNotices()
  },

  // 加载公告列表
  loadNotices() {
    if (!this.data.hasMore && this.data.page > 1) return
    this.setData({ loading: true })
    getNoticeList({ page: this.data.page, pageSize: this.data.pageSize })
      .then((res) => {
        const list = Array.isArray(res) ? res : (res && res.list) || (res && res.records) || []
        const total = (res && res.total) || list.length
        const notices = this.data.page === 1 ? list : this.data.notices.concat(list)
        this.setData({
          notices: notices,
          loading: false,
          hasMore: notices.length < total
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 上拉加载
  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 })
      this.loadNotices()
    }
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.setData({ page: 1, notices: [], hasMore: true })
    this.loadNotices()
    setTimeout(() => wx.stopPullDownRefresh(), 1000)
  },

  // 跳转公告详情
  onNoticeTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/notice/detail?id=' + id
    })
  }
})
