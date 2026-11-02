<template>
  <div class="orders-page">
    <el-tabs v-model="tab" class="orders-tabs">
      <el-tab-pane label="买入" name="buy"/>
      <el-tab-pane label="卖出" name="sell"/>
    </el-tabs>
    <div class="orders-toolbar" v-if="list.length">
      <el-checkbox v-model="selectAll" @change="toggleAll" class="orders-select-all">全选</el-checkbox>
      <el-button size="small" type="danger" plain @click="batchDelete" :disabled="!selected.length">删除选中 ({{selected.length}})</el-button>
    </div>
    <el-table :data="list" size="small" v-loading="loading" empty-text="暂无订单" @row-click="showDetail" class="orders-table">
      <el-table-column width="40">
        <template #default="{row}">
          <el-checkbox :model-value="selected.includes(row.id)" @change="toggle(row.id)" @click.stop />
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="50"/>
      <el-table-column prop="workTitle" label="作品" show-overflow-tooltip/>
      <el-table-column label="对方" width="100">
        <template #default="{row}">{{tab==='buy'?row.sellerName:row.buyerName}}</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{row}">¥{{row.totalAmount}}</template>
      </el-table-column>
      <el-table-column label="状态" width="130">
        <template #default="{row}">
          <el-tag :type="st(row.orderStatus)" size="small">{{row.orderStatus}}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="210">
        <template #default="{row}">
          <el-button size="small" v-if="tab==='buy'&&row.orderStatus==='WAITING_DEPOSIT'" type="primary"
                     :disabled="!orderPaymentEnabled" @click.stop="openPayment(row,'DEPOSIT')">付定金</el-button>
          <el-button size="small" v-if="tab==='buy'&&row.orderStatus==='WAITING_FINAL_PAYMENT'"
                     type="primary" plain @click.stop="confirmDelivery(row.id)">确认交付</el-button>
          <el-button size="small" v-if="tab==='buy'&&row.orderStatus==='DELIVERED'" type="success"
                     :disabled="!orderPaymentEnabled" @click.stop="openPayment(row,'FINAL')">付尾款</el-button>
          <el-button size="small" v-if="tab==='sell'&&row.orderStatus==='DEPOSIT_PAID'" type="primary" @click.stop="deliver(row.id)">交付</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-dialog v-model="dlg" title="订单详情" width="700px" top="3vh" class="orders-dialog">
      <el-descriptions v-if="d" :column="2" border size="small" class="orders-desc">
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
      <div v-if="d?.paymentStages?.length" class="orders-section">
        <h4 class="orders-section-title">支付阶段</h4>
        <el-table :data="d.paymentStages" size="small" class="orders-inner-table">
          <el-table-column prop="stageType" label="类型"/>
          <el-table-column prop="amount" label="金额"/>
          <el-table-column prop="payStatus" label="状态"/>
          <el-table-column prop="payTime" label="时间"/>
        </el-table>
      </div>
      <div v-if="d?.deliveries?.length" class="orders-section">
        <h4 class="orders-section-title">交付记录</h4>
        <el-table :data="d.deliveries" size="small" class="orders-inner-table">
          <el-table-column prop="content" label="内容"/>
          <el-table-column label="附件" min-width="150">
            <template #default="{row}">
              <el-button v-if="row.attachmentUrl" link type="primary" @click="downloadAttachment(d.id,row)">
                {{ row.attachmentName || '下载附件' }}
              </el-button>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="时间"/>
        </el-table>
      </div>
    </el-dialog>
    <el-dialog v-model="deliveryDialog" title="提交交付" width="min(520px, calc(100vw - 32px))"
               :close-on-click-modal="false" :close-on-press-escape="!deliverySubmitting"
               :show-close="!deliverySubmitting" @closed="resetDelivery">
      <el-form label-position="top">
        <el-form-item label="交付说明">
          <el-input v-model="deliveryForm.content" type="textarea" :rows="5" maxlength="2048" show-word-limit
                    placeholder="说明交付内容、使用方式或注意事项"/>
        </el-form-item>
        <el-form-item label="交付附件">
          <el-upload drag :auto-upload="false" :limit="1" :on-change="selectDeliveryFile"
                     :on-remove="removeDeliveryFile" :file-list="deliveryFiles"
                     accept=".pdf,.docx,.xlsx,.pptx,.txt,.zip">
            <div class="upload-copy">选择 PDF、Office、TXT 或 ZIP 文件</div>
            <div class="upload-hint">单个文件不超过 50MB</div>
          </el-upload>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button :disabled="deliverySubmitting" @click="deliveryDialog=false">取消</el-button>
        <el-button type="primary" :loading="deliverySubmitting" @click="submitDelivery">确认交付</el-button>
      </template>
    </el-dialog>
    <el-dialog v-model="paymentDialog" title="订单支付" width="min(420px, calc(100vw - 32px))"
               :close-on-click-modal="!paymentSubmitting" :show-close="!paymentSubmitting">
      <el-alert v-if="!orderPaymentEnabled" title="产品订单在线支付尚未开放" type="warning" :closable="false"/>
      <el-descriptions v-else :column="1" border size="small">
        <el-descriptions-item label="订单">#{{ paymentForm.orderId }}</el-descriptions-item>
        <el-descriptions-item label="阶段">{{ paymentForm.stageType==='DEPOSIT'?'定金':'尾款' }}</el-descriptions-item>
        <el-descriptions-item label="金额">¥{{ paymentForm.amount }}</el-descriptions-item>
      </el-descriptions>
      <el-radio-group v-model="paymentForm.method" :disabled="paymentSubmitting" style="margin-top:16px">
        <el-radio-button value="wxpay">微信支付</el-radio-button>
        <el-radio-button value="alipay">支付宝</el-radio-button>
      </el-radio-group>
      <template #footer>
        <el-button :disabled="paymentSubmitting" @click="paymentDialog=false">取消</el-button>
        <el-button type="primary" :loading="paymentSubmitting" :disabled="!orderPaymentEnabled"
                   @click="submitPayment">前往安全支付</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '../api/request'
