import { request } from '../../utils/request'

function normalizeBanners(banners: any) {
  if (!Array.isArray(banners)) return []
  return banners
    .filter((banner: any) => banner && (banner.image_url || banner.imageUrl) && (banner.activity_id || banner.activityId))
    .map((banner: any) => ({
      ...banner,
      imageUrl: banner.image_url || banner.imageUrl,
      activityId: banner.activity_id || banner.activityId,
    }))
}

function normalizeQuestionnaire(questionnaire: any) {
  if (!questionnaire) return null
  return {
    ...questionnaire,
    popupImageUrl: questionnaire.popup_image_url || questionnaire.popupImageUrl || '',
    popupTitle: questionnaire.popup_title || questionnaire.popupTitle || '',
    headerTitle: questionnaire.header_title || questionnaire.headerTitle || '',
  }
}

Page({
  data: {
    banners: [],
    activities: [],
    points: 0,
    about: [],
    showAbout: false,
    aboutTitle: '关于我们',
    aboutImageUrl: '',
    aboutContent: '',
    questionnaire: null as any,
    loading: true,
    showSurvey: false,
    userName: '',
  },
  onShow() {
    const tab = this.getTabBar && this.getTabBar()
    if (tab) tab.setData({ selected: 0 })
    const user = getApp<IAppOption>().globalData.user as any
    request<any>('/mini/home')
      .then((d) => {
        const questionnaire = normalizeQuestionnaire(d.questionnaire)
        this.setData({
          ...d,
          banners: normalizeBanners(d.banners),
          questionnaire,
          aboutTitle: d.about?.[0]?.title || '关于我们',
          aboutImageUrl: d.about?.[0]?.image_url || d.about?.[0]?.imageUrl || '',
          aboutContent: d.about?.[0]?.content || '',
          loading: false,
          userName: getDisplayName(d.user || user, !!getApp<IAppOption>().globalData.token),
          showSurvey: !!(questionnaire && !questionnaire.completedThisMonth),
        })
        if (d.user) {
          const app = getApp<IAppOption>()
          app.globalData.user = { ...(app.globalData.user || {}), ...d.user }
        }
      })
      .catch(() => this.setData({ loading: false, banners: [], questionnaire: null, aboutImageUrl: '', showSurvey: false, userName: getDisplayName(user, !!getApp<IAppOption>().globalData.token) }))
  },
  openAbout() {
    this.setData({ showAbout: true })
  },
  closeAbout() {
    this.setData({ showAbout: false })
  },
  openBanner(e: WechatMiniprogram.TouchEvent) {
    const activityId = e.currentTarget.dataset.activityId
    if (!activityId) return
    wx.navigateTo({ url: `/pages/activity-detail/activity-detail?id=${encodeURIComponent(activityId)}` })
  },
  closeSurvey() {
    this.setData({ showSurvey: false })
  },
  goSurvey() {
    wx.navigateTo({ url: '/pages/questionnaire/questionnaire' })
  },
  noop() {},
})

function getDisplayName(user: any, loggedIn: boolean) {
  if (!loggedIn) return ''
  const realName = user?.realName || user?.real_name
  const nickname = user?.nickname
  return String(realName || nickname || '微信用户').trim()
}
