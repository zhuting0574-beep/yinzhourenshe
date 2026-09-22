import { request, login, scanActivity, bindPhone } from '../../utils/request'
Page({
  data: { user: null as any, loggedIn: false, service: null as any, rulesVisible: false, rulesContent: '' },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    const app = getApp<IAppOption>()
    Promise.all([request<any[]>('/mini/content/SERVICE'), request<any[]>('/mini/content/RULES')])
      .then(([service, rules]) => this.setData({ service: service[0] || null, rulesContent: rules[0]?.content || '' }))
      .catch(() => {})
    if (!app.globalData.token) return this.setData({ user: null, loggedIn: false })
    request<any>('/mini/profile')
      .then((user) => this.setData({ user, loggedIn: true }))
      .catch(() => this.setData({ user: null, loggedIn: false }))
  },
  login() {
    login().then((user) => this.setData({ user, loggedIn: true }))
  },
  phone(e: any) {
    if (!e.detail.code) return wx.showToast({ title: '未授权手机号', icon: 'none' })
    bindPhone(e.detail.code).then((user) => {
      this.setData({ user, loggedIn: true })
      wx.showToast({ title: '绑定成功，获得100积分' })
    })
  },
  logout() {
    const app = getApp<IAppOption>()
    app.globalData.token = ''
    app.globalData.user = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('user')
    this.setData({ user: null, loggedIn: false })
    wx.showToast({ title: '已退出登录', icon: 'success' })
  },
  navigate() {
    const s: any = this.data.service
    const latitude = Number(s?.latitude)
    const longitude = Number(s?.longitude)
    if (!s || !Number.isFinite(latitude) || !Number.isFinite(longitude)) {
      return wx.showToast({ title: '服务点暂未配置地图位置', icon: 'none' })
    }
    wx.openLocation({ latitude, longitude, name: s.title || '鄞领π服务点', address: s.address || '' })
  },
  showRules() {
    if (!this.data.rulesContent) return wx.showToast({ title: '积分规则暂未配置', icon: 'none' })
    this.setData({ rulesVisible: true })
  },
  closeRules() {
    this.setData({ rulesVisible: false })
  },
  noop() {},
})
