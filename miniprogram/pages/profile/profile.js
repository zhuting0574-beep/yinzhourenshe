const { request, login, bindPhone } = require('../../utils/request')
Page({
  data: { user: null, loggedIn: false, service: null },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    const app = getApp()
    request('/mini/content/SERVICE')
      .then((items) => this.setData({ service: items[0] || null }))
      .catch(() => {})
    if (!app.globalData.token) return this.setData({ user: null, loggedIn: false })
    request('/mini/profile')
      .then((user) => this.setData({ user, loggedIn: true }))
      .catch(() => this.setData({ user: null, loggedIn: false }))
  },
  login() {
    login().then((user) => this.setData({ user, loggedIn: true }))
  },
  phone(e) {
    if (!e.detail.code) return wx.showToast({ title: '未授权手机号', icon: 'none' })
    bindPhone(e.detail.code).then((user) => {
      this.setData({ user, loggedIn: true })
      wx.showToast({ title: '绑定成功，获得100积分' })
    })
  },
  navigate() {
    const s = this.data.service
    if (s)
      wx.openLocation({
        latitude: Number(s.latitude),
        longitude: Number(s.longitude),
        name: s.title,
        address: s.address,
      })
    else wx.navigateTo({ url: '/pages/rules/rules' })
  },
})
