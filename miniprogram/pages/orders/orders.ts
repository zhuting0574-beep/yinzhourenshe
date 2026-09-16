import {request,requireLogin} from '../../utils/request'
Page({data:{items:[]},onShow(){requireLogin().then(()=>request<any[]>('/mini/orders')).then(items=>this.setData({items}))}})