const tab=ref('buy'),list=ref([]),loading=ref(false),selected=ref([]),selectAll=ref(false),dlg=ref(false),d=ref(null)
const deliveryDialog=ref(false),deliverySubmitting=ref(false),deliveryOrderId=ref(null),deliveryFiles=ref([])
const deliveryForm=ref({content:'',file:null})
const orderPaymentEnabled=ref(false),paymentDialog=ref(false),paymentSubmitting=ref(false)
const paymentForm=ref({orderId:null,stageType:'DEPOSIT',amount:'0.00',method:'wxpay'})
const st=s=>({COMPLETED:'success',CANCELLED:'info'}[s]||(s?.includes('WAITING')||s==='PENDING')?'warning':'')
const load=async()=>{loading.value=true;try{const r=await request.get('/app/order/'+(tab.value==='buy'?'buy':'sell'),{params:{page:1,size:50}});if(r.code===1)list.value=r.data?.records||[];selected.value=[];selectAll.value=false}finally{loading.value=false}}
const toggle=id=>{const i=selected.value.indexOf(id);i>=0?selected.value.splice(i,1):selected.value.push(id);selectAll.value=selected.value.length===list.value.length}
const toggleAll=v=>{selected.value=v?list.value.map(o=>o.id):[]}
const batchDelete=async()=>{if(!selected.value.length)return;try{await ElMessageBox.confirm('删除选中订单？','批量删除',{type:'warning'})}catch{return};await request.delete('/app/order/batch',{data:selected.value});selected.value=[];load()}
const confirmDelivery=async id=>{await request.put(`/app/order/${id}/confirm`);ElMessage.success('交付已确认，请支付尾款');await load()}
const loadPaymentMode=async()=>{try{const r=await request.get('/app/config/trade-mode');orderPaymentEnabled.value=Boolean(r.data?.orderPaymentEnabled)}catch{orderPaymentEnabled.value=false}}
const openPayment=(row,stageType)=>{
  if(!orderPaymentEnabled.value){ElMessage.warning('产品订单在线支付尚未开放');return}
  paymentForm.value={orderId:row.id,stageType,amount:stageType==='DEPOSIT'?row.deposit:row.finalAmount,method:'wxpay'}
  paymentDialog.value=true
}
const submitPayment=async()=>{
  if(paymentSubmitting.value||!orderPaymentEnabled.value)return
  paymentSubmitting.value=true
  try{
    const r=await request.post('/app/epay/order/create',{
      orderId:paymentForm.value.orderId,
      stageType:paymentForm.value.stageType,
      method:paymentForm.value.method
    })
    if(r.code!==1||!r.data?.payUrl||!r.data?.tradeNo)throw new Error(r.msg||'创建支付失败')
    const target=new URL(r.data.payUrl)
    if(target.protocol!=='https:')throw new Error('支付网关地址不安全')
    window.sessionStorage.setItem('orange-order-payment-trade-no',r.data.tradeNo)
    window.location.assign(target.toString())
  }catch(error){ElMessage.error(error?.message||'创建支付失败')}
  finally{paymentSubmitting.value=false}
}
const checkReturnedPayment=async()=>{
  const tradeNo=window.sessionStorage.getItem('orange-order-payment-trade-no')
  if(!tradeNo)return
  try{
    const r=await request.get(`/app/epay/order/status/${encodeURIComponent(tradeNo)}`)
    if(r.data?.status==='SUCCESS'){
      window.sessionStorage.removeItem('orange-order-payment-trade-no')
      ElMessage.success('支付已确认');await load()
    }else ElMessage.info('支付结果确认中，请稍后刷新订单')
  }catch{}
}
const deliver=id=>{deliveryOrderId.value=id;deliveryDialog.value=true}
const selectDeliveryFile=file=>{deliveryForm.value.file=file.raw;deliveryFiles.value=[file]}
const removeDeliveryFile=()=>{deliveryForm.value.file=null;deliveryFiles.value=[]}
const resetDelivery=()=>{deliveryOrderId.value=null;deliveryFiles.value=[];deliveryForm.value={content:'',file:null}}
const submitDelivery=async()=>{
  const content=deliveryForm.value.content.trim()
  if(!content&&!deliveryForm.value.file){ElMessage.warning('请填写交付说明或选择附件');return}
  const file=deliveryForm.value.file
  if(file&&file.size>50*1024*1024){ElMessage.warning('附件不能超过 50MB');return}
  deliverySubmitting.value=true
  let attachment=null
  try{
    if(file){
      const form=new FormData();form.append('file',file)
      const uploaded=await request.post(`/app/order/${deliveryOrderId.value}/delivery/attachment`,form,{timeout:300000})
      if(uploaded.code!==1)throw new Error(uploaded.msg||'附件上传失败')
      attachment=uploaded.data
    }
    const result=await request.put(`/app/order/${deliveryOrderId.value}/deliver`,{
      content,
      attachmentUrl:attachment?.objectKey||null,
      attachmentName:attachment?.originalName||null,
      attachmentContentType:attachment?.contentType||null,
      attachmentSize:attachment?.size||null
    })
    if(result.code!==1)throw new Error(result.msg||'提交交付失败')
    ElMessage.success('交付已提交');deliveryDialog.value=false;await load()
  }catch(error){
    if(attachment?.objectKey){
      try{await request.delete(`/app/order/${deliveryOrderId.value}/delivery/attachment`,{data:{objectKey:attachment.objectKey}})}catch{}
    }
    ElMessage.error(error?.message||'提交交付失败')
  }finally{deliverySubmitting.value=false}
}
const downloadAttachment=async(orderId,row)=>{
  const downloadWindow=window.open('about:blank','_blank')
  try{
    const result=await request.get(`/app/order/${orderId}/delivery/${row.id}/attachment-url`)
    if(result.code!==1)throw new Error(result.msg||'获取下载地址失败')
    if(downloadWindow)downloadWindow.location=result.data
    else window.location.href=result.data
  }catch(error){if(downloadWindow)downloadWindow.close();ElMessage.error(error?.message||'附件下载失败')}
}
const showDetail=async r=>{try{const res=await request.get('/app/order/'+r.id);if(res.code===1){d.value=res.data;dlg.value=true}}catch{}}
watch(tab,load);onMounted(async()=>{await Promise.all([load(),loadPaymentMode()]);await checkReturnedPayment()})
</script>

