import { request, requireLogin } from '../../utils/request'
Page({
  data: { items: [], recordNotice: '核销时请出示订单编号或绑定手机号', couponVisible: false, couponType: '', couponCode: '', couponQrPath: '', couponLoading: false },
  onShow() {
    requireLogin()
      .then(() => request<any[]>('/mini/orders'))
      .then((items) => this.setData({ items }))
    request<any[]>('/mini/content/ORDER_RECORD')
      .then((items) => this.setData({ recordNotice: stripHtml(items[0]?.content) || this.data.recordNotice }))
      .catch(() => {})
  },
  back() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }) })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  showCoupon(e: any) { const order = e.currentTarget.dataset.order; if (!order || this.data.couponLoading) return; this.setData({ couponLoading: true }); request<any>(`/mini/orders/${order.id}/coupon`).then((coupon) => { this.setData({ couponVisible: true, couponType: coupon.product_type, couponCode: coupon.code || '', couponQrPath: '' }); if (coupon.product_type === 'METRO_COUPON') return this.loadCouponQr(order.id) }).catch(() => {}).finally(() => this.setData({ couponLoading: false })) },
  loadCouponQr(orderId: number) { const app = getApp<IAppOption>(); return new Promise<void>((resolve, reject) => wx.request({ url: `${app.globalData.apiBase}/mini/orders/${orderId}/coupon-qr.png`, responseType: 'arraybuffer', header: { Authorization: `Bearer ${app.globalData.token}` }, success: (result) => { if (result.statusCode !== 200) return reject(new Error('二维码加载失败')); const path = `${wx.env.USER_DATA_PATH}/metro-coupon-${orderId}.png`; wx.getFileSystemManager().writeFile({ filePath: path, data: result.data as ArrayBuffer, encoding: 'binary', success: () => { this.setData({ couponQrPath: path }); resolve() }, fail: reject }) }, fail: reject })) },
  closeCoupon() { this.setData({ couponVisible: false, couponQrPath: '', couponCode: '' }) },
  noop() {},
  copyCoupon() { wx.setClipboardData({ data: this.data.couponCode }) },
})

function stripHtml(value: unknown) {
  return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim()
}
