const envVersion = wx.getAccountInfoSync?.().miniProgram?.envVersion || 'release'
const apiBase = envVersion === 'release'
  ? 'https://www.yinlinpai.com/api'
  : 'https://www.yinlinpai.com/test-api/api'

function questionnaireIdFromLaunch(options: any) {
  const raw = String(options?.query?.id || options?.query?.scene || '').trim()
  if (!raw) return ''
  try { return decodeURIComponent(raw).replace(/^(?:id|questionnaire|q)=/i, '').trim() } catch { return raw.replace(/^(?:id|questionnaire|q)=/i, '').trim() }
}

App<IAppOption>({
  globalData: { apiBase, token: '', user: null, pendingRoute: '', pendingQuestionnaireId: '' } as any,
  onLaunch(options: any) {
    ;(this.globalData as any).pendingQuestionnaireId = questionnaireIdFromLaunch(options)
    this.globalData.token = wx.getStorageSync('token') || ''
    this.globalData.user = wx.getStorageSync('user') || null
    const stored = this.globalData.user as any
    if (stored && (stored.nickname === '测试用户' || stored.phone === '13800000000' || String(stored.openid || '').startsWith('dev:'))) {
      this.globalData.token = ''
      this.globalData.user = null
      wx.removeStorageSync('token')
      wx.removeStorageSync('user')
    }
  },
  onShow(options: any) {
    const questionnaireId = questionnaireIdFromLaunch(options)
    if (questionnaireId) (this.globalData as any).pendingQuestionnaireId = questionnaireId
  },
})
