import {defineStore} from 'pinia'
import api from '../api'
export const useSession=defineStore('session',{state:()=>({username:localStorage.getItem('adminName')||''}),actions:{async login(username:string,password:string){const data:any=await api.post('/admin/auth/login',{username,password});localStorage.setItem('adminToken',data.token);localStorage.setItem('adminName',data.username);this.username=data.username},logout(){localStorage.clear();location.href='/login'}}})
