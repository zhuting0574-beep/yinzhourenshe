import { request, requireLogin } from '../../utils/request'
Page({
  data: { items: [], recordNotice: '核销时请出示订单编号或绑定手机号', couponVisible: false, couponType: '', couponCode: '', couponImagePath: '', couponLoading: false },
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
  showCoupon(e: any) { const order = e.currentTarget.dataset.order; if (!order || this.data.couponLoading) return; this.setData({ couponLoading: true }); request<any>(`/mini/orders/${order.id}/coupon`).then((coupon) => { this.setData({ couponVisible: true, couponType: coupon.product_type, couponCode: coupon.code || '', couponImagePath: '' }); if (coupon.product_type === 'METRO_COUPON') return this.loadCouponImage(order.id, 'qr'); if (coupon.product_type === 'BARCODE_COUPON') return this.loadCouponImage(order.id, 'barcode') }).catch(() => {}).finally(() => this.setData({ couponLoading: false })) },
  loadCouponImage(orderId: number, kind: 'qr' | 'barcode') { const app = getApp<IAppOption>(); const label = kind === 'qr' ? '二维码' : '条形码'; return new Promise<void>((resolve, reject) => wx.request({ url: `${app.globalData.apiBase}/mini/orders/${orderId}/coupon-${kind}.png`, responseType: 'arraybuffer', header: { Authorization: `Bearer ${app.globalData.token}` }, success: (result) => { if (result.statusCode !== 200) return reject(new Error(`${label}加载失败`)); const path = `${wx.env.USER_DATA_PATH}/${kind}-coupon-${orderId}.png`; wx.getFileSystemManager().writeFile({ filePath: path, data: result.data as ArrayBuffer, encoding: 'binary', success: () => { this.setData({ couponImagePath: path }); resolve() }, fail: reject }) }, fail: reject })) },
  closeCoupon() { this.setData({ couponVisible: false, couponImagePath: '', couponCode: '' }) },
  noop() {},
  copyCoupon() { wx.setClipboardData({ data: this.data.couponCode }) },
})

function stripHtml(value: unknown) {
  return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim()
}
