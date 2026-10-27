const { request, requireLogin } = require('../../utils/request')

function normalizeDescription(value) {
  if (value == null) return ''
  const description = String(value).trim()
  return description.toLowerCase() === 'null' ? '' : description
}

Page({
  data: { questionnaire: null, questions: [], answers: {}, submitting: false },
  onLoad() {
    requireLogin()
      .then(() => request('/mini/questionnaires/active'))
      .then(
        (q) => {
          if (!q) return
          this.setData({
            questionnaire: {
              ...q,
              description: normalizeDescription(q.description),
              headerTitle: q.header_title || q.headerTitle || '',
            },
            questions: (q.questions || []).map((x) => ({
              ...x,
              options: JSON.parse(x.options_json || '[]'),
            })),
          })
        }
      )
  },
  back() {
    wx.navigateBack({ fail: () => this.home() })
  },
  home() {
    wx.switchTab({ url: '/pages/home/home' })
  },
  change(e) {
    this.setData({ [`answers.${e.currentTarget.dataset.id}`]: e.detail.value })
  },
  submit() {
    const q = this.data.questionnaire
    if (!q || this.data.submitting) return
    this.setData({ submitting: true })
    request(`/mini/questionnaires/${q.id}/submit`, {
      method: 'POST',
      data: { answers: this.data.answers },
    })
      .then(() =>
        wx.showModal({
          title: '提交成功',
          content: '已获得100积分',
          showCancel: false,
          success: () => wx.navigateBack(),
        })
      )
      .catch(() => this.setData({ submitting: false }))
  },
})
