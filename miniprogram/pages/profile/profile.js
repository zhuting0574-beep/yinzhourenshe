const { request, login, bindPhone, updateWechatProfile } = require('../../utils/request')
Page({
  data: { user: null, loggedIn: false, profileComplete: false, service: null, rulesVisible: false, rulesContent: '' },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 4 })
    const app = getApp()
    request('/mini/content/SERVICE', { silent: true })
      .then((service) => this.setData({ service: service[0] || null }))
      .catch((error) => console.warn('Service content request failed', error))
    request('/mini/content/RULES', { errorTitle: '积分规则获取失败' })
      .then((rules) => this.setData({ rulesContent: rules[0]?.content || '' }))
      .catch((error) => console.warn('Points rules request failed', error))
    if (!app.globalData.token) return this.setData({ user: null, loggedIn: false, profileComplete: false })
    request('/mini/profile')
      .then((user) => {
        app.globalData.user = Object.assign({}, app.globalData.user || {}, user)
        const registered = isRegisteredUser(user)
        this.setData({ user: registered ? user : null, loggedIn: registered, profileComplete: registered && isProfileComplete(user) })
      })
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
  phone(e) {
    const code = e.detail && e.detail.code
    if (!code) {
      const detail = String(e.detail && e.detail.errMsg || '')
      console.warn('[getPhoneNumber failed]', e.detail)
      return showPhonePermissionHelp(detail)
    }
    const signedIn = login().then(() => undefined)
    signedIn
      .then(() => bindPhone(code))
      .then((user) => user.phoneAlreadyRegistered ? user : getWechatProfile().then((profile) => isUsableWechatProfile(profile) ? updateWechatProfile(profile.nickName, profile.avatarUrl) : user))
      .then((user) => {
        this.setData({ user, loggedIn: true, profileComplete: isProfileComplete(user) })
        wx.showToast({ title: '注册成功', icon: 'success' })
      })
      .catch((error) => wx.showToast({ title: error && error.message || '手机号授权失败，请重试', icon: 'none' }))
  },
  logout() {
    const app = getApp();
    app.globalData.token = '';
    app.globalData.user = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('user');
    this.setData({ user: null, loggedIn: false, profileComplete: false });
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

function getWechatProfile() {
  if (typeof wx.getUserProfile !== 'function') return Promise.resolve(null)
  return new Promise((resolve) => wx.getUserProfile({
    desc: '用于展示您的微信昵称和头像',
    success: (result) => resolve(result.userInfo),
    fail: () => resolve(null),
  }))
}

function isProfileComplete(user) {
  if (!user) return false
  if (typeof user.profileComplete === 'boolean') return user.profileComplete
  if (typeof user.profile_complete === 'boolean') return user.profile_complete
  const values = [user.realName || user.real_name, user.age, user.graduationSchool || user.graduation_school, user.majorDirection || user.major_direction, user.city]
  return values.every((value) => value !== null && value !== undefined && String(value).trim() !== '')
}

function isRegisteredUser(user) {
  return !!user && !!String(user.phone || '').trim()
}

function showPhonePermissionHelp(detail) {
  const denied = /deny|cancel|user deny/i.test(detail)
  const unavailable = /no permission|not authorized|not support|invalid|fail/i.test(detail) && !denied
  const reason = detail ? `\n\n微信返回：${detail}` : ''
  wx.showModal({
    title: '手机号授权失败',
    content: denied
      ? '你拒绝了手机号授权。手机号权限没有单独的微信设置开关，请重新点击按钮，在微信弹窗中选择允许。'
      : unavailable
        ? `当前小程序可能未开通微信手机号授权能力，请管理员在微信公众平台确认后再试。${reason}`
        : `微信没有返回手机号授权凭证，请重新点击按钮。${reason}`,
    confirmText: '知道了',
    showCancel: false,
  })
}

function isUsableWechatProfile(profile) {
  return !!profile && String(profile.nickName || '').trim() && !['微信用户', '测试用户', '未设置昵称'].includes(String(profile.nickName).trim()) && String(profile.avatarUrl || '').trim()
}
