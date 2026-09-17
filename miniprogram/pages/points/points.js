const {request,requireLogin}=require('../../utils/request')
Page({data:{items:[],points:0},onShow(){requireLogin().then(()=>Promise.all([request('/mini/profile/points'),request('/mini/profile')])).then(([items,user])=>this.setData({items,points:user.points}))},back(){wx.navigateBack({fail:()=>this.home()})},home(){wx.switchTab({url:'/pages/home/home'})}})
