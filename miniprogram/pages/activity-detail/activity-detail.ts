import { request, requireLogin, scanActivity } from '../../utils/request'
const app = getApp<IAppOption>()
Page({
  data: {
    item: null as any,
    submitting: false,
    showRegistrationDialog: false,
    registrationStep: 'confirm',
    currentPhone: '',
    registrationPhone: '',
    phoneError: '',
    actionText: '我要报名',
    rewardText: '立即报名',
    actionDisabled: false,
  },
  onLoad(o: any) {
    this.load(o.id)
  },
  load(id: string) {
    return request<any>(`/mini/activities/${id}`).then((item) => {
      this.setActivity(item)
      return item
    })
  },
  getActionState(item: any) {
    const status = item && item.participation_status
    if (status === 'CHECKED_IN')
      return { actionText: '已签到', rewardText: '已完成签到', actionDisabled: true }
    if (status === 'REGISTERED') {
      const mode = (item && item.checkin_mode) || 'QR'
      return {
        actionText: '扫码签到',
        rewardText: mode === 'BOTH' ? '扫码并定位后' : '扫码后',
        actionDisabled: false,
      }
    }
    return { actionText: '我要报名', rewardText: '立即报名', actionDisabled: false }
  },
  setActivity(item: any) {
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
    const item: any = this.data.item
    if (!item || item.participation_status === 'CHECKED_IN' || this.data.submitting) return
    if (item.participation_status === 'REGISTERED') {
      return this.qrCheckIn()
    }
    this.register()
  },
  qrCheckIn() {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true })
    scanActivity()
      .then(() => this.load(item.id))
      .finally(() => this.setData({ submitting: false }))
  },
  noop() {},
  register() {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    if (!app.globalData.token) {
      app.globalData.pendingRoute = `/pages/activity-detail/activity-detail?id=${item.id}`
      wx.navigateTo({ url: '/pages/login/login' })
      return
    }
    this.setData({ submitting: true })
    requireLogin()
      .then((loginUser: any) => request<any>('/mini/profile', { silent: true }).catch(() => loginUser).then((user) => user || loginUser))
      .then((user: any) => {
        app.globalData.user = { ...(app.globalData.user || {}), ...user }
        wx.setStorageSync('user', app.globalData.user)
        if (!isProfileComplete(user)) {
          this.setData({ submitting: false })
          return wx.showModal({
            title: '请先完善信息',
            content: '完成个人信息填写后才能报名活动，是否现在前往完善信息？',
            confirmText: '现在填写',
            cancelText: '暂不填写',
            success: (result) => result.confirm && wx.navigateTo({ url: '/pages/profile-edit/profile-edit' }),
          })
        }
        const current = user?.phone || app.globalData.user?.phone
        this.setData({
          submitting: false,
          showRegistrationDialog: true,
          registrationStep: current ? 'confirm' : 'phone',
          currentPhone: current || '',
          registrationPhone: current || '',
          phoneError: '',
        })
      })
      .catch(() => this.setData({ submitting: false }))
  },
  closeRegistrationDialog() {
    if (this.data.submitting) return
    this.setData({ showRegistrationDialog: false, phoneError: '' })
  },
  chooseOtherPhone() {
    this.setData({ registrationStep: 'phone', registrationPhone: '', phoneError: '' })
  },
  inputRegistrationPhone(e: any) {
    this.setData({ registrationPhone: e.detail.value, phoneError: '' })
  },
  useCurrentPhone() {
    this.submitRegistration(this.data.currentPhone)
  },
  submitPhoneRegistration() {
    const phone = String(this.data.registrationPhone || '').trim()
    if (!/^1[3-9]\d{9}$/.test(phone)) {
      this.setData({ phoneError: '请输入正确的11位手机号码' })
      return
    }
    this.submitRegistration(phone)
  },
  submitRegistration(phone: string) {
    const item: any = this.data.item
    if (!item || this.data.submitting) return
    this.setData({ submitting: true, phoneError: '' })
    request<any>(`/mini/activities/${item.id}/register`, { method: 'POST', data: { phone }, silent: true })
      .then((updated) => {
        const next = { ...(this.data.item || {}), ...(updated || {}) }
        if (!next.participation_status) next.participation_status = 'REGISTERED'
        this.setActivity(next)
        this.setData({ submitting: false, showRegistrationDialog: false })
        wx.showModal({ title: '报名成功', content: '您已成功报名该活动', showCancel: false })
      })
      .catch((error: any) => {
        this.setData({ submitting: false })
        if (error?.code === 'INVALID_PHONE') {
          this.setData({ showRegistrationDialog: true, registrationStep: 'phone', phoneError: error.message || '请输入正确的手机号' })
          return
        }
        const message = error?.message
          ? String(error.message)
          : error && (error.statusCode === 401 || error.statusCode === 403)
            ? '登录状态已失效，请重新登录后再报名'
            : '报名失败，请稍后重试'
        wx.showModal({ title: '报名失败', content: message, showCancel: false })
      })
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

function isProfileComplete(user: any) {
  if (!user) return false
  if (typeof user.profileComplete === 'boolean') return user.profileComplete
  if (typeof user.profile_complete === 'boolean') return user.profile_complete
  const values = [user.realName || user.real_name, user.age, user.graduationSchool || user.graduation_school, user.majorDirection || user.major_direction, user.city]
  return values.every((value) => value !== null && value !== undefined && String(value).trim() !== '')
}
