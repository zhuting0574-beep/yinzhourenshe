<script setup lang="ts">
import { ref, onMounted } from 'vue'
import axios from 'axios'
const page = ref('dashboard'); const loading = ref(false); const rows = ref<any[]>([])
const menus = [{key:'dashboard',label:'数据概览'},{key:'users',label:'用户管理'},{key:'activities',label:'活动管理'},{key:'questionnaires',label:'问卷管理'},{key:'products',label:'商品与库存'},{key:'orders',label:'兑换订单'},{key:'points',label:'积分明细'},{key:'content',label:'展示内容'}]
async function load(){ loading.value=true; try { const url = page.value==='dashboard'?'/api/admin/dashboard':`/api/admin/${page.value}`; const {data}=await axios.get(url); rows.value=data.data||[] } catch { rows.value=[] } finally { loading.value=false } }
function select(key:string){page.value=key;load()}; onMounted(load)
</script>
<template><el-container class="admin"><el-aside width="220px"><div class="logo">π <b>鄞领π</b><small>运营管理平台</small></div><el-menu :default-active="page" @select="select"><el-menu-item v-for="m in menus" :key="m.key" :index="m.key">{{m.label}}</el-menu-item></el-menu></el-aside><el-container><el-header>工作台 / {{menus.find(m=>m.key===page)?.label}}</el-header><el-main v-loading="loading"><h1>{{menus.find(m=>m.key===page)?.label}}</h1><el-card><el-table :data="rows" empty-text="暂无数据"><el-table-column v-for="(v,k) in (rows[0]||{})" :key="k" :prop="k" :label="k" /></el-table></el-card></el-main></el-container></el-container></template>
