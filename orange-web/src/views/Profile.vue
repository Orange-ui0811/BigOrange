<template>
  <div v-loading="loading" class="profile-page">
    <el-card v-if="account" shadow="never" class="profile-card">
      <div class="profile-header">
        <PersonaOrangeBadge v-if="persona.avatarEnabled" compact :equipment="persona.equipment" :appearance="persona.appearance" :label="`${profile?.nickname || account.username}的橙子画像头像`" />
        <el-avatar v-else :size="64" class="profile-avatar" :src="profile?.avatarUrl || undefined">
          {{ (profile?.nickname || account.username || '?')[0] }}
        </el-avatar>
        <div class="profile-info">
          <h3 class="profile-name">{{ profile?.nickname || account.username }}</h3>
          <p class="profile-bio">{{ profile?.bio || '这个人很懒，什么都没写' }}</p>
          <p class="profile-skills" v-if="profile?.skills">{{ profile.skills }}</p>
        </div>
        <el-button v-if="isMe" size="small" @click="$router.push('/me/edit')" class="profile-edit-btn">编辑资料</el-button>
      </div>
    </el-card>
    <PersonaProfileShowcase v-if="persona.enabled" :persona="persona" />
    <h4 class="profile-section-title">公开作品 ({{ works?.total || 0 }})</h4>
    <el-row :gutter="16">
      <el-col :span="8" v-for="w in works?.records" :key="w.id" class="profile-work-col">
        <el-card shadow="never" class="profile-work-card" @click="$router.push('/work/'+w.id)">
          <div class="profile-work-title">{{ w.title }}</div>
          <div class="profile-work-status">{{ w.auditStatus }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import request from '../api/request'
import PersonaOrangeBadge from '../components/PersonaOrangeBadge.vue'
import PersonaProfileShowcase from '../components/PersonaProfileShowcase.vue'
import { getPublicPersonaWorkshop } from '../api/personaWorkshop'
import { useAuthStore } from '../stores/authStore'

const route = useRoute()
const isMe = route.params.id === 'me'
const authStore = useAuthStore()
const account = ref(null)
const profile = ref(null)
const works = ref(null)
const loading = ref(false)
const persona = ref({ enabled: false, avatarEnabled: false, equipment: [], dimensions: [], completion: {} })

onMounted(async () => {
  loading.value = true
  try {
    const id = route.params.id === 'me' ? authStore.accountId : route.params.id
    const url = id ? `/app/profile/${id}` : '/app/profile/me-info'
    const res = await request.get(url)
    if (res.code === 1 && res.data) {
      if (id) { account.value = res.data.account; profile.value = res.data.profile; works.value = res.data.works }
      else {
        account.value = { username: res.data.username }
        profile.value = {
          nickname: res.data.nickname,
          avatarUrl: res.data.avatarUrl,
          bio: res.data.bio,
          skills: res.data.skills,
        }
      }
      if (id) {
        const personaResponse = await getPublicPersonaWorkshop(id)
        if (personaResponse.code === 1 && personaResponse.data) persona.value = personaResponse.data
      }
    }
  } finally { loading.value = false }
})
</script>

<style scoped>
.profile-page {
  max-width: 960px;
}

.profile-card {
  border: 2px solid #f0ece6;
  border-radius: 2px;
  box-shadow: none;
  margin-bottom: 16px;
}

.profile-card :deep(.el-card__body) {
  padding: 20px;
}

.profile-header {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-avatar {
  background: #c55312;
  font-size: 24px;
  font-family: "Courier New", "SimHei", monospace;
  flex-shrink: 0;
  border-radius: 2px;
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name {
  margin: 0;
  font-family: "Courier New", "SimHei", monospace;
  font-size: 16px;
  font-weight: 700;
  color: #3a302a;
  letter-spacing: 1px;
}

.profile-bio {
  color: #9f9890;
  font-size: 13px;
  margin: 4px 0 0;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.profile-skills {
  color: #9f9890;
  font-size: 12px;
  margin: 4px 0 0;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.profile-edit-btn {
  margin-left: auto;
  flex-shrink: 0;
  font-family: "Courier New", "SimHei", monospace;
  border-radius: 0;
  background: #f37021;
  border-color: #f37021;
  color: #fff;
  box-shadow: 0 3px 0 rgba(197, 83, 18, 0.15);
}

.profile-edit-btn:hover {
  background: #c55312;
  border-color: #c55312;
}

.profile-section-title {
  font-family: "Courier New", "SimHei", monospace;
  font-size: 16px;
  font-weight: 700;
  color: #3a302a;
  letter-spacing: 1px;
  margin: 0 0 12px 0;
}

.profile-work-col {
  margin-bottom: 16px;
}

.profile-work-card {
  border: 2px solid #f0ece6;
  border-radius: 2px;
  cursor: pointer;
  box-shadow: none;
  transition: border-color 0.15s;
}

.profile-work-card:hover {
  border-color: #f37021;
}

.profile-work-card :deep(.el-card__body) {
  padding: 12px;
}

.profile-work-title {
  font-weight: 600;
  font-size: 14px;
  color: #3a302a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-family: "Microsoft YaHei", "PingFang SC", sans-serif;
}

.profile-work-status {
  font-size: 11px;
  color: #9f9890;
  margin-top: 4px;
  font-family: "Courier New", "SimHei", monospace;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
</style>
