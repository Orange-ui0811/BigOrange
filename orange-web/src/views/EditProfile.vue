<template>
  <el-card shadow="never" style="max-width:520px;margin:0 auto;border-radius:12px">
    <h3 style="margin:0 0 16px">编辑资料</h3>
    <el-form label-position="top">
      <el-form-item label="昵称"><el-input v-model="form.nickname"/></el-form-item>
      <el-form-item label="简介"><el-input v-model="form.bio" type="textarea" :rows="3"/></el-form-item>
      <el-form-item label="技能"><el-input v-model="form.skills" placeholder="如：平面设计,UI设计"/></el-form-item>
      <el-form-item label="联系方式"><el-input v-model="form.contactIntro" placeholder="邮箱/微信等"/></el-form-item>
      <el-button type="primary" @click="save" :loading="loading" style="background:#e65100;border-color:#e65100">保存</el-button>
    </el-form>
  </el-card>
</template>
<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
const loading=ref(false)
const form=reactive({nickname:'',bio:'',skills:'',contactIntro:''})
onMounted(async()=>{try{const r=await request.get('/app/profile/me');if(r.code===1){form.nickname=r.data.nickname||'';form.bio=r.data.bio||'';form.skills=r.data.skills||'';form.contactIntro=r.data.contactIntro||''}}catch{}})
const save=async()=>{loading.value=true;try{await request.put('/app/profile/update',form);ElMessage.success('保存成功')}catch{}finally{loading.value=false}}
</script>
