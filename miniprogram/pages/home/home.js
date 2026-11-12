"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const request_1 = require("../../utils/request");
function normalizeBanners(banners) {
    if (!Array.isArray(banners))
        return [];
    return banners
        // A banner can be display-only, so an activity relation is optional.
        .filter((banner) => banner && (banner.image_url || banner.imageUrl))
        .map((banner) => { var _a, _b; return ({
        ...banner,
        imageUrl: banner.image_url || banner.imageUrl,
        activityId: (_b = (_a = banner.activity_id) !== null && _a !== void 0 ? _a : banner.activityId) !== null && _b !== void 0 ? _b : '',
    }); });
}
function normalizeQuestionnaire(questionnaire) {
    if (!questionnaire)
        return null;
    return {
        ...questionnaire,
        popupImageUrl: questionnaire.effective_image_url || questionnaire.popup_image_url || questionnaire.popupImageUrl || '',
        popupTitle: questionnaire.popup_title || questionnaire.popupTitle || '',
        headerTitle: questionnaire.header_title || questionnaire.headerTitle || '',
    };
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
        questionnaire: null,
        loading: true,
        showSurvey: false,
        userName: '',
    },
    onShow() {
        const tab = this.getTabBar && this.getTabBar();
        if (tab)
            tab.setData({ selected: 0 });
        const user = getApp().globalData.user;
        (0, request_1.request)('/mini/home', { silent: true })
            .catch((error) => (error === null || error === void 0 ? void 0 : error.code) === 'NOT_FOUND' ? (0, request_1.request)('/mini/home', { silent: true }) : Promise.reject(error))
            .then((d) => {
            var _a, _b, _c, _d, _e, _f, _g, _h;
            const questionnaire = normalizeQuestionnaire(d.questionnaire);
            this.setData({
                ...d,
                banners: normalizeBanners(d.banners),
                questionnaire,
                aboutTitle: ((_b = (_a = d.about) === null || _a === void 0 ? void 0 : _a[0]) === null || _b === void 0 ? void 0 : _b.title) || '关于我们',
                aboutImageUrl: ((_d = (_c = d.about) === null || _c === void 0 ? void 0 : _c[0]) === null || _d === void 0 ? void 0 : _d.image_url) || ((_f = (_e = d.about) === null || _e === void 0 ? void 0 : _e[0]) === null || _f === void 0 ? void 0 : _f.imageUrl) || '',
                aboutContent: ((_h = (_g = d.about) === null || _g === void 0 ? void 0 : _g[0]) === null || _h === void 0 ? void 0 : _h.content) || '',
                loading: false,
                userName: getDisplayName(d.user || user, !!getApp().globalData.token),
                showSurvey: !!(questionnaire && !questionnaire.completedThisMonth),
            });
            if (d.user) {
                const app = getApp();
                app.globalData.user = { ...(app.globalData.user || {}), ...d.user };
            }
        })
            .catch(() => this.setData({ loading: false, banners: [], questionnaire: null, aboutImageUrl: '', showSurvey: false, userName: getDisplayName(user, !!getApp().globalData.token) }));
    },
    openAbout() {
        this.setData({ showAbout: true });
    },
    closeAbout() {
        this.setData({ showAbout: false });
    },
    openBanner(e) {
        const activityId = e.currentTarget.dataset.activityId;
        if (!activityId)
            return;
        wx.navigateTo({ url: `/pages/activity-detail/activity-detail?id=${encodeURIComponent(activityId)}` });
    },
    closeSurvey() {
        this.setData({ showSurvey: false });
    },
    goSurvey() {
        wx.navigateTo({ url: '/pages/questionnaire/questionnaire' });
    },
    noop() { },
    onShareAppMessage() { return { title: '鄞领π', path: '/pages/home/home' }; },
    onShareTimeline() { return { title: '鄞领π', query: '' }; },
});
function getDisplayName(user, loggedIn) {
    if (!loggedIn)
        return '';
    const realName = (user === null || user === void 0 ? void 0 : user.realName) || (user === null || user === void 0 ? void 0 : user.real_name);
    const nickname = user === null || user === void 0 ? void 0 : user.nickname;
    return String(realName || nickname || '微信用户').trim();
}
