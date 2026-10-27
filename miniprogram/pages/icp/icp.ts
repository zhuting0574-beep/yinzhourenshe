const queryUrl = 'https://beian.miit.gov.cn/'
Page({
  goBack() {
    if (getCurrentPages().length > 1) wx.navigateBack()
    else this.goHome()
  },
  goHome() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  copyUrl() {
    wx.setClipboardData({ data: queryUrl, success: () => wx.showToast({ title: '查询网址已复制', icon: 'none' }) })
  },
  openQuery() {
    wx.showModal({
      title: '备案查询',
      content: '微信小程序暂不支持直接打开工信部查询网站，是否复制网址后在浏览器中打开？',
      confirmText: '复制网址',
      success: (result) => {
        if (result.confirm) this.copyUrl()
      },
    })
  },
})
