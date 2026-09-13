import {request} from '../../utils/request'; Page({data:{banners:[],activities:[],points:0},onLoad(){request<any>('/mini/home').then((d:any)=>this.setData(d.data||{})).catch(()=>{})}})
