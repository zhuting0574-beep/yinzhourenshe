import {defineStore} from 'pinia'
import api from '../api'
import {clearAdminSession,getAdminStorage,setAdminStorage} from '../utils/adminStorage'
export const useSession=defineStore('session',{state:()=>({username:getAdminStorage('adminName')||'',role:getAdminStorage('adminRole')||'ADMIN'}),getters:{orderOnly:(state)=>state.role==='ORDER_OPERATOR'},actions:{async login(username:string,password:string){const data:any=await api.post('/admin/auth/login',{username,password});setAdminStorage('adminToken',data.token);setAdminStorage('adminName',data.username);setAdminStorage('adminRole',data.role||'ADMIN');this.username=data.username;this.role=data.role||'ADMIN'},logout(){clearAdminSession();location.href=`${import.meta.env.BASE_URL}login`}}})
