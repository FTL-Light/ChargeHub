const { getNearbyStations } = require('../../api/index.js')

Page({
  data: {
    stations: [],
    loading: true,
    latitude: null,
    longitude: null,
    currentCity: '定位中...'
  },

  onLoad() {
    this.getLocation()
  },

  onShow() {
    // 每次显示刷新列表
    if (this.data.latitude) {
      this.loadStations()
    }
  },

  // 获取定位
  getLocation() {
    this.setData({ loading: true })
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        const { latitude, longitude } = res
        this.setData({
          latitude,
          longitude,
          currentCity: '当前位置'
        })
        this.loadStations()
      },
      fail: () => {
        this.setData({ loading: false, currentCity: '定位失败' })
        wx.showToast({
          title: '定位失败，已显示全部站点',
          icon: 'none'
        })
        // 定位失败也加载站点列表
        this.loadStations()
      }
    })
  },

  // 加载附近站点
  loadStations() {
    this.setData({ loading: true })
    const params = {
      latitude: this.data.latitude || '',
      longitude: this.data.longitude || ''
    }
    getNearbyStations(params)
      .then((res) => {
        const list = Array.isArray(res) ? res : (res && res.list) || (res && res.records) || []
        this.setData({
          stations: list,
          loading: false
        })
      })
      .catch(() => {
        this.setData({ loading: false, stations: [] })
      })
  },

  // 跳转站点详情
  onStationTap(e) {
    const id = e.currentTarget.dataset.id
    wx.navigateTo({
      url: '/pages/station/detail?id=' + id
    })
  },

  // 扫码充电
  onScanCharge() {
    wx.scanCode({
      onlyFromCamera: false,
      scanType: ['qrCode'],
      success: (res) => {
        // 解析扫码结果，提取充电桩ID
        let chargerId = ''
        try {
          if (res.result && res.result.indexOf('chargerId=') > -1) {
            chargerId = res.result.split('chargerId=')[1].split('&')[0]
          } else if (res.result && res.result.indexOf('id=') > -1) {
            chargerId = res.result.split('id=')[1].split('&')[0]
          } else {
            chargerId = res.result
          }
        } catch (e) {
          chargerId = res.result
        }
        if (!chargerId) {
          wx.showToast({ title: '无效的二维码', icon: 'none' })
          return
        }
        wx.navigateTo({
          url: '/pages/charging/index?chargerId=' + chargerId
        })
      },
      fail: () => {
        wx.showToast({ title: '扫码已取消', icon: 'none' })
      }
    })
  },

  // 跳转搜索
  onSearchTap() {
    wx.showToast({
      title: '搜索功能开发中',
      icon: 'none'
    })
  },

  // 下拉刷新
  onPullDownRefresh() {
    this.getLocation()
    setTimeout(() => {
      wx.stopPullDownRefresh()
    }, 1000)
  }
})