<style scoped>
.orders-page {
  background: #ffffff;
  border: 2px solid #f0ece6;
  border-radius: 2px;
  padding: 16px;
}

.orders-tabs :deep(.el-tabs__header) {
  margin-bottom: 16px;
}

.orders-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 2px;
  background-color: #f0ece6;
}

.orders-tabs :deep(.el-tabs__item) {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 13px;
  font-weight: 600;
  color: #9f9890;
  letter-spacing: 0.5px;
  height: 36px;
  line-height: 36px;
}

.orders-tabs :deep(.el-tabs__item.is-active) {
  color: #f37021;
}

.orders-tabs :deep(.el-tabs__active-bar) {
  background-color: #f37021;
  height: 2px;
}

.orders-toolbar {
  margin-bottom: 12px;
  display: flex;
  align-items: center;
}

.orders-select-all {
  margin-right: 12px;
}

.orders-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.orders-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.orders-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.orders-table :deep(.el-table__row:hover > td) {
  background-color: #faf9f7;
  cursor: pointer;
}

.orders-table :deep(.el-button) {
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
}

.orders-table :deep(.el-button--small) {
  height: 28px;
  font-size: 11px;
}

.orders-table :deep(.el-button--primary) {
  background: #f37021;
  border-color: #f37021;
  color: #fff;
  box-shadow: 0 3px 0 rgba(197, 83, 18, 0.15);
}

