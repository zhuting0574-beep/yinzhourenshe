import { request, requireLogin } from '../../utils/request'
Page({
  data: { item: null as any, submitting: false, service: null as any, pickupNotice: '<p>点击兑换后请在周一至周五 9:00-17:00</p><p>来“鄞领π”鄞州区大学生就业创业综合服务中心领取</p><p>暂不支持快递运输，敬请谅解！</p>', successNotice: '请在7个自然日内到服务点领取' },
  onLoad(o: any) {
    request<any>(`/mini/products/${o.id}`).then((item) => this.setData({ item }))
    request<any[]>('/mini/content/SERVICE')
      .then((items) => this.setData({ service: items[0] || null }))
      .catch(() => {})
    request<any[]>('/mini/content/PRODUCT_NOTICE')
      .then((items) => this.setData({ pickupNotice: items[0]?.content || this.data.pickupNotice }))
      .catch(() => {})
    request<any[]>('/mini/content/ORDER_SUCCESS')
      .then((items) => this.setData({ successNotice: stripHtml(items[0]?.content) || this.data.successNotice }))
      .catch(() => {})
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  navigate() {
    const s: any = this.data.service
    if (s)
      wx.openLocation({
        latitude: Number(s.latitude),
        longitude: Number(s.longitude),
        name: s.title,
        address: s.address,
      })
  },
  openLogin() {
    const item: any = this.data.item
    const app = getApp()
    if (item && item.id != null) app.globalData.pendingRoute = `/pages/product-detail/product-detail?id=${item.id}`
    wx.navigateTo({ url: '/pages/login/login' })
  },
  redeem() {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    const app = getApp()
    if (!app.globalData.token) return this.openLogin()
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
              content: this.data.successNotice,
              showCancel: false,
              success: () => wx.navigateTo({ url: '/pages/orders/orders' }),
            })
          )
          .catch((error: any) => {
            this.setData({ submitting: false })
            if (error && (error.statusCode === 401 || error.statusCode === 403)) return this.openLogin()
            const message = error?.message ? String(error.message) : '兑换失败，请稍后重试'
            wx.showModal({ title: '兑换失败', content: message, showCancel: false })
          })
      },
    })
  },
})

function stripHtml(value: unknown) {
  return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim()
}
