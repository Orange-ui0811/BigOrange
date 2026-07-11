<template>
  <el-card shadow="never" class="profile-card">
    <h3 class="page-title">编辑资料</h3>
    <el-form label-position="top">
      <el-form-item label="头像"><el-upload :action="API+'/app/common/upload?dir=avatar'" :headers="uploadHeaders" :on-success="onAvatarSuccess" :show-file-list="false" accept="image/*"><el-button size="small">上传头像</el-button></el-upload><el-image v-if="form.avatarUrl" :src="form.avatarUrl" class="avatar-preview" fit="cover"/></el-form-item>
      <el-form-item label="昵称"><el-input v-model="form.nickname"/></el-form-item>
      <el-form-item label="简介"><el-input v-model="form.bio" type="textarea" :rows="3"/></el-form-item>
      <el-form-item label="技能"><el-input v-model="form.skills" placeholder="如：平面设计,UI设计"/></el-form-item>
      <el-form-item label="联系方式"><el-input v-model="form.contactIntro" placeholder="邮箱/微信等"/></el-form-item>
      <el-button type="primary" @click="save" :loading="loading" class="btn-primary">保存</el-button>
    </el-form>
  </el-card>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
const API = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'
const loading=ref(false)
const form=reactive({nickname:'',bio:'',skills:'',contactIntro:'',avatarUrl:''})
onMounted(async()=>{try{const r=await request.get('/app/profile/me');if(r.code===1){form.nickname=r.data.nickname||'';form.bio=r.data.bio||'';form.skills=r.data.skills||'';form.contactIntro=r.data.contactIntro||'';form.avatarUrl=r.data.avatarUrl||''}}catch{}})
onMounted(async()=>{try{const r=await request.get('/app/profile/me');if(r.code===1){form.nickname=r.data.nickname||'';form.bio=r.data.bio||'';form.skills=r.data.skills||'';form.contactIntro=r.data.contactIntro||''}}catch{}})
const uploadHeaders = { 'App-Token': sessionStorage.getItem('appToken') || '' }
const onAvatarSuccess = (res) => { if (res.code === 1) form.avatarUrl = res.data }
import { avatarUrl, nickname } from '../stores/unread'
const save=async()=>{loading.value=true;try{await request.put('/app/profile/update',form);nickname.value=form.nickname;avatarUrl.value=form.avatarUrl;ElMessage.success('保存成功')}catch{}finally{loading.value=false}}
</script>
<style scoped>
.profile-card {
  max-width: 520px;
  margin: 0 auto;
  border: 2px solid #f0ece6;
  border-radius: 2px;
}
.page-title {
  margin: 0 0 16px;
  font-family: "Courier New","SimHei",monospace;
  font-size: 16px;
  color: #3a302a;
  font-weight: 700;
}
.avatar-preview {
  width: 60px;
  height: 60px;
  border-radius: 2px;
  margin-top: 8px;
}
.btn-primary {
  background: #f37021;
  border-color: #f37021;
  border-radius: 0;
  font-family: "Courier New","SimHei",monospace;
  box-shadow: 0 3px 0 rgba(197,83,18,0.15);
}
</style>
