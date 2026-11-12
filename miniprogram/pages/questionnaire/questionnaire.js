"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const request_1 = require("../../utils/request");
function normalizeDescription(value) {
    if (value == null)
        return '';
    const description = String(value).trim();
    return description.toLowerCase() === 'null' ? '' : description;
}
Page({
    data: { questionnaire: null, questions: [], answers: {}, submitting: false },
    onLoad(options) {
        const questionnaireId = String((options === null || options === void 0 ? void 0 : options.id) || '').trim();
        (0, request_1.requireLogin)()
            .then(() => (0, request_1.request)(questionnaireId ? `/mini/questionnaires/${encodeURIComponent(questionnaireId)}` : '/mini/questionnaires/active'))
            .then((q) => {
            if (!q)
                return;
            this.setData({
                questionnaire: {
                    ...q,
                    description: normalizeDescription(q.description),
                    headerTitle: q.header_title || q.headerTitle || '',
                    effectiveImageUrl: q.effective_image_url || q.popup_image_url || '',
                },
                questions: (q.questions || []).map((x) => ({
                    ...x,
                    options: JSON.parse(x.options_json || '[]'),
                })),
            });
        });
    },
    back() {
        wx.navigateBack({ fail: () => this.home() });
    },
    home() {
        wx.switchTab({ url: '/pages/home/home' });
    },
    change(e) {
        this.setData({ [`answers.${e.currentTarget.dataset.id}`]: e.detail.value });
    },
    submit() {
        const q = this.data.questionnaire;
        if (!q || this.data.submitting)
            return;
        this.setData({ submitting: true });
        (0, request_1.request)(`/mini/questionnaires/${q.id}/submit`, {
            method: 'POST',
            data: { answers: this.data.answers },
        })
            .then(() => wx.showModal({
            title: '提交成功',
            content: '已获得100积分',
            showCancel: false,
            success: () => wx.navigateBack(),
        }))
            .catch(() => this.setData({ submitting: false }));
    },
    onShareAppMessage() { const q = this.data.questionnaire || {}; return { title: q.name || '调查问卷', path: `/pages/questionnaire/questionnaire${q.id ? `?id=${q.id}` : ''}`, imageUrl: q.effectiveImageUrl }; },
    onShareTimeline() { const q = this.data.questionnaire || {}; return { title: q.name || '调查问卷', query: q.id ? `id=${q.id}` : '', imageUrl: q.effectiveImageUrl }; },
});
