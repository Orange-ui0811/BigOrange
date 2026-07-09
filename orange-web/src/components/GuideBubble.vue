<template>
  <div class="guide-bubble" v-if="guide">
    <div class="guide-speech">
      <!-- 橙子角标 -->
      <div class="guide-mascot">
        <PixelOrangeMascot :expression="guide.expression" style="width:48px;height:48px" />
      </div>
      <!-- 文案 -->
      <div class="guide-lines">
        <p v-for="(line, i) in guide.lines" :key="i">{{ line }}</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import PixelOrangeMascot from './PixelOrangeMascot.vue'

const route = useRoute()

const GUIDES = {
  '/market': {
    expression: 'sparkle',
    lines: [
      '✨ 欢迎来到 OrangeVC 小广场！',
      '这里有大家分享的 AI 作品',
      '也有各种奇思妙想的定制需求',
      '用搜索框和分类药丸快速筛选',
      '看到喜欢的就点进去瞧瞧吧～',
    ]
  },
  '/work/': {
    expression: 'curious',
    lines: [
      '🖼️ 顶部可以看封面和在线演示',
      '有演示链接的话能直接体验哦',
      '中间写了技术栈和功能亮点',
      '右边点一下就能联系作者啦',
      '喜欢的话记得收藏和评论～',
    ]
  },
  '/requirement/': {
    expression: 'curious',
    lines: [
      '💡 有人在这里提出了一个想法',
      '标签会告诉你是做什么类型的',
      '中间看预算范围和具体需求',
      '感兴趣就点右边联系发布者',
      '也可以先收藏起来，回头再看～',
    ]
  },
  '/order/': {
    expression: 'smile',
    lines: [
      '📋 订单的每一笔都记录在案',
      '买卖双方、关联作品一目了然',
      '定金和尾款是分阶段支付的',
      '随时回来看进度和状态变更',
      '有问题就回到聊天沟通吧～',
    ]
  },

  '/ai': {
    expression: 'amazed',
    lines: [
      '🔥 不知道做什么？让数据告诉你！',
      '上面滚动的 Ticker 是 GitHub 热搜',
      '本周涨星和全时段热门都有',
      '去榜单中心看看大家在关注什么',
      '趋势分析 + 方案设计帮你落地～',
    ]
  },
  '/leaderboard': {
    expression: 'amazed',
    lines: [
      '🏆 热度就是最好的风向标！',
      '本网站作品排名和创作者榜',
      '加上 GitHub 仓库三种时段',
      '全时段 / 本周涨星 / 本周新增',
      '点任意卡片就能跳过去看详情～',
    ]
  },
  '/trend': {
    expression: 'amazed',
    lines: [
      '🧭 AI 帮你分析技术趋势～',
      '精准推荐：选方向、目的和基础',
      '自由提问：直接问任何技术问题',
      'Topic 全景图展示热度与机会',
      'AI 会给你一份专属分析报告～',
    ]
  },
  '/plan': {
    expression: 'amazed',
    lines: [
      '📝 一个想法 → 完整项目方案！',
      '写下想法，AI 帮你生成六阶段',
      '想法 → 需求 → 标准 → 风险 → 任务 → 匹配',
      '每阶段都能自己改或重新生成',
      '选详细或精简模式控制深度～',
    ]
  },
  '/submit-job': {
    expression: 'smile',
    lines: [
      '💼 看看真实世界在招什么样的人',
      '先选技术方向，再看具体岗位',
      'JD 详情用 Markdown 清晰展示',
      '右边面板展示需要的技术栈',
      '你也可以投稿岗位分享给大家～',
    ]
  },

  '/publish': {
    expression: 'wink',
    lines: [
      '🚀 把你的宝贝挂上平台吧！',
      '左边「发布作品」：适合有 Demo 的',
      '右边「发布需求」：适合有想法找人的',
      '每边都列好了下一步要填什么',
      '提交后等审核，通过就公开展示～',
    ]
  },
  '/edit/': {
    expression: 'smile',
    lines: [
      '✏️ 修改已发布的内容',
      '字段都已经帮你填好了',
      '改完保存就立刻生效',
      '不用重新走审核流程～',
    ]
  },

  '/chat': {
    expression: 'wink',
    lines: [
      '💬 所有的沟通都在这里！',
      '左边是全部会话，点一下打开',
      '橙色角标提醒你有新消息',
      '聊得差不多可以发起报价',
      '对方接受后就能创建订单啦～',
    ]
  },

  '/me': {
    expression: 'love',
    lines: [
      '🏠 这是你的专属小天地！',
      '顶部可以快速看主页或编辑资料',
      '六个大卡片覆盖全部管理功能',
      '作品、订单、收藏、历史',
      '还有配额充值和账号设置～',
    ]
  },
  '/profile/': {
    expression: 'smile',
    lines: [
      '👤 这是公开的个人主页',
      '头像、昵称、简介都在这里',
      '所有公开作品也会展示出来',
      '点击作品可以查看详情哦～',
    ]
  },
  '/me/edit': {
    expression: 'smile',
    lines: [
      '🖊️ 更新你的对外形象',
      '换上喜欢的头像和昵称',
      '写一段个性简介让大家认识你',
      '保存后个人主页会同步更新～',
    ]
  },
  '/me/works': {
    expression: 'love',
    lines: [
      '📦 你所有的作品都在这里',
      '已发布、审核中、草稿分得很清',
      '可以上架、下架、编辑或删除',
      '点击作品就能跳去详情页',
      '想发新作品？去「发布」页面～',
    ]
  },
  '/me/orders': {
    expression: 'smile',
    lines: [
      '🛒 买和卖的记录分开得很清楚',
      '每笔订单的状态随时可查',
      '金额、关联作品都列在这里',
      '订单有问题回聊天沟通解决～',
    ]
  },
  '/me/favorites': {
    expression: 'love',
    lines: [
      '⭐ 你收藏的宝贝全在这里！',
      '作品和需求都可以收藏',
      '点击可以快速回到详情页',
      '不喜欢了随时取消收藏～',
    ]
  },
  '/me/history': {
    expression: 'smile',
    lines: [
      '👣 浏览过的都会自动记下来',
      '作品和需求都会出现在这里',
      '方便你找回之前看过的东西',
      '点一下就能回到那个页面～',
    ]
  },
  '/me/notifications': {
    expression: 'smile',
    lines: [
      '🔔 叮咚！系统消息都在这～',
      '新消息提醒、审核结果通知',
      '订单状态变更也会推送',
      '左边栏底部的角标是未读数',
      '点进来就不会错过任何事～',
    ]
  },
  '/me/quota': {
    expression: 'love',
    lines: [
      '💎 这是你的小金库！',
      '余额会显示在顶部大卡片上',
      '有兑换码可以直接兑换到账',
      '也支持微信和支付宝在线充值',
      '每笔收支都有记录，花在哪很清楚～',
    ]
  },
  '/me/settings': {
    expression: 'smile',
    lines: [
      '🔑 管好你的账号安全',
      '在这里修改登录密码',
      '建议设置一个强密码',
      '保护好账号很重要哦～',
    ]
  },
}

const guide = computed(() => {
  const p = route.path
  if (GUIDES[p]) return GUIDES[p]
  for (const [key, val] of Object.entries(GUIDES)) {
    // 精确前缀匹配：/work/123 → 匹配 /work/
    if (key.endsWith('/') && p.startsWith(key)) return val
    // 非斜杠结尾前缀：/chat/123 → 匹配 /chat
    if (!key.endsWith('/') && p.startsWith(key + '/')) return val
  }
  return null
})
</script>

<style scoped>
.guide-bubble {
  margin: 6px 10px 0;
}
.guide-speech {
  position: relative;
  background: #fffef9;
  border: 1px solid var(--border-orange);
  border-radius: 14px;
  padding: 10px 12px 10px 12px;
  padding-top: 32px;
  box-shadow: var(--shadow-card);
}
.guide-mascot {
  position: absolute;
  top: -14px;
  left: 8px;
  z-index: 2;
}
.guide-lines p {
  margin: 0;
  font-size: 11px;
  line-height: 1.65;
  color: var(--text-secondary);
}
.guide-lines p:first-child {
  color: var(--brand-strong);
  font-weight: 700;
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: -.01em;
  margin-bottom: 2px;
}
</style>
