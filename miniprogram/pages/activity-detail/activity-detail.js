const { request, requireLogin, scanActivity } = require('../../utils/request')
const app = getApp()
Page({
  data: {
    item: null,
    submitting: false,
    showCheckinPicker: false,
    actionText: '我要报名',
    rewardText: '立即报名',
    actionDisabled: false,
  },
  onLoad(o) {
    this.load(o.id)
  },
  load(id) {
    return request(`/mini/activities/${id}`).then((item) => {
      this.setActivity(item)
      return item
    })
  },
  getActionState(item) {
    const status = item && item.participation_status
    if (status === 'CHECKED_IN')
      return { actionText: '已签到', rewardText: '已完成签到', actionDisabled: true }
    if (status === 'REGISTERED') {
      const mode = (item && item.checkin_mode) || 'QR'
      return {
        actionText: mode === 'LOCATION' ? '定位签到' : mode === 'BOTH' ? '选择签到方式' : '扫码签到',
        rewardText: mode === 'LOCATION' ? '定位签到后' : mode === 'BOTH' ? '扫码或定位签到后' : '扫码签到后',
        actionDisabled: false,
      }
    }
    return { actionText: '我要报名', rewardText: '立即报名', actionDisabled: false }
  },
  setActivity(item) {
    const normalized = item
      ? { ...item, participation_status: item.participation_status ? String(item.participation_status).toUpperCase() : null }
      : item
    this.setData({ item: normalized, ...this.getActionState(normalized) })
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  primaryAction() {
    const item = this.data.item
    if (!item || item.participation_status === 'CHECKED_IN' || this.data.submitting) return
    if (item.participation_status === 'REGISTERED') {
      const mode = item.checkin_mode || 'QR'
      if (mode === 'BOTH') return this.setData({ showCheckinPicker: true })
      if (mode === 'QR') return this.qrCheckIn()
      this.locationCheckIn()
      return
    }
    this.register()
  },
  qrCheckIn() {
    const item = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true })
    scanActivity()
      .then(() => this.load(item.id))
      .finally(() => this.setData({ submitting: false }))
  },
  chooseCheckin(e) {
    const mode = e.currentTarget.dataset.mode
    this.setData({ showCheckinPicker: false })
    if (mode === 'QR') this.qrCheckIn()
    if (mode === 'LOCATION') this.locationCheckIn()
  },
  closeCheckinPicker() {
    this.setData({ showCheckinPicker: false })
  },
  noop() {},
  register() {
    const item = this.data.item
    if (!item || this.data.submitting) return
    if (!app.globalData.token) {
      app.globalData.pendingRoute = `/pages/activity-detail/activity-detail?id=${item.id}`
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ submitting: true })
    const submitRegistration = () => request(`/mini/activities/${item.id}/register`, { method: 'POST' })
    requireLogin()
      .then(submitRegistration)
      .catch((error) => {
        if (error && (error.statusCode === 401 || error.statusCode === 403)) return requireLogin().then(submitRegistration)
        throw error
      })
      .then((updated) => {
        const next = { ...(this.data.item || {}), ...(updated || {}) }
        if (!next.participation_status) next.participation_status = 'REGISTERED'
        this.setActivity(next)
        this.setData({ submitting: false })
        wx.showModal({ title: '报名成功', content: '您已成功报名该活动', showCancel: false })
      })
      .catch((error) => {
        this.setData({ submitting: false })
        const message =
          error && error.message
            ? String(error.message)
            : error && (error.statusCode === 401 || error.statusCode === 403)
              ? '登录状态已失效，请重新登录后再报名'
              : '报名失败，请稍后重试'
        wx.showModal({ title: '报名失败', content: message, showCancel: false })
      })
  },
  locationCheckIn() {
    const item = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true })
    requireLogin()
      .then(
        () =>
          new Promise((resolve, reject) =>
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
      .catch((error) => {
        if (error && error.errMsg && String(error.errMsg).includes('getLocation'))
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
    const i = this.data.item
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
