import { request, requireLogin } from '../../utils/request'
Page({
  data: { items: [], points: 0 },
  onShow() {
    requireLogin()
      .then(() =>
        Promise.all([request<any[]>('/mini/profile/points'), request<any>('/mini/profile')])
      )
      .then(([items, user]) => this.setData({ items, points: user.points }))
  },
  back() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }) })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})
