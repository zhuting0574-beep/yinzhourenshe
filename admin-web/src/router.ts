import {createRouter,createWebHistory} from 'vue-router'
import Login from './views/Login.vue'
import Workbench from './views/Workbench.vue'
const modules=['dashboard','users','activities','questionnaires','products','orders','points','content','banners']
const router=createRouter({history:createWebHistory(),routes:[{path:'/login',component:Login},{path:'/',redirect:'/dashboard'},...modules.map(name=>({path:`/${name}`,component:Workbench,props:{module:name}}))]})
router.beforeEach(to=>{if(to.path==='/login')return true;if(!localStorage.getItem('adminToken'))return '/login';if(localStorage.getItem('adminRole')==='ORDER_OPERATOR'&&to.path!=='/orders')return '/orders';return true})
export default router