.orders-table :deep(.el-button--primary:hover) {
  background: #c55312;
  border-color: #c55312;
}

.orders-table :deep(.el-button--success) {
  background: #7fb63c;
  border-color: #7fb63c;
  color: #fff;
  box-shadow: 0 3px 0 rgba(46, 125, 50, 0.15);
}

.orders-table :deep(.el-button--success:hover) {
  background: #2e7d32;
  border-color: #2e7d32;
}

.orders-table :deep(.el-button--danger) {
  background: #e53935;
  border-color: #e53935;
  color: #fff;
  box-shadow: 0 3px 0 rgba(198, 40, 40, 0.15);
}

.orders-table :deep(.el-button--danger:hover) {
  background: #c62828;
  border-color: #c62828;
}

.orders-table :deep(.el-tag) {
  border-radius: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 10px;
  font-weight: 700;
  height: 20px;
  line-height: 20px;
  padding: 0 6px;
  border: none;
}

.orders-table :deep(.el-tag--success) {
  background: #7fb63c;
  color: #fff;
}

.orders-table :deep(.el-tag--warning) {
  background: #f37021;
  color: #fff;
}

.orders-table :deep(.el-tag--info) {
  background: #e5d9c3;
  color: #3a302a;
}

.orders-dialog :deep(.el-dialog__header) {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 16px;
  font-weight: 700;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
  padding-bottom: 12px;
}

.orders-dialog :deep(.el-dialog__body) {
  padding: 16px 20px;
}

.orders-desc :deep(.el-descriptions__label) {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  color: #9f9890;
  text-transform: uppercase;
  letter-spacing: 1px;
}

.orders-desc :deep(.el-descriptions__content) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
}

.orders-section {
  margin-top: 16px;
}

.orders-section-title {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 14px;
  font-weight: 700;
  color: #3a302a;
  margin: 0 0 10px 0;
  letter-spacing: 0.5px;
}

.orders-inner-table {
  border: 2px solid #f0ece6;
  border-radius: 2px;
}

.orders-inner-table :deep(.el-table__header th) {
  background-color: #faf9f7;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 11px;
  font-weight: 700;
  text-transform: uppercase;
  color: #3a302a;
  letter-spacing: 1px;
  border-bottom: 2px solid #f0ece6;
}

.orders-inner-table :deep(.el-table__body td) {
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
  font-size: 13px;
  color: #3a302a;
  border-bottom: 1px solid #f0ece6;
}

.orders-page :deep(.el-upload),
.orders-page :deep(.el-upload-dragger) {
  width: 100%;
}

.upload-copy {
  color: #3a302a;
  font-size: 13px;
  font-weight: 600;
}

.upload-hint {
  margin-top: 4px;
  color: #9f9890;
  font-size: 12px;
}
</style>
