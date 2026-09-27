const { request } = require('../../utils/request')

const emptyForm = () => ({ realName: '', age: '', graduationSchool: '', majorDirection: '', city: '' })

Page({
  data: { form: emptyForm(), saving: false },
  onLoad() {
    this.redirecting = false
  },
  onShow() {
    const app = getApp()
    if (!app.globalData.token) {
      if (this.redirecting) return
      this.redirecting = true
      app.globalData.pendingRoute = '/pages/profile-edit/profile-edit'
      return wx.navigateTo({ url: '/pages/login/login' })
    }
    this.redirecting = false
    request('/mini/profile')
      .then((profile) => this.setData({ form: toForm(profile) }))
      .catch(() => {})
  },
  input(e) {
    const field = e.currentTarget.dataset.field
    this.setData({ [`form.${field}`]: e.detail.value })
  },
  save() {
    if (this.data.saving) return
    const form = this.data.form
    const age = String(form.age || '').trim()
    if (age && (!/^\d+$/.test(age) || Number(age) < 1 || Number(age) > 150)) {
      return wx.showToast({ title: '年龄请输入1-150的整数', icon: 'none' })
    }
    this.setData({ saving: true })
    request('/mini/profile/details', {
      method: 'PUT',
      data: {
        realName: String(form.realName || '').trim(),
        age: age ? Number(age) : null,
        graduationSchool: String(form.graduationSchool || '').trim(),
        majorDirection: String(form.majorDirection || '').trim(),
        city: String(form.city || '').trim(),
      },
    })
      .then((result) => {
        const profile = result && result.user ? result.user : result
        if (profile) this.setData({ form: toForm(profile) })
        const rewarded = !!(result && result.rewardGranted)
        wx.showToast({ title: rewarded ? '完善资料成功，获得100积分' : '信息保存成功', icon: 'success' })
        const app = getApp()
        if (profile) app.globalData.user = { ...(app.globalData.user || {}), ...profile }
      })
      .catch(() => {})
      .finally(() => this.setData({ saving: false }))
  },
  back() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/profile/profile' }) })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
})

function toForm(profile) {
  if (!profile) return emptyForm()
  return {
    realName: profile.realName ?? profile.real_name ?? '',
    age: profile.age === null || profile.age === undefined ? '' : String(profile.age),
    graduationSchool: profile.graduationSchool ?? profile.graduation_school ?? '',
    majorDirection: profile.majorDirection ?? profile.major_direction ?? '',
    city: profile.city ?? '',
  }
}
