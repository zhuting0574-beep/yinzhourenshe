const { request, requireLogin } = require('../../utils/request')
Page({
  data: { item: null, submitting: false, service: null },
  onLoad(o) {
    request(`/mini/products/${o.id}`).then((item) => this.setData({ item }))
    request('/mini/content/SERVICE')
      .then((items) => this.setData({ service: items[0] || null }))
      .catch(() => {})
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  navigate() {
    const s = this.data.service
    if (s)
      wx.openLocation({
        latitude: Number(s.latitude),
        longitude: Number(s.longitude),
        name: s.title,
        address: s.address,
      })
  },
  redeem() {
    const item = this.data.item
    if (!item || this.data.submitting) return
    wx.showModal({
      title: '确认兑换',
      content: `将使用${item.points_cost}积分兑换${item.name}`,
      success: (r) => {
        if (!r.confirm) return
        this.setData({ submitting: true })
        requireLogin()
          .then(() =>
            request('/mini/orders', {
              method: 'POST',
              data: { productId: item.id, requestId: `${Date.now()}-${item.id}` },
            })
          )
          .then(() =>
            wx.showModal({
              title: '兑换成功',
              content: '请在7个自然日内到服务点领取',
              showCancel: false,
              success: () => wx.navigateTo({ url: '/pages/orders/orders' }),
            })
          )
          .catch(() => this.setData({ submitting: false }))
      },
    })
  },
})
