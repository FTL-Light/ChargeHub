const { submitFeedback, getFeedbackList } = require('../../api/index.js')

Page({
  data: {
    form: {
      type: '',
      title: '',
      content: '',
      phone: '',
      images: []
    },
    typeOptions: [
      { value: 'charger', label: '充电桩故障' },
      { value: 'environment', label: '环境问题' },
      { value: 'other', label: '其他' }
    ],
    typeIndex: 0,
    feedbackList: [],
    submitting: false,
    loading: false
  },

  onLoad() {
    this.loadFeedbackList()
  },

  // 加载历史反馈
  loadFeedbackList() {
    this.setData({ loading: true })
    getFeedbackList({ page: 1, pageSize: 20 })
      .then((res) => {
        const list = Array.isArray(res) ? res : (res && res.list) || (res && res.records) || []
        this.setData({
          feedbackList: list,
          loading: false
        })
      })
      .catch(() => {
        this.setData({ loading: false })
      })
  },

  // 类型选择
  onTypeChange(e) {
    const index = e.detail.value
    this.setData({
      typeIndex: index,
      'form.type': this.data.typeOptions[index].value
    })
  },

  // 标题输入
  onTitleInput(e) {
    this.setData({ 'form.title': e.detail.value })
  },

  // 内容输入
  onContentInput(e) {
    this.setData({ 'form.content': e.detail.value })
  },

  // 电话输入
  onPhoneInput(e) {
    this.setData({ 'form.phone': e.detail.value })
  },

  // 选择图片
  chooseImage() {
    const images = this.data.form.images
    if (images.length >= 4) {
      wx.showToast({ title: '最多上传4张图片', icon: 'none' })
      return
    }
    wx.chooseMedia({
      count: 4 - images.length,
      mediaType: ['image'],
      sourceType: ['album', 'camera'],
      success: (res) => {
        const newImages = res.tempFiles.map((file) => file.tempFilePath)
        this.setData({
          'form.images': images.concat(newImages)
        })
      },
      fail: () => {}
    })
  },

  // 预览图片
  previewImage(e) {
    const current = e.currentTarget.dataset.src
    wx.previewImage({
      current: current,
      urls: this.data.form.images
    })
  },

  // 删除图片
  deleteImage(e) {
    const index = e.currentTarget.dataset.index
    const images = this.data.form.images
    images.splice(index, 1)
    this.setData({ 'form.images': images })
  },

  // 提交反馈
  submitFeedback() {
    if (this.data.submitting) return
    const form = this.data.form
    if (!form.type) {
      form.type = this.data.typeOptions[this.data.typeIndex].value
    }
    if (!form.title.trim()) {
      wx.showToast({ title: '请输入标题', icon: 'none' })
      return
    }
    if (!form.content.trim()) {
      wx.showToast({ title: '请输入问题描述', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    wx.showLoading({ title: '提交中' })
    submitFeedback({
      type: form.type,
      title: form.title,
      content: form.content,
      phone: form.phone,
      images: form.images
    })
      .then(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
        wx.showToast({ title: '提交成功', icon: 'success' })
        // 重置表单
        this.setData({
          form: {
            type: '',
            title: '',
            content: '',
            phone: '',
            images: []
          },
          typeIndex: 0
        })
        this.loadFeedbackList()
      })
      .catch(() => {
        wx.hideLoading()
        this.setData({ submitting: false })
      })
  }
})
