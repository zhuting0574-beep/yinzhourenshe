const { request, updateWechatProfile } = require('../../utils/request')

const emptyForm = () => ({ avatarUrl: '', nickname: '', realName: '', gender: '', age: '', graduationSchool: '', majorDirection: '', city: '' })
const ageOptions = ['请选择年龄', ...Array.from({ length: 150 }, (_, index) => `${index + 1}`)]
const genderOptions = ['男', '女']
const defaultRegion = ['浙江省', '宁波市']

Page({
  data: {
    form: emptyForm(), saving: false, ageOptions, genderOptions, ageIndex: 0, region: defaultRegion,
    pickerVisible: false, pickerTitle: '', pickerOptions: [], pickerIndex: 0, pickerScrollId: '', pickerType: '', profileRewardPoints: 100,
  },
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
    this.formInitialized = true
    request('/mini/reward-config', { silent: true }).then((config) => this.setData({ profileRewardPoints: Number(config && config.profileRewardPoints) || 100 })).catch(() => {})
    request('/mini/profile')
      .then((profile) => {
        const form = toForm(profile, app.globalData.user)
        const current = this.data.form
        const merged = this.formDirty ? mergeForm(form, current) : form
        this.setData({ form: merged, ageIndex: merged.age ? Number(merged.age) : 0, region: regionForCity(merged.city) })
      })
      .catch(() => {})
  },
  input(e) {
    const field = e.currentTarget.dataset.field
    this.formDirty = true
    this.setData({ [`form.${field}`]: e.detail.value })
  },
  chooseAvatar(e) {
    const avatarUrl = String(e.detail && e.detail.avatarUrl || '').trim()
    if (!avatarUrl) return wx.showToast({ title: '未获取到微信头像', icon: 'none' })
    this.setData({ 'form.avatarUrl': avatarUrl })
    const nickname = String(this.data.form.nickname || '').trim()
    if (!nickname || isPlaceholderNickname(nickname)) {
      return wx.showToast({ title: '头像已获取，请输入昵称后保存', icon: 'none' })
    }
    this.uploadAvatar(avatarUrl, nickname)
      .catch((error) => wx.showToast({ title: error && error.message ? error.message : '头像保存失败，请稍后重试', icon: 'none' }))
  },
  syncWechatProfile() {
    const app = getApp()
    getWechatProfile()
      .then((profile) => {
        if (!profile) throw new Error('未获取到微信资料，请允许使用微信头像和昵称')
        const draft = Object.assign({}, this.data.form)
        const currentNickname = String(draft.nickname || '').trim()
        const nickname = isPlaceholderNickname(currentNickname) ? String(profile.nickName || '').trim() : currentNickname || String(profile.nickName || '').trim()
        const avatarUrl = String(profile.avatarUrl || draft.avatarUrl || '').trim()
        if (!nickname || isPlaceholderNickname(nickname) || !avatarUrl) throw new Error('未获取到有效微信资料，请重新授权')
        this.setData({
          'form.nickname': nickname || currentNickname,
          'form.avatarUrl': avatarUrl || draft.avatarUrl,
        })
        app.globalData.user = Object.assign({}, app.globalData.user || {}, { nickname, avatar_url: avatarUrl, avatarUrl })
        return updateWechatProfile(nickname, avatarUrl).then((user) => {
          const latest = this.data.form
          const merged = Object.assign({}, latest, {
            nickname: String(nickname || user.nickname || latest.nickname).trim(),
            avatarUrl: avatarUrl || user.avatar_url || user.avatarUrl || latest.avatarUrl,
          })
          if (!merged.nickname || isPlaceholderNickname(merged.nickname) || !merged.avatarUrl) throw new Error('微信资料保存失败，请稍后重试')
          this.setData({ form: merged })
          app.globalData.user = Object.assign({}, app.globalData.user || {}, user)
          wx.showToast({ title: '微信资料已更新', icon: 'success' })
        })
      })
      .catch((error) => wx.showToast({ title: error && error.message ? error.message : '微信资料获取失败', icon: 'none' }))
  },
  uploadAvatar(avatarPath, nickname) {
    const app = getApp()
    return new Promise((resolve, reject) => wx.uploadFile({
      url: `${app.globalData.apiBase.replace(/\/$/, '')}/admin/uploads`,
      filePath: avatarPath,
      name: 'file',
      header: { Authorization: `Bearer ${app.globalData.token}` },
      success: (result) => {
        try {
          const body = JSON.parse(result.data || '{}')
          const url = (body.data && body.data.url) || body.url
          if (!url) throw new Error(body.message || '头像上传失败')
          updateWechatProfile(nickname, url).then((user) => {
            const savedNickname = String(user && user.nickname || '').trim()
            const savedAvatar = String(user && (user.avatar_url || user.avatarUrl) || '').trim()
            if (!savedNickname || isPlaceholderNickname(savedNickname) || !savedAvatar) throw new Error('微信资料保存失败，请稍后重试')
            const merged = Object.assign({}, this.data.form, { nickname: savedNickname, avatarUrl: savedAvatar })
            this.setData({ form: merged })
            app.globalData.user = Object.assign({}, app.globalData.user || {}, user)
            wx.showToast({ title: '微信资料已更新', icon: 'success' }); resolve(user)
          }).catch(reject)
        } catch (error) { reject(error) }
      },
      fail: reject,
    }))
  },
  ageChange(e) {
    const index = Number(e.detail.value)
    this.formDirty = true
    this.setData({ ageIndex: index, 'form.age': index > 0 ? String(index) : '' })
  },
  openGenderPicker() {
    const pickerIndex = Math.max(0, genderOptions.indexOf(this.data.form.gender))
    this.setData({ pickerVisible: true, pickerTitle: '选择性别', pickerOptions: genderOptions, pickerIndex, pickerType: 'gender', pickerScrollId: `picker-option-${pickerIndex}` })
  },
  openAgePicker() {
    const pickerIndex = this.data.form.age ? Number(this.data.form.age) : 0
    this.setData({ pickerVisible: true, pickerTitle: '选择年龄', pickerOptions: ageOptions, pickerIndex, pickerType: 'age', pickerScrollId: `picker-option-${pickerIndex}` })
  },
  selectPickerOption(e) {
    this.setData({ pickerIndex: Number(e.currentTarget.dataset.index) })
  },
  closePicker() {
    this.setData({ pickerVisible: false })
  },
  confirmPicker() {
    const index = Number(this.data.pickerIndex)
    this.formDirty = true
    if (this.data.pickerType === 'gender') {
      this.setData({ 'form.gender': genderOptions[index] || '', pickerVisible: false })
    } else if (this.data.pickerType === 'age') {
      this.setData({ ageIndex: index, 'form.age': index > 0 ? String(index) : '', pickerVisible: false })
    }
  },
  noop() {},
  genderChange(e) { this.setData({ 'form.gender': genderOptions[Number(e.detail.value)] }) },
  regionChange(e) {
    const region = e.detail.value || defaultRegion
    this.formDirty = true
    this.setData({ region, 'form.city': region[1] || '' })
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
        avatarUrl: String(form.avatarUrl || '').trim(),
        nickname: String(form.nickname || '').trim(),
      realName: String(form.realName || '').trim(),
      gender: String(form.gender || '').trim(),
        age: age ? Number(age) : null,
        graduationSchool: String(form.graduationSchool || '').trim(),
        majorDirection: String(form.majorDirection || '').trim(),
        city: String(form.city || '').trim(),
      },
    })
      .then((result) => {
        const profile = result && result.user ? result.user : result
        if (profile) this.setData({ form: toForm(profile) })
        this.formDirty = false
        this.formInitialized = true
        const rewarded = !!(result && result.rewardGranted)
        wx.showToast({ title: rewarded ? `完善资料成功，获得${this.data.profileRewardPoints}积分` : '信息保存成功', icon: 'success' })
        const app = getApp()
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

function toForm(profile, fallback) {
  if (!profile) return emptyForm()
  return {
    avatarUrl: firstNonBlank(profile.avatarUrl, profile.avatar_url, fallback && (fallback.avatarUrl || fallback.avatar_url)),
    nickname: firstNonBlank(profile.nickname, fallback && fallback.nickname),
    realName: profile.realName ?? profile.real_name ?? '',
    gender: profile.gender ?? '',
    age: profile.age === null || profile.age === undefined ? '' : String(profile.age),
    graduationSchool: profile.graduationSchool ?? profile.graduation_school ?? '',
    majorDirection: profile.majorDirection ?? profile.major_direction ?? '',
    city: profile.city ?? '',
  }
}

function firstNonBlank(...values) {
  return values.find((value) => value !== null && value !== undefined && String(value).trim() !== '') || ''
}

function mergeForm(base, draft) {
  return Object.keys(base).reduce((result, field) => {
    result[field] = draft[field] || base[field]
    return result
  }, Object.assign({}, base))
}

function isPlaceholderNickname(value) {
  return !value || ['微信用户', '测试用户', '未设置昵称'].includes(value)
}

function getWechatProfile() {
  if (typeof wx.getUserProfile !== 'function') return Promise.resolve(null)
  return new Promise((resolve) => wx.getUserProfile({
    desc: '用于展示您的微信昵称和头像',
    success: (result) => resolve(result.userInfo),
    fail: () => resolve(null),
  }))
}

function regionForCity(city) {
  if (!city) return defaultRegion
  if (city === '宁波市' || city === '宁波') return defaultRegion
  return [defaultRegion[0], city]
}
