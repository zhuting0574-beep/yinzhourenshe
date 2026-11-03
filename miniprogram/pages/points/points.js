const { request, requireLogin } = require('../../utils/request')
Page({
  data: { items: [], points: 0 },
  onShow() {
    requireLogin()
      .then(() => Promise.all([request('/mini/profile/points'), request('/mini/profile')]))
      .then(([items, user]) => this.setData({ items, points: user.points }))
  },
  back() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }) })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
