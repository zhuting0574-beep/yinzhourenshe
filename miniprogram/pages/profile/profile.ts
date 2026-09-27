import { request, bindPhone } from '../../utils/request'
Page({
  data: { user: null as any, loggedIn: false, profileComplete: false, service: null as any, rulesVisible: false, rulesContent: '' },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    const app = getApp<IAppOption>()
    request<any[]>('/mini/content/SERVICE', { silent: true })
      .then((service) => this.setData({ service: service[0] || null }))
      .catch((error) => console.warn('Service content request failed', error))
    request<any[]>('/mini/content/RULES', { errorTitle: '积分规则获取失败' })
      .then((rules) => this.setData({ rulesContent: rules[0]?.content || '' }))
      .catch((error) => console.warn('Points rules request failed', error))
    if (!app.globalData.token) return this.setData({ user: null, loggedIn: false, profileComplete: false })
    request<any>('/mini/profile')
      .then((user) => this.setData({ user, loggedIn: true, profileComplete: isProfileComplete(user) }))
      .catch(() => this.setData({ user: null, loggedIn: false, profileComplete: false }))
  },
  openLogin() {
    if (this.data.loggedIn) return
    wx.navigateTo({ url: '/pages/login/login' })
  },
  editProfile() {
    if (!this.data.loggedIn) return this.openLogin()
    wx.navigateTo({ url: '/pages/profile-edit/profile-edit' })
  },
  phone(e: any) {
    if (!e.detail.code) return wx.showToast({ title: '未授权手机号', icon: 'none' })
    bindPhone(e.detail.code).then((user) => {
      this.setData({ user, loggedIn: true, profileComplete: isProfileComplete(user) })
      wx.showToast({ title: '绑定成功，获得100积分' })
    })
  },
  logout() {
    const app = getApp<IAppOption>()
    app.globalData.token = ''
    app.globalData.user = null
    wx.removeStorageSync('token')
    wx.removeStorageSync('user')
    this.setData({ user: null, loggedIn: false, profileComplete: false })
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

function isProfileComplete(user: any) {
  if (!user) return false
  if (typeof user.profileComplete === 'boolean') return user.profileComplete
  if (typeof user.profile_complete === 'boolean') return user.profile_complete
  const values = [user.realName || user.real_name, user.age, user.graduationSchool || user.graduation_school, user.majorDirection || user.major_direction, user.city]
  return values.every((value) => value !== null && value !== undefined && String(value).trim() !== '')
}
