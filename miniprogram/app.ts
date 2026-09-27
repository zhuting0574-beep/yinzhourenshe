App<IAppOption>({
  globalData: { apiBase: 'https://api.milsimnb.cn/api', token: '', user: null, pendingRoute: '' },
  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.user = wx.getStorageSync('user') || null
  },
})
