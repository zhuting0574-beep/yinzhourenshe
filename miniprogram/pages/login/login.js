const { login, request } = require('../../utils/request')
const app = getApp()

Page({
  data: { submitting: false, serviceAgreed: false, privacyAgreed: false, agreementVisible: false, agreementTitle: '', agreementContent: '', serviceAgreement: '', privacyAgreement: '' },
  onLoad() {
    Promise.all([
      request('/mini/content/USER_SERVICE_AGREEMENT', { silent: true }),
      request('/mini/content/PRIVACY_POLICY', { silent: true }),
    ]).then(([service, privacy]) => this.setData({ serviceAgreement: service?.[0]?.content || '', privacyAgreement: privacy?.[0]?.content || '' })).catch((error) => {
      console.warn('Agreement content request failed', error)
      wx.showToast({ title: '协议加载失败，请检查网络后重试', icon: 'none' })
    })
  },
  agreementChange(event) {
    const values = event.detail.value || []
    this.setData({ serviceAgreed: values.includes('service'), privacyAgreed: values.includes('privacy') })
  },
  openAgreement(event) {
    const type = event.currentTarget.dataset.type
    this.setData({ agreementVisible: true, agreementTitle: type === 'service' ? '用户服务协议' : '隐私政策', agreementContent: type === 'service' ? (this.data.serviceAgreement || '<p>协议内容加载中，请稍后重试。</p>') : (this.data.privacyAgreement || '<p>协议内容加载中，请稍后重试。</p>') })
  },
  closeAgreement() { this.setData({ agreementVisible: false }) },
  stopPropagation() {},
  back() {
    app.globalData.pendingRoute = ''
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) })
  },
  submit() {
    if (this.data.submitting) return
    if (!this.data.serviceAgreed || !this.data.privacyAgreed) {
      wx.showToast({ title: '请先阅读并同意两项协议', icon: 'none' })
      return
    }
    this.setData({ submitting: true })
    login()
      .then(() => {
        wx.showToast({ title: '登录成功', icon: 'success' })
        const pendingRoute = app.globalData.pendingRoute
        app.globalData.pendingRoute = ''
        if (pendingRoute) {
          wx.navigateBack({
            fail: () => wx.redirectTo({ url: pendingRoute }),
          })
          return
        }
        wx.switchTab({ url: '/pages/profile/profile' })
      })
      .catch((error) =>
        wx.showModal({
          title: '登录失败',
          content: error?.message || error?.errMsg || '登录失败，请重试',
          showCancel: false,
        })
      )
      .finally(() => this.setData({ submitting: false }))
  },
})
