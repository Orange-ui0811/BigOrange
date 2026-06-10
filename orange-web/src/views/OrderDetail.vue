<template>
  <div v-loading="loading">
    <el-card v-if="order" shadow="never" style="border-radius:12px">
      <h3 style="margin:0 0 12px">订单 #{{order.id}}</h3>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="买方">{{order.buyerName}}</el-descriptions-item>
        <el-descriptions-item label="卖方">{{order.sellerName}}</el-descriptions-item>
        <el-descriptions-item label="作品">{{order.workTitle||'-'}}</el-descriptions-item>
        <el-descriptions-item label="总金额">¥{{order.totalAmount}}</el-descriptions-item>
        <el-descriptions-item label="定金">¥{{order.deposit}}</el-descriptions-item>
        <el-descriptions-item label="尾款">¥{{order.finalAmount}}</el-descriptions-item>
        <el-descriptions-item label="状态"><el-tag>{{order.orderStatus}}</el-tag></el-descriptions-item>
        <el-descriptions-item label="时间">{{order.createTime}}</el-descriptions-item>
      </el-descriptions>
      <div v-if="order.paymentStages?.length" style="margin-top:16px">
        <h4>支付阶段</h4>
        <el-table :data="order.paymentStages" size="small">
          <el-table-column prop="stageType" label="类型"/>
          <el-table-column prop="amount" label="金额"/>
          <el-table-column prop="payStatus" label="状态"/>
          <el-table-column prop="payTime" label="时间"/>
        </el-table>
      </div>
      <div v-if="order.deliveries?.length" style="margin-top:16px">
        <h4>交付记录</h4>
        <el-table :data="order.deliveries" size="small">
          <el-table-column prop="content" label="内容"/>
          <el-table-column prop="createTime" label="时间"/>
        </el-table>
      </div>
    </el-card>
  </div>
</template>
<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
const route=useRoute()
const order=ref(null),loading=ref(false)
onMounted(async()=>{loading.value=true;try{const r=await request.get('/app/order/'+route.params.id);if(r.code===1)order.value=r.data}catch{}finally{loading.value=false}})
</script>
