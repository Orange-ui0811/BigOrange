<template>
  <div>
    <div style="margin-bottom:12px" v-if="list.length">
      <el-checkbox v-model="selectAll" @change="toggleAll" style="margin-right:12px">全选</el-checkbox>
      <el-button size="small" type="danger" plain @click="batchDelete" :disabled="!selected.length">批量删除 ({{selected.length}})</el-button>
    </div>
    <el-table :data="list" size="small" stripe v-loading="loading" empty-text="暂无订单" @row-click="showDetail">
      <el-table-column width="40"><template #default="{row}"><el-checkbox :model-value="selected.includes(row.id)" @change="toggle(row.id)" @click.stop /></template></el-table-column>
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="buyerName" label="买方" width="100" />
      <el-table-column prop="sellerName" label="卖方" width="100" />
      <el-table-column prop="workTitle" label="作品" min-width="120" show-overflow-tooltip />
      <el-table-column label="金额" width="90"><template #default="{row}">{{ '¥'+row.totalAmount }}</template></el-table-column>
      <el-table-column label="状态" width="120"><template #default="{row}"><el-tag :type="st(row.orderStatus)" size="small">{{ row.orderStatus }}</el-tag></template></el-table-column>
      <el-table-column prop="createTime" label="时间" width="160" />
    </el-table>
    <el-pagination v-if="total>pageSize" style="margin-top:16px;justify-content:flex-end" background layout="total, prev, pager, next" :total="total" :page-size="pageSize" v-model:current-page="page" @current-change="load" />
    <el-dialog v-model="dlg" title="订单详情" width="700px" top="3vh">
      <el-descriptions v-if="d" :column="2" border size="small">
        <el-descriptions-item label="订单ID">{{ d.id }}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag :type="st(d.orderStatus)" size="small">{{ d.orderStatus }}</el-tag></el-descriptions-item>
        <el-descriptions-item label="买方">{{ d.buyerName }}</el-descriptions-item>
        <el-descriptions-item label="卖方">{{ d.sellerName }}</el-descriptions-item>
        <el-descriptions-item label="作品">{{ d.workTitle||'-' }}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{ d.totalAmount }}</el-descriptions-item>
        <el-descriptions-item label="定金">¥{{ d.deposit||0 }}</el-descriptions-item>
        <el-descriptions-item label="尾款">¥{{ d.finalAmount||0 }}</el-descriptions-item>
        <el-descriptions-item label="来源">{{ d.sourceType }}</el-descriptions-item>
        <el-descriptions-item label="时间">{{ d.createTime }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="d?.paymentStages?.length" style="margin-top:16px"><h4>支付阶段</h4>
        <el-table :data="d.paymentStages" size="small"><el-table-column prop="stageType" label="类型"/><el-table-column prop="amount" label="金额"/><el-table-column prop="payStatus" label="状态"/><el-table-column prop="payTime" label="时间"/></el-table>
      </div>
      <div v-if="d?.deliveries?.length" style="margin-top:16px"><h4>交付记录</h4>
        <el-table :data="d.deliveries" size="small"><el-table-column prop="content" label="内容"/><el-table-column prop="createTime" label="时间"/></el-table>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import request from '../api/request'

const list=ref([]),loading=ref(false),page=ref(1),total=ref(0),pageSize=ref(20),selected=ref([]),selectAll=ref(false),dlg=ref(false),d=ref(null)
const st=s=>({COMPLETED:'success',CANCELLED:'info'}[s]||(s?.includes('WAITING')||s==='PENDING')?'warning':'')

const load=async()=>{loading.value=true;try{const r=await request.get('/admin/orders',{params:{page:page.value,size:pageSize.value}});if(r.code===1){list.value=r.data?.records||[];total.value=r.data?.total||0}}finally{loading.value=false}}
const toggle=id=>{const i=selected.value.indexOf(id);i>=0?selected.value.splice(i,1):selected.value.push(id);selectAll.value=selected.value.length===list.value.length}
const toggleAll=v=>{selected.value=v?list.value.map(o=>o.id):[]}
const batchDelete=async()=>{if(!selected.value.length)return;try{await ElMessageBox.confirm('删除选中订单？','批量删除',{type:'warning'})}catch{return};await request.delete('/admin/orders/batch',{data:selected.value});selected.value=[];load()}
const showDetail=async r=>{try{const res=await request.get('/admin/orders/'+r.id);if(res.code===1){d.value=res.data;dlg.value=true}}catch{}}
onMounted(load)
</script>
