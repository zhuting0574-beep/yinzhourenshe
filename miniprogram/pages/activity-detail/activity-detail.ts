import { request, requireLogin, scanActivity } from '../../utils/request'
Page({
  data: { item: null as any, submitting: false },
  onLoad(o: any) {
    this.load(o.id)
  },
  load(id: string) {
    return request<any>(`/mini/activities/${id}`).then((item) => this.setData({ item }))
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  primaryAction() {
    const item: any = this.data.item
    if (!item || item.participation_status === 'CHECKED_IN' || this.data.submitting) return
    if (item.participation_status === 'REGISTERED') {
      if ((item.checkin_mode || 'QR') === 'QR') {
        this.setData({ submitting: true })
        scanActivity()
          .then(() => this.load(item.id))
          .finally(() => this.setData({ submitting: false }))
      } else this.locationCheckIn()
      return
    }
    this.register()
  },
  register() {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true })
    requireLogin()
      .then(() => request<any>(`/mini/activities/${item.id}/register`, { method: 'POST' }))
      .then((updated) => {
        this.setData({ item: updated, submitting: false })
        wx.showToast({ title: '报名成功' })
      })
      .catch(() => this.setData({ submitting: false }))
  },
  locationCheckIn() {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true })
    requireLogin()
      .then(
        () =>
          new Promise<any>((resolve, reject) =>
            wx.getLocation({
              type: 'gcj02',
              isHighAccuracy: true,
              highAccuracyExpireTime: 5000,
              success: resolve,
              fail: reject,
            })
          )
      )
      .then((location) =>
        request(`/mini/activities/${item.id}/check-in/location`, {
          method: 'POST',
          data: {
            latitude: location.latitude,
            longitude: location.longitude,
            accuracy: location.accuracy || 0,
          },
        })
      )
      .then(() => {
        wx.showModal({ title: '签到成功', content: '活动已签到，获得100积分', showCancel: false })
        return this.load(item.id)
      })
      .catch((error: any) => {
        if (error?.errMsg && String(error.errMsg).includes('getLocation'))
          wx.showModal({
            title: '无法获取位置',
            content:
              item.checkin_mode === 'BOTH'
                ? '请开启定位权限后重试，或使用二维码签到。'
                : '请开启定位权限后重试。',
            confirmText: '去设置',
            success: (r) => r.confirm && wx.openSetting(),
          })
      })
      .finally(() => this.setData({ submitting: false }))
  },
  navigate() {
    const i: any = this.data.item
    if (i.latitude != null && i.longitude != null)
      wx.openLocation({
        latitude: Number(i.latitude),
        longitude: Number(i.longitude),
        name: i.place_name,
        address: i.address,
      })
    else wx.showToast({ title: '活动地点尚未配置', icon: 'none' })
  },
})
