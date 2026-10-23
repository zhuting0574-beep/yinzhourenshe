Page({
  data: { failed: false },
  goBack() {
    if (getCurrentPages().length > 1) wx.navigateBack()
    else this.goHome()
  },
  goHome() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  onError() { this.setData({ failed: true }) },
  copyUrl() {
    wx.setClipboardData({ data: 'https://beian.miit.gov.cn/', success: () => wx.showToast({ title: '查询网址已复制', icon: 'none' }) })
  },
})
