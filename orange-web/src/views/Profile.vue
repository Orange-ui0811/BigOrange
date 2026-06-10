<template>
  <div v-loading="loading">
    <el-card v-if="account" shadow="never" style="border-radius:12px;margin-bottom:16px">
      <div style="display:flex;align-items:center;gap:16px">
        <el-avatar :size="64" style="background:#e65100;font-size:24px">{{ (profile?.nickname || account.username)[0] }}</el-avatar>
        <div>
          <h3 style="margin:0">{{ profile?.nickname || account.username }}</h3>
          <p style="color:#909399;font-size:13px;margin:4px 0 0">{{ profile?.bio || '这个人很懒，什么都没写' }}</p>
          <p style="color:#909399;font-size:12px;margin:4px 0 0" v-if="profile?.skills">{{ profile.skills }}</p>
        </div>
        <el-button v-if="isMe" size="small" @click="$router.push('/me/edit')" style="margin-left:auto">编辑资料</el-button>
      </div>
    </el-card>
    <h4 style="margin-bottom:12px">公开作品 ({{ works?.total || 0 }})</h4>
    <el-row :gutter="16">
      <el-col :span="8" v-for="w in works?.records" :key="w.id" style="margin-bottom:16px">
        <el-card shadow="hover" :body-style="{padding:'12px'}" style="border-radius:10px;cursor:pointer;border:1px solid #f0ece6" @click="$router.push('/work/'+w.id)">
          <div style="font-weight:600;font-size:14px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap">{{ w.title }}</div>
          <div style="font-size:11px;color:#909399;margin-top:4px">{{ w.auditStatus }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'

const route = useRoute()
const isMe = route.params.id === 'me'
const account = ref(null)
const profile = ref(null)
const works = ref(null)
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id === 'me' ? null : route.params.id
    const url = id ? `/app/profile/${id}` : '/app/profile/me-info'
    const res = await request.get(url)
    if (res.code === 1 && res.data) {
      if (id) { account.value = res.data.account; profile.value = res.data.profile; works.value = res.data.works }
      else { account.value = { username: res.data.username }; profile.value = { nickname: res.data.nickname } }
    }
  } finally { loading.value = false }
})
</script>
