"use strict";
Object.defineProperty(exports, "__esModule", { value: true });
const request_1 = require("../../utils/request");
const emptyForm = () => ({ avatarUrl: '', nickname: '', realName: '', gender: '', phone: '', birthDate: '', gradeYear: '', graduationSchool: '', majorDirection: '', city: '' });
const currentYear = new Date().getFullYear();
const gradeOptions = ['请选择级数', ...Array.from({ length: currentYear + 10 - 1950 + 1 }, (_, index) => `${currentYear + 10 - index}级`)];
const genderOptions = ['男', '女'];
const defaultRegion = ['浙江省', '宁波市'];
Page({
    data: {
        form: emptyForm(), saving: false, gradeOptions, genderOptions, gradeIndex: 0, region: defaultRegion, currentDate: new Date().toISOString().slice(0, 10),
        pickerVisible: false, pickerTitle: '', pickerOptions: [], pickerIndex: 0, pickerScrollId: '', pickerType: '', profileRewardPoints: 100,
    },
    onLoad() {
        ;
        this.redirecting = false;
    },
    onShow() {
        const app = getApp();
        if (!app.globalData.token) {
            if (this.redirecting)
                return;
            this.redirecting = true;
            app.globalData.pendingRoute = '/pages/profile-edit/profile-edit';
            return wx.navigateTo({ url: '/pages/login/login' });
        }
        ;
        this.redirecting = false;
        this.formInitialized = true;
        (0, request_1.request)('/mini/reward-config', { silent: true }).then((config) => this.setData({ profileRewardPoints: Number(config === null || config === void 0 ? void 0 : config.profileRewardPoints) || 100 })).catch(() => { });
        (0, request_1.request)('/mini/profile')
            .then((profile) => {
            const form = toForm(profile, app.globalData.user);
            const current = this.data.form;
            const merged = this.formDirty ? mergeForm(form, current) : form;
            this.setData({ form: merged, gradeIndex: gradeIndexFor(merged.gradeYear), region: regionForCity(merged.city) });
        })
            .catch(() => { });
    },
    input(e) {
        const field = e.currentTarget.dataset.field;
        this.formDirty = true;
        this.setData({ [`form.${field}`]: e.detail.value });
    },
    chooseAvatar(e) {
        var _a;
        const avatarUrl = String(((_a = e.detail) === null || _a === void 0 ? void 0 : _a.avatarUrl) || '').trim();
        if (!avatarUrl)
            return wx.showToast({ title: '未获取到微信头像', icon: 'none' });
        this.setData({ 'form.avatarUrl': avatarUrl });
        const nickname = String(this.data.form.nickname || '').trim();
        if (!nickname || isPlaceholderNickname(nickname)) {
            return wx.showToast({ title: '头像已获取，请输入昵称后保存', icon: 'none' });
        }
        this.uploadAvatar(avatarUrl, nickname)
            .catch((error) => wx.showToast({ title: (error === null || error === void 0 ? void 0 : error.message) || '头像保存失败，请稍后重试', icon: 'none' }));
    },
    syncWechatProfile() {
        const app = getApp();
        getWechatProfile()
            .then((profile) => {
            if (!profile)
                throw new Error('未获取到微信资料，请允许使用微信头像和昵称');
            const draft = { ...this.data.form };
            const currentNickname = String(draft.nickname || '').trim();
            const nickname = isPlaceholderNickname(currentNickname) ? String(profile.nickName || '').trim() : currentNickname || String(profile.nickName || '').trim();
            const avatarUrl = String(profile.avatarUrl || draft.avatarUrl || '').trim();
            if (!nickname || isPlaceholderNickname(nickname) || !avatarUrl)
                throw new Error('未获取到有效微信资料，请重新授权');
            this.setData({
                'form.nickname': nickname || currentNickname,
                'form.avatarUrl': avatarUrl || draft.avatarUrl,
            });
            app.globalData.user = { ...(app.globalData.user || {}), nickname, avatar_url: avatarUrl, avatarUrl };
            return (0, request_1.updateWechatProfile)(nickname, avatarUrl).then((user) => {
                const latest = this.data.form;
                const merged = {
                    ...latest,
                    nickname: String(nickname || user.nickname || latest.nickname).trim(),
                    avatarUrl: avatarUrl || user.avatar_url || user.avatarUrl || latest.avatarUrl,
                };
                if (!merged.nickname || isPlaceholderNickname(merged.nickname) || !merged.avatarUrl)
                    throw new Error('微信资料保存失败，请稍后重试');
                this.setData({ form: merged });
                app.globalData.user = { ...(app.globalData.user || {}), ...user };
                wx.showToast({ title: '微信资料已更新', icon: 'success' });
            });
        })
            .catch((error) => wx.showToast({ title: (error === null || error === void 0 ? void 0 : error.message) || '微信资料获取失败', icon: 'none' }));
    },
    uploadAvatar(avatarPath, nickname) {
        const app = getApp();
        return new Promise((resolve, reject) => wx.uploadFile({
            url: `${app.globalData.apiBase.replace(/\/$/, '')}/admin/uploads`,
            filePath: avatarPath,
            name: 'file',
            header: { Authorization: `Bearer ${app.globalData.token}` },
            success: (result) => {
                var _a;
                try {
                    const body = JSON.parse(result.data || '{}');
                    const url = ((_a = body.data) === null || _a === void 0 ? void 0 : _a.url) || body.url;
                    if (!url)
                        throw new Error(body.message || '头像上传失败');
                    (0, request_1.updateWechatProfile)(nickname, url).then((user) => {
                        const savedNickname = String((user === null || user === void 0 ? void 0 : user.nickname) || '').trim();
                        const savedAvatar = String((user === null || user === void 0 ? void 0 : user.avatar_url) || (user === null || user === void 0 ? void 0 : user.avatarUrl) || '').trim();
                        if (!savedNickname || isPlaceholderNickname(savedNickname) || !savedAvatar)
                            throw new Error('微信资料保存失败，请稍后重试');
                        const merged = { ...this.data.form, nickname: savedNickname, avatarUrl: savedAvatar };
                        this.setData({ form: merged });
                        app.globalData.user = { ...(app.globalData.user || {}), ...user };
                        wx.showToast({ title: '微信资料已更新', icon: 'success' });
                        resolve(user);
                    }).catch(reject);
                }
                catch (error) {
                    reject(error);
                }
            },
            fail: reject,
        }));
    },
    openGenderPicker() {
        const pickerIndex = Math.max(0, genderOptions.indexOf(this.data.form.gender));
        this.setData({ pickerVisible: true, pickerTitle: '选择性别', pickerOptions: genderOptions, pickerIndex, pickerType: 'gender', pickerScrollId: `picker-option-${pickerIndex}` });
    },
    openGradePicker() {
        const pickerIndex = gradeIndexFor(this.data.form.gradeYear);
        this.setData({ pickerVisible: true, pickerTitle: '选择级数', pickerOptions: gradeOptions, pickerIndex, pickerType: 'grade', pickerScrollId: `picker-option-${pickerIndex}` });
    },
    selectPickerOption(e) {
        this.setData({ pickerIndex: Number(e.currentTarget.dataset.index) });
    },
    closePicker() {
        this.setData({ pickerVisible: false });
    },
    confirmPicker() {
        const index = Number(this.data.pickerIndex);
        this.formDirty = true;
        if (this.data.pickerType === 'gender') {
            this.setData({ 'form.gender': genderOptions[index] || '', pickerVisible: false });
        }
        else if (this.data.pickerType === 'grade') {
            this.setData({ gradeIndex: index, 'form.gradeYear': index > 0 ? gradeOptions[index].replace('级', '') : '', pickerVisible: false });
        }
    },
    noop() { },
    genderChange(e) { this.setData({ 'form.gender': genderOptions[Number(e.detail.value)] }); },
    regionChange(e) {
        const region = e.detail.value || defaultRegion;
        this.formDirty = true;
        this.setData({ region, 'form.city': region[1] || '' });
    },
    birthDateChange(e) { ; this.formDirty = true; this.setData({ 'form.birthDate': e.detail.value }); },
    getPhoneNumber(e) { var _a; const code = (_a = e.detail) === null || _a === void 0 ? void 0 : _a.code; if (!code)
        return wx.showToast({ title: '未获取到手机号', icon: 'none' }); (0, request_1.bindPhone)(code).then(() => (0, request_1.request)('/mini/profile')).then((profile) => { this.setData({ form: toForm(profile), gradeIndex: gradeIndexFor(String((profile === null || profile === void 0 ? void 0 : profile.grade_year) || '')), region: regionForCity((profile === null || profile === void 0 ? void 0 : profile.city) || '') }); this.formDirty = false; wx.showToast({ title: '手机号已获取', icon: 'success' }); }).catch(() => { }); },
    save() {
        if (this.data.saving)
            return;
        const form = this.data.form;
        const phone = String(form.phone || '').trim();
        if (phone && !/^1[3-9]\d{9}$/.test(phone))
            return wx.showToast({ title: '请输入正确的11位手机号', icon: 'none' });
        const labels = { realName: '真实姓名', gender: '性别', phone: '手机号', birthDate: '出生日期', gradeYear: '级数', graduationSchool: '毕业院校', majorDirection: '专业方向', city: '所在城市' };
        const missing = Object.keys(labels).filter(key => !String(form[key] || '').trim()).map(key => labels[key]);
        if (missing.length)
            return wx.showModal({ title: '信息尚未完善', content: `未填写：${missing.join('、')}。未完善资料将无法报名活动，是否仍要保存？`, confirmText: '继续保存', success: result => result.confirm && this.performSave() });
        this.performSave();
    },
    performSave() {
        const app = getApp();
        const form = this.data.form;
        this.setData({ saving: true });
        (0, request_1.request)('/mini/profile/details', {
            method: 'PUT',
            data: {
                avatarUrl: String(form.avatarUrl || '').trim(),
                nickname: String(form.nickname || '').trim(),
                realName: String(form.realName || '').trim(),
                gender: String(form.gender || '').trim(),
                phone: String(form.phone || '').trim(),
                birthDate: String(form.birthDate || '').trim() || null,
                gradeYear: form.gradeYear ? Number(form.gradeYear) : null,
                graduationSchool: String(form.graduationSchool || '').trim(),
                majorDirection: String(form.majorDirection || '').trim(),
                city: String(form.city || '').trim(),
            },
        })
            .then((result) => {
            const profile = (result === null || result === void 0 ? void 0 : result.user) || result;
            if (profile)
                this.setData({ form: toForm(profile) });
            this.formDirty = false;
            this.formInitialized = true;
            const rewarded = !!(result === null || result === void 0 ? void 0 : result.rewardGranted);
            wx.showToast({ title: rewarded ? `完善资料成功，获得${this.data.profileRewardPoints}积分` : '信息保存成功', icon: 'success' });
            if (profile)
                app.globalData.user = { ...(app.globalData.user || {}), ...profile };
        })
            .catch(() => { })
            .finally(() => this.setData({ saving: false }));
    },
    onBackTap() {
        const pages = getCurrentPages();
        if (pages.length > 1) {
            return wx.navigateBack({
                delta: 1,
                fail: () => wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) }),
            });
        }
        wx.switchTab({ url: '/pages/profile/profile', fail: () => wx.reLaunch({ url: '/pages/profile/profile' }) });
    },
    onHomeTap() {
        wx.switchTab({ url: '/pages/home/home', fail: () => wx.reLaunch({ url: '/pages/home/home' }) });
    },
    back() { this.onBackTap(); },
    home() { this.onHomeTap(); },
});
function toForm(profile, fallback = null) {
    var _a, _b, _c, _d, _e, _f, _g, _h, _j, _k, _l, _m, _o;
    if (!profile)
        return emptyForm();
    return {
        avatarUrl: firstNonBlank(profile.avatarUrl, profile.avatar_url, fallback === null || fallback === void 0 ? void 0 : fallback.avatarUrl, fallback === null || fallback === void 0 ? void 0 : fallback.avatar_url),
        nickname: firstNonBlank(profile.nickname, fallback === null || fallback === void 0 ? void 0 : fallback.nickname),
        realName: (_b = (_a = profile.realName) !== null && _a !== void 0 ? _a : profile.real_name) !== null && _b !== void 0 ? _b : '',
        gender: (_c = profile.gender) !== null && _c !== void 0 ? _c : '',
        phone: (_d = profile.phone) !== null && _d !== void 0 ? _d : '',
        birthDate: (_f = (_e = profile.birthDate) !== null && _e !== void 0 ? _e : profile.birth_date) !== null && _f !== void 0 ? _f : '',
        gradeYear: (_h = (_g = profile.gradeYear) !== null && _g !== void 0 ? _g : profile.grade_year) !== null && _h !== void 0 ? _h : '',
        graduationSchool: (_k = (_j = profile.graduationSchool) !== null && _j !== void 0 ? _j : profile.graduation_school) !== null && _k !== void 0 ? _k : '',
        majorDirection: (_m = (_l = profile.majorDirection) !== null && _l !== void 0 ? _l : profile.major_direction) !== null && _m !== void 0 ? _m : '',
        city: (_o = profile.city) !== null && _o !== void 0 ? _o : '',
    };
}
function gradeIndexFor(value) { if (!value)
    return 0; const index = gradeOptions.indexOf(`${value}级`); return index < 0 ? 0 : index; }
function firstNonBlank(...values) {
    return values.find((value) => value !== null && value !== undefined && String(value).trim() !== '') || '';
}
function mergeForm(base, draft) {
    return Object.keys(base).reduce((result, key) => {
        const field = key;
        result[field] = draft[field] || base[field];
        return result;
    }, { ...base });
}
function isPlaceholderNickname(value) {
    return !value || ['微信用户', '测试用户', '未设置昵称'].includes(value);
}
function getWechatProfile() {
    if (typeof wx.getUserProfile !== 'function')
        return Promise.resolve(null);
    return new Promise((resolve) => wx.getUserProfile({
        desc: '用于展示您的微信昵称和头像',
        success: (result) => resolve(result.userInfo),
        fail: () => resolve(null),
    }));
}
function regionForCity(city) {
    if (!city)
        return defaultRegion;
    if (city === '宁波市' || city === '宁波')
        return defaultRegion;
    return [defaultRegion[0], city];
}
