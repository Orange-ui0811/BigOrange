<template>
  <div><el-tabs v-model="tab"><el-tab-pane label="买入" name="buy"/><el-tab-pane label="卖出" name="sell"/></el-tabs>
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无订单">
      <el-table-column prop="id" label="ID" width="50"/>
      <el-table-column prop="workTitle" label="作品" show-overflow-tooltip/>
      <el-table-column label="对方" width="100"><template #default="{row}">{{tab==='buy'?row.sellerName:row.buyerName}}</template></el-table-column>
      <el-table-column label="金额" width="100"><template #default="{row}">¥{{row.totalAmount}}</template></el-table-column>
      <el-table-column label="状态" width="130"><template #default="{row}"><el-tag :type="st(row.orderStatus)" size="small">{{row.orderStatus}}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="120"><template #default="{row}">
        <el-button size="small" v-if="tab==='buy'&&row.orderStatus==='WAITING_DEPOSIT'" type="primary" @click="act(row.id,'deposit')">付定金</el-button>
        <el-button size="small" v-if="tab==='buy'&&(row.orderStatus==='WAITING_FINAL_PAYMENT'||row.orderStatus==='DELIVERED')" type="success" @click="act(row.id,'final-pay')">付尾款</el-button>
        <el-button size="small" v-if="tab==='sell'&&row.orderStatus==='DEPOSIT_PAID'" type="primary" @click="deliver(row.id)">交付</el-button>
      </template></el-table-column>
    </el-table>
  </div>
</template>
<script setup>
import { ref, watch, onMounted } from 'vue'
import request from '../api/request'
const tab=ref('buy'),list=ref([]),loading=ref(false)
const st=s=>({COMPLETED:'success',CANCELLED:'info'}[s]||(s?.includes('WAITING')||s==='PENDING')?'warning':'')
const load=async()=>{loading.value=true;try{const r=await request.get('/app/order/'+(tab.value==='buy'?'buy':'sell'),{params:{page:1,size:50}});if(r.code===1)list.value=r.data?.records||[]}finally{loading.value=false}}
const act=async(id,a)=>{await request.put(`/app/order/${id}/${a}`);load()}
const deliver=async(id)=>{const c=prompt('交付内容：');if(!c)return;await request.put(`/app/order/${id}/deliver`,{content:c});load()}
watch(tab,load);onMounted(load)
</script>
