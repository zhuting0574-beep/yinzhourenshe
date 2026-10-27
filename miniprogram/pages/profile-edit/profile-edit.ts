import { request, updateWechatProfile } from '../../utils/request'

type ProfileForm = {
  avatarUrl: string
  nickname: string
  realName: string
  age: string
  graduationSchool: string
  majorDirection: string
  city: string
}

const emptyForm = (): ProfileForm => ({ avatarUrl: '', nickname: '', realName: '', age: '', graduationSchool: '', majorDirection: '', city: '' })
const ageOptions = ['请选择年龄', ...Array.from({ length: 150 }, (_, index) => `${index + 1}`)]
const defaultRegion = ['浙江省', '宁波市']

Page({
  data: { form: emptyForm(), saving: false, ageOptions, ageIndex: 0, region: defaultRegion },
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
    ;(this as any).formInitialized = true
    request<any>('/mini/profile')
      .then((profile) => {
        const form = toForm(profile, app.globalData.user)
        const current = this.data.form
        const merged = (this as any).formDirty ? mergeForm(form, current) : form
        this.setData({ form: merged, ageIndex: merged.age ? Number(merged.age) : 0, region: regionForCity(merged.city) })
      })
      .catch(() => {})
  },
  input(e: any) {
    const field = e.currentTarget.dataset.field as keyof ProfileForm
    ;(this as any).formDirty = true
    this.setData({ [`form.${field}`]: e.detail.value })
  },
  chooseAvatar(e: any) {
    const avatarUrl = String(e.detail?.avatarUrl || '').trim()
    if (!avatarUrl) return wx.showToast({ title: '未获取到微信头像', icon: 'none' })
    this.setData({ 'form.avatarUrl': avatarUrl })
    getWechatProfile().then((profile) => {
      const nickname = String(profile?.nickName || '').trim()
      if (!nickname || isPlaceholderNickname(nickname)) throw new Error('未获取到微信昵称，请允许微信资料授权')
      this.setData({ 'form.nickname': nickname })
      return this.uploadAvatar(avatarUrl, nickname)
    }).catch((error: any) => wx.showToast({ title: error?.message || '微信资料获取失败', icon: 'none' }))
  },
  syncWechatProfile() {
    const app = getApp<IAppOption>()
    getWechatProfile()
      .then((profile) => {
        if (!profile) throw new Error('未获取到微信资料，请允许使用微信头像和昵称')
        const draft = { ...this.data.form }
        const currentNickname = String(draft.nickname || '').trim()
        const nickname = isPlaceholderNickname(currentNickname) ? String(profile.nickName || '').trim() : currentNickname || String(profile.nickName || '').trim()
        const avatarUrl = String(profile.avatarUrl || draft.avatarUrl || '').trim()
        if (!nickname || isPlaceholderNickname(nickname) || !avatarUrl) throw new Error('未获取到有效微信资料，请重新授权')
        this.setData({
          'form.nickname': nickname || currentNickname,
          'form.avatarUrl': avatarUrl || draft.avatarUrl,
        })
        app.globalData.user = { ...(app.globalData.user || {}), nickname, avatar_url: avatarUrl, avatarUrl }
        return updateWechatProfile(nickname, avatarUrl).then((user) => {
          const latest = this.data.form
          const merged = {
            ...latest,
            nickname: String(nickname || user.nickname || latest.nickname).trim(),
            avatarUrl: avatarUrl || user.avatar_url || user.avatarUrl || latest.avatarUrl,
          }
          if (!merged.nickname || isPlaceholderNickname(merged.nickname) || !merged.avatarUrl) throw new Error('微信资料保存失败，请稍后重试')
          this.setData({ form: merged })
          app.globalData.user = { ...(app.globalData.user || {}), ...user }
          wx.showToast({ title: '微信资料已更新', icon: 'success' })
        })
      })
      .catch((error: any) => wx.showToast({ title: error?.message || '微信资料获取失败', icon: 'none' }))
  },
  uploadAvatar(avatarPath: string, nickname: string) {
    const app = getApp<IAppOption>()
    return new Promise<any>((resolve, reject) => wx.uploadFile({
      url: `${app.globalData.apiBase.replace(/\/$/, '')}/admin/uploads`,
      filePath: avatarPath,
      name: 'file',
      header: { Authorization: `Bearer ${app.globalData.token}` },
      success: (result) => {
        try {
          const body = JSON.parse(result.data || '{}')
          const url = body.data?.url || body.url
          if (!url) throw new Error(body.message || '头像上传失败')
          updateWechatProfile(nickname, url).then((user) => {
            const savedNickname = String(user?.nickname || '').trim()
            const savedAvatar = String(user?.avatar_url || user?.avatarUrl || '').trim()
            if (!savedNickname || isPlaceholderNickname(savedNickname) || !savedAvatar) throw new Error('微信资料保存失败，请稍后重试')
            const merged = { ...this.data.form, nickname: savedNickname, avatarUrl: savedAvatar }
            this.setData({ form: merged })
            app.globalData.user = { ...(app.globalData.user || {}), ...user }
            wx.showToast({ title: '微信资料已更新', icon: 'success' }); resolve(user)
          }).catch(reject)
        } catch (error: any) {
          reject(error)
        }
      },
      fail: reject,
    }))
  },
  ageChange(e: any) {
    const index = Number(e.detail.value)
    ;(this as any).formDirty = true
    this.setData({ ageIndex: index, 'form.age': index > 0 ? String(index) : '' })
  },
  regionChange(e: any) {
    const region = e.detail.value || defaultRegion
    ;(this as any).formDirty = true
    this.setData({ region, 'form.city': region[1] || '' })
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
        avatarUrl: String(form.avatarUrl || '').trim(),
        nickname: String(form.nickname || '').trim(),
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
        ;(this as any).formDirty = false
        ;(this as any).formInitialized = true
        const rewarded = !!result?.rewardGranted
        wx.showToast({ title: rewarded ? '完善资料成功，获得100积分' : '信息保存成功', icon: 'success' })
        if (profile) app.globalData.user = { ...(app.globalData.user || {}), ...profile }
      })
      .catch(() => {})
      .finally(() => this.setData({ saving: false }))
  },
  onBackTap() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      return wx.navigateBack({
        delta: 1,
        fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }),
      })
    }
    wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) })
  },
  onHomeTap() {
    wx.switchTab({ url: '/pages/home/home', fail: () => wx.reLaunch({ url: '/pages/home/home' }) })
  },
  back() { this.onBackTap() },
  home() { this.onHomeTap() },
})

