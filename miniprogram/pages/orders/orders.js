const { request, requireLogin } = require('../../utils/request')
Page({
  data: { items: [] },
  onShow() {
    requireLogin()
      .then(() => request('/mini/orders'))
      .then((items) => this.setData({ items }))
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
