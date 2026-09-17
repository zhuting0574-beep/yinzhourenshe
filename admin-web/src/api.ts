import axios from 'axios'

function normalizeDateTimes(value:any):any {
  if (typeof value === 'string') return /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value) ? value.replace('T',' ') : value
  if (Array.isArray(value)) return value.map(normalizeDateTimes)
  if (value && typeof value === 'object') return Object.fromEntries(Object.entries(value).map(([key,item])=>[key,normalizeDateTimes(item)]))
  return value
}

const api=axios.create({baseURL:'/api'})
api.interceptors.request.use(c=>{const token=localStorage.getItem('adminToken');if(token)c.headers.Authorization=`Bearer ${token}`;return c})
api.interceptors.response.use(r=>{if(r.data?.success===false)return Promise.reject(new Error(r.data.message));return normalizeDateTimes(r.data?.data??r.data)},e=>{if(e.response?.status===401){localStorage.removeItem('adminToken');location.href='/login'}return Promise.reject(new Error(e.response?.data?.message||e.message||'请求失败'))})
export default api
