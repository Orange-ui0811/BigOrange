<template>
  <main class="not-found" id="main-content" tabindex="-1">
    <div class="status">404</div>
    <h1>这里没有登录入口，也没有您要找的页面</h1>
    <p>请核对网址。OrangeVC 的公开入口只有官方网站、公开市场、法律政策与支持中心。</p>
    <div class="actions">
      <router-link to="/market">返回公开市场</router-link>
      <router-link to="/support">联系与举报</router-link>
    </div>
    <small>官方网站：orangevf.com</small>
  </main>
</template>

<style scoped>
.not-found {
  min-height: 100vh;
  display: grid;
  place-content: center;
  justify-items: start;
  gap: 18px;
  padding: 32px;
  background: #fffaf4;
  color: #30251e;
}
.status { color: #f97316; font: 900 clamp(64px, 14vw, 148px)/.8 "Courier New", monospace; }
h1 { max-width: 760px; margin: 0; font-size: clamp(28px, 5vw, 54px); line-height: 1.08; }
p { max-width: 680px; margin: 0; color: #6b5a50; line-height: 1.8; }
.actions { display: flex; flex-wrap: wrap; gap: 12px; }
.actions a { padding: 12px 18px; border: 1px solid #f97316; border-radius: 12px; color: #9a3412; font-weight: 800; text-decoration: none; }
.actions a:first-child { background: #f97316; color: #fff; }
small { color: #8b776b; }
</style>
