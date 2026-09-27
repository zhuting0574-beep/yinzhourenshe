import { request } from '../../utils/request'

type ProfileForm = {
  realName: string
  age: string
  graduationSchool: string
  majorDirection: string
  city: string
}

const emptyForm = (): ProfileForm => ({ realName: '', age: '', graduationSchool: '', majorDirection: '', city: '' })

Page({
  data: { form: emptyForm(), saving: false },
  onLoad() {
    ;(this as any).redirecting = false
  },
  onShow() {
    const app = getApp<IAppOption>()
    if (!app.globalData.token) {
      if ((this as any).redirecting) return
      ;(this as any).redirecting = true
      app.globalData.pendingRoute = '/pages/profile-edit/profile-edit'
      return wx.navigateTo({ url: '/pages/login/login' })
    }
    ;(this as any).redirecting = false
    request<any>('/mini/profile')
      .then((profile) => this.setData({ form: toForm(profile) }))
      .catch(() => {})
  },
  input(e: any) {
    const field = e.currentTarget.dataset.field as keyof ProfileForm
    this.setData({ [`form.${field}`]: e.detail.value })
  },
  save() {
    if (this.data.saving) return
    const app = getApp<IAppOption>()
    const form = this.data.form
    const age = String(form.age || '').trim()
    if (age && (!/^\d+$/.test(age) || Number(age) < 1 || Number(age) > 150)) {
      return wx.showToast({ title: '年龄请输入1-150的整数', icon: 'none' })
    }
    this.setData({ saving: true })
    request<any>('/mini/profile/details', {
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
        const profile = result?.user || result
        if (profile) this.setData({ form: toForm(profile) })
        const rewarded = !!result?.rewardGranted
        wx.showToast({ title: rewarded ? '完善资料成功，获得100积分' : '信息保存成功', icon: 'success' })
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

function toForm(profile: any): ProfileForm {
  if (!profile) return emptyForm()
  return {
    realName: profile.realName ?? profile.real_name ?? '',
    age: profile.age === null || profile.age === undefined ? '' : String(profile.age),
    graduationSchool: profile.graduationSchool ?? profile.graduation_school ?? '',
    majorDirection: profile.majorDirection ?? profile.major_direction ?? '',
    city: profile.city ?? '',
  }
}
