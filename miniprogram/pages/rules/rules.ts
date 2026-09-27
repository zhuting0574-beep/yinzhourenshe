import { request } from '../../utils/request'
Page({
  data: { rules: [], service: [] },
  onLoad() {
    request<any[]>('/mini/content/RULES', { errorTitle: '积分规则获取失败' })
      .then((rules) => this.setData({ rules }))
      .catch((error) => console.warn('Points rules request failed', error))
    request<any[]>('/mini/content/SERVICE', { silent: true })
      .then((service) => this.setData({ service }))
      .catch((error) => console.warn('Service content request failed', error))
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  navigate() {
    const s: any = this.data.service[0]
    if (s)
      wx.openLocation({
        latitude: Number(s.latitude),
        longitude: Number(s.longitude),
        name: s.title,
        address: s.address,
      })
  },
})
