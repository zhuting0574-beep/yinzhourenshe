const { request, requireLogin } = require('../../utils/request')
Page({
  data: { items: [], recordNotice: '核销时请出示订单编号或绑定手机号' },
  onShow() {
    requireLogin()
      .then(() => request('/mini/orders'))
      .then((items) => this.setData({ items }))
    request('/mini/content/ORDER_RECORD')
      .then((items) => this.setData({ recordNotice: stripHtml(items[0] && items[0].content) || this.data.recordNotice }))
      .catch(() => {})
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})

function stripHtml(value) {
  return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim()
}
