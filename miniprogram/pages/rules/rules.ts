import {request} from '../../utils/request'
Page({data:{rules:[],service:[]},onLoad(){Promise.all([request<any[]>('/mini/content/RULES'),request<any[]>('/mini/content/SERVICE')]).then(([rules,service])=>this.setData({rules,service}))},navigate(){const s:any=this.data.service[0];if(s)wx.openLocation({latitude:Number(s.latitude),longitude:Number(s.longitude),name:s.title,address:s.address})}})
