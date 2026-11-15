const envVersion = wx.getAccountInfoSync?.().miniProgram?.envVersion || 'release'
const apiBase = envVersion === 'release'
  ? 'https://www.yinlinpai.com/api'
  : 'https://www.yinlinpai.com/test-api/api'

function questionnaireIdFromLaunch(options) {
  const raw = String(options?.query?.id || options?.query?.scene || '').trim()
  if (!raw) return ''
  try { return decodeURIComponent(raw).replace(/^(?:id|questionnaire|q)=/i, '').trim() } catch { return raw.replace(/^(?:id|questionnaire|q)=/i, '').trim() }
}

App({
  globalData: { apiBase, token: '', user: null, pendingRoute: '', pendingQuestionnaireId: '' },
  onLaunch(options) {
    this.globalData.pendingQuestionnaireId = questionnaireIdFromLaunch(options)
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.user = wx.getStorageSync('user') || null
    const stored = this.globalData.user
    if (stored && (stored.nickname === '测试用户' || stored.phone === '13800000000' || String(stored.openid || '').indexOf('dev:') === 0)) {
      this.globalData.token = ''
      this.globalData.user = null
      wx.removeStorageSync('token')
      wx.removeStorageSync('user')
    }
  },
  onShow(options) {
    const questionnaireId = questionnaireIdFromLaunch(options)
    if (questionnaireId) this.globalData.pendingQuestionnaireId = questionnaireId
  },
})