function toForm(profile: any, fallback: any = null): ProfileForm {
  if (!profile) return emptyForm()
  return {
    avatarUrl: firstNonBlank(profile.avatarUrl, profile.avatar_url, fallback?.avatarUrl, fallback?.avatar_url),
    nickname: firstNonBlank(profile.nickname, fallback?.nickname),
    realName: profile.realName ?? profile.real_name ?? '',
    age: profile.age === null || profile.age === undefined ? '' : String(profile.age),
    graduationSchool: profile.graduationSchool ?? profile.graduation_school ?? '',
    majorDirection: profile.majorDirection ?? profile.major_direction ?? '',
    city: profile.city ?? '',
  }
}

function firstNonBlank(...values: any[]) {
  return values.find((value) => value !== null && value !== undefined && String(value).trim() !== '') || ''
}

function mergeForm(base: ProfileForm, draft: ProfileForm): ProfileForm {
  return Object.keys(base).reduce((result, key) => {
    const field = key as keyof ProfileForm
    result[field] = draft[field] || base[field]
    return result
  }, { ...base })
}

function isPlaceholderNickname(value: string) {
  return !value || ['微信用户', '测试用户', '未设置昵称'].includes(value)
}

function getWechatProfile(): Promise<{ nickName: string; avatarUrl: string } | null> {
  if (typeof wx.getUserProfile !== 'function') return Promise.resolve(null)
  return new Promise((resolve) => wx.getUserProfile({
    desc: '用于展示您的微信昵称和头像',
    success: (result) => resolve(result.userInfo),
    fail: () => resolve(null),
  }))
}

function regionForCity(city: string): string[] {
  if (!city) return defaultRegion
  if (city === '宁波市' || city === '宁波') return defaultRegion
  return [defaultRegion[0], city]
}
