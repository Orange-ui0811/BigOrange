<template>
  <el-card shadow="never" style="max-width:480px;margin:0 auto;border-radius:12px">
    <h3 style="margin:0 0 16px">修改密码</h3>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
      <el-form-item label="旧密码" prop="old"><el-input v-model="form.old" type="password" show-password/></el-form-item>
      <el-form-item label="新密码" prop="new"><el-input v-model="form.new" type="password" show-password/></el-form-item>
      <el-button type="primary" @click="submit" :loading="loading" style="background:#e65100;border-color:#e65100">确认修改</el-button>
    </el-form>
  </el-card>
</template>
<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import request from '../api/request'
const loading=ref(false)
const form=reactive({old:'',new:''})
const rules={old:[{required:true,message:'请输入旧密码'}],new:[{required:true,message:'请输入新密码'}]}
const formRef=ref(null)
const submit=async()=>{const v=await formRef.value.validate().catch(()=>false);if(!v)return;loading.value=true;try{await request.put('/admin/auth/password',{oldPassword:form.old,newPassword:form.new});ElMessage.success('修改成功');form.old='';form.new=''}finally{loading.value=false}}
</script>
