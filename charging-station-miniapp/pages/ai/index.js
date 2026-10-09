const { aiChat } = require('../../api/index.js')

Page({
  data: {
    messages: [],
    inputMessage: '',
    scrollToView: '',
    isLoading: false,
    sessionId: '',
    quickQuestions: [
      '附近有哪些充电站？',
      '充电价格是多少？',
      '如何充值账户余额？',
      '充电桩故障如何报修？'
    ]
  },

  onLoad() {
    // 生成sessionId
    const sessionId = 'session_' + Date.now() + '_' + Math.floor(Math.random() * 10000)
    this.setData({ sessionId: sessionId })
    // 添加欢迎消息
    this.addMessage('assistant', '您好！我是AI智能客服，有什么可以帮您吗？您可以问我充电站位置、充电价格、账户充值、故障报修等问题。')
  },

  // 输入处理
  onInput(e) {
    this.setData({ inputMessage: e.detail.value })
  },

  // 添加消息
  addMessage(role, content) {
    const messages = this.data.messages
    const msg = {
      id: 'msg_' + Date.now() + '_' + Math.floor(Math.random() * 1000),
      role: role,
      content: content,
      time: this.formatTime(new Date())
    }
    messages.push(msg)
    this.setData({
      messages: messages,
      scrollToView: msg.id
    })
  },

  // 发送消息
  sendMessage() {
    const content = this.data.inputMessage.trim()
    if (!content || this.data.isLoading) return
    this.addMessage('user', content)
    this.setData({
      inputMessage: '',
      isLoading: true
    })
    aiChat({
      sessionId: this.data.sessionId,
      message: content
    })
      .then((res) => {
        const reply = (res && (res.reply || res.content || res.message || res.data)) || '抱歉，我没有理解您的问题，请尝试换一种提问方式。'
        this.addMessage('assistant', reply)
        this.setData({ isLoading: false })
      })
      .catch(() => {
        this.addMessage('assistant', '抱歉，服务暂时不可用，请稍后再试。')
        this.setData({ isLoading: false })
      })
  },

  // 快捷问题
  onQuickQuestion(e) {
    const question = e.currentTarget.dataset.question
    this.setData({ inputMessage: question })
    this.sendMessage()
  },

  // 格式化时间
  formatTime(date) {
    const pad = (n) => (n < 10 ? '0' + n : '' + n)
    return pad(date.getHours()) + ':' + pad(date.getMinutes())
  }
})
