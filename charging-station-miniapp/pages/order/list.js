const { getOrderList } = require('../../api/index.js')

Page({
  data: {
    orders: [],
    loading: true,
    page: 1,
    pageSize: 10,
    hasMore: true,
    currentTab: 0,
    tabs: [
      { name: '全部', value: '' },
      { name: '充电中', value: 'charging' },
      { name: '已完成', value: 'completed' },
      { name: '已取消', value: 'cancelled' }
    ]
  },

  onLoad() {
    this.loadOrders()
  },

  onShow() {
    // 切换tab回来时刷新
    this.setData({ page: 1, orders: [], hasMore: true })
    this.loadOrders()
  },

  // 切换tab
  onTabChange(e) {
    const index = e.currentTarget.dataset.index
    if (index === this.data.currentTab) return
    this.setData({
      currentTab: index,
      page: 1,
      orders: [],
      hasMore: true,
      loading: true
    })
    this.loadOrders()
  },

  // 加载订单
  loadOrders() {
    if (!this.data.hasMore && this.data.page > 1) return
    this.setData({ loading: true })
    const tab = this.data.tabs[this.data.currentTab]
    const params = {
      page: this.data.page,
      pageSize: this.data.pageSize,
      status: tab.value
    }
    getOrderList(params)
      .then((res) => {
        const list = Array.isArray(res) ? res : (res && res.list) || (res && res.records) || []
        const total = (res && res.total) || list.length
        const orders = this.data.page === 1 ? list : this.data.orders.concat(list)
        this.setData({
          orders: orders,
          loading: false,
          hasMore: orders.length < total
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 上拉加载更多
  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.setData({ page: this.data.page + 1 })
      this.loadOrders()
    }
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.setData({ page: 1, orders: [], hasMore: true })
    this.loadOrders()
    setTimeout(() => wx.stopPullDownRefresh(), 1000)
  },

  // 跳转订单详情
  onOrderTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/order/detail?id=' + id
    })
  },

  // 获取状态文本
  getStatusText(status) {
    const map = {
      charging: '充电中',
      completed: '已完成',
      cancelled: '已取消',
      pending: '待支付'
    }
    return map[status] || status
  }
})
