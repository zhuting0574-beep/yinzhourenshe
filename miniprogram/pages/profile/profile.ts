import {request} from '../../utils/request'
Page({data:{user:null,points:0},onShow(){request<any>('/mini/profile').then((r:any)=>this.setData(r.data||{})).catch(()=>{})}})
