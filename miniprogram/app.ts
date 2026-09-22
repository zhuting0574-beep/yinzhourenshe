App<IAppOption>({
  globalData: { apiBase: 'http://localhost:8081/api', token: '', user: null, pendingRoute: '' },
  onLaunch() {
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.user = wx.getStorageSync('user') || null
  },
})
