App<IAppOption>({
  globalData: { apiBase: 'https://api.milsimnb.cn/api', token: '', user: null, pendingRoute: '' },
  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.user = wx.getStorageSync('user') || null
    const stored = this.globalData.user as any
    if (stored && (stored.nickname === '测试用户' || stored.phone === '13800000000' || String(stored.openid || '').startsWith('dev:'))) {
      this.globalData.token = ''
      this.globalData.user = null
      wx.removeStorageSync('token')
      wx.removeStorageSync('user')
    }
  },
})
