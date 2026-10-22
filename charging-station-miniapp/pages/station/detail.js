const { getStationDetail } = require('../../api/index.js')

Page({
  data: {
    stationId: null,
    station: {},
    chargers: [],
    loading: true
  },

  onLoad(options) {
    if (options.id) {
      this.setData({ stationId: options.id })
      this.loadStationDetail(options.id)
    }
  },

  // 加载站点详情
  loadStationDetail(id) {
    this.setData({ loading: true })
    wx.showLoading({ title: '加载中' })
    getStationDetail(id)
      .then((res) => {
        wx.hideLoading()
        const station = res && res.station ? res.station : (res || {})
        const chargers = res && res.chargers ? res.chargers : (station.chargers || [])
        this.setData({
          station: station,
          chargers: chargers,
          loading: false
        })
        wx.setNavigationBarTitle({ title: station.name || '站点详情' })
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ loading: false })
      })
  },

  // 点击充电桩
  onChargerTap(e) {
    const charger = e.currentTarget.dataset.charger
    // 与后端一致: 0-故障, 1-空闲, 2-使用中
    if (charger.status !== 1) {
      const tip = charger.status === 2 ? '该充电桩使用中' : '该充电桩当前不可用'
      wx.showToast({ title: tip, icon: 'none' })
      return
    }
    wx.navigateTo({
      url: '/pages/charging/index?chargerId=' + charger.id
    })
  },

  // 导航
  onNavigateTap() {
    const station = this.data.station
    if (!station.latitude || !station.longitude) {
      wx.showToast({ title: '暂无位置信息', icon: 'none' })
      return
    }
    wx.openLocation({
      latitude: parseFloat(station.latitude),
      longitude: parseFloat(station.longitude),
      name: station.name || '充电站',
      address: station.address || '',
      scale: 18
    })
  },

  // 拨打电话
  onCallTap() {
    const phone = this.data.station.phone || this.data.station.contactPhone
    if (!phone) {
      wx.showToast({ title: '暂无联系电话', icon: 'none' })
      return
    }
    wx.makePhoneCall({
      phoneNumber: phone
    })
  },

  // 获取充电桩状态文本
  getStatusText(status) {
    const map = { 0: '空闲', 1: '使用中', 2: '故障', 3: '离线' }
    return map[status] || status
  }
})
