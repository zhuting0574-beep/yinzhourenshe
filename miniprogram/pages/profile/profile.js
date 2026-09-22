const { request, login, bindPhone } = require('../../utils/request')
Page({
  data: { user: null, loggedIn: false, service: null, rulesVisible: false, rulesContent: '' },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    const app = getApp()
    Promise.all([request('/mini/content/SERVICE'), request('/mini/content/RULES')])
      .then(([service, rules]) => this.setData({ service: service[0] || null, rulesContent: rules[0]?.content || '' }))
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
  logout() {
    const app = getApp();
    app.globalData.token = '';
    app.globalData.user = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('user');
    this.setData({ user: null, loggedIn: false });
    wx.showToast({ title: '已退出登录', icon: 'success' });
  },
  navigate() {
    const s = this.data.service
    const latitude = Number(s && s.latitude)
    const longitude = Number(s && s.longitude)
    if (!s || !Number.isFinite(latitude) || !Number.isFinite(longitude)) return wx.showToast({ title: '服务点暂未配置地图位置', icon: 'none' })
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
