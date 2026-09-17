import {request,requireLogin} from '../../utils/request'
Page({data:{items:[]},onShow(){requireLogin().then(()=>request<any[]>('/mini/orders')).then(items=>this.setData({items}))},back(){wx.navigateBack({fail:()=>this.home()})},home(){wx.switchTab({url:'/pages/home/home'})}})
