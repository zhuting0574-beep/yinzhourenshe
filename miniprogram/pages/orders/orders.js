"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const request_1 = require("../../utils/request");
Page({
    data: { items: [], recordNotice: '核销时请出示订单编号或绑定手机号', couponVisible: false, couponType: '', couponCode: '', couponImagePath: '', couponLoading: false },
    onShow() {
        (0, request_1.requireLogin)()
            .then(() => (0, request_1.request)('/mini/orders'))
            .then((items) => this.setData({ items }));
        (0, request_1.request)('/mini/content/ORDER_RECORD')
            .then((items) => { var _a; return this.setData({ recordNotice: stripHtml((_a = items[0]) === null || _a === void 0 ? void 0 : _a.content) || this.data.recordNotice }); })
            .catch(() => { });
    },
    back() {
        wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }) });
    },
    home() {
        wx.switchTab({ url: '/pages/home/home' });
    },
    showCoupon(e) { const order = e.currentTarget.dataset.order; if (!order || this.data.couponLoading)
        return; this.setData({ couponLoading: true }); (0, request_1.request)(`/mini/orders/${order.id}/coupon`).then((coupon) => { this.setData({ couponVisible: true, couponType: coupon.product_type, couponCode: coupon.code || '', couponImagePath: '' }); if (coupon.product_type === 'METRO_COUPON')
        return this.loadCouponImage(order.id, 'qr'); if (coupon.product_type === 'BARCODE_COUPON')
        return this.loadCouponImage(order.id, 'barcode'); }).catch(() => { }).finally(() => this.setData({ couponLoading: false })); },
    loadCouponImage(orderId, kind) { const app = getApp(); const label = kind === 'qr' ? '二维码' : '条形码'; return new Promise((resolve, reject) => wx.request({ url: `${app.globalData.apiBase}/mini/orders/${orderId}/coupon-${kind}.png`, responseType: 'arraybuffer', header: { Authorization: `Bearer ${app.globalData.token}` }, success: (result) => { if (result.statusCode !== 200)
            return reject(new Error(`${label}加载失败`)); const path = `${wx.env.USER_DATA_PATH}/${kind}-coupon-${orderId}.png`; wx.getFileSystemManager().writeFile({ filePath: path, data: result.data, encoding: 'binary', success: () => { this.setData({ couponImagePath: path }); resolve(); }, fail: reject }); }, fail: reject })); },
    closeCoupon() { this.setData({ couponVisible: false, couponImagePath: '', couponCode: '' }); },
    noop() { },
    copyCoupon() { wx.setClipboardData({ data: this.data.couponCode }); },
});
function stripHtml(value) {
    return String(value || '').replace(/<[^>]*>/g, ' ').replace(/&nbsp;/gi, ' ').replace(/\s+/g, ' ').trim();
}
