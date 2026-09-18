const { request } = require('../../utils/request')
Page({
  data: { rules: [], service: [] },
  onLoad() {
    Promise.all([request('/mini/content/RULES'), request('/mini/content/SERVICE')]).then(
      ([rules, service]) => this.setData({ rules, service })
    )
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  navigate() {
    const s = this.data.service[0]
    if (s)
      wx.openLocation({
        latitude: Number(s.latitude),
        longitude: Number(s.longitude),
        name: s.title,
        address: s.address,
      })
  },
})
