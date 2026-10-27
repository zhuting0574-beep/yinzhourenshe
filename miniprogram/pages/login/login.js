const { login } = require('../../utils/request')
const app = getApp()

Page({
  data: { submitting: false },
  back() {
    app.globalData.pendingRoute = ''
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) })
  },
  submit() {
    if (this.data.submitting) return
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
