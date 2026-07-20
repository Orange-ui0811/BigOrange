<template>
  <div class="legal-page">
    <header class="legal-header">
      <router-link class="brand" :to="homePath">OrangeVC</router-link>
      <nav aria-label="法律文件">
        <router-link v-for="item in legalNav" :key="item.key" :to="`/legal/${item.key}`">
          {{ item.label }}
        </router-link>
        <router-link to="/support">举报与申诉</router-link>
      </nav>
    </header>

    <main id="main-content" class="legal-shell" tabindex="-1">
      <aside class="toc" aria-label="本页目录">
        <strong>本页目录</strong>
        <a v-for="(section, index) in document.sections" :key="section.title" :href="`#section-${index}`">
          {{ section.title }}
        </a>
      </aside>

      <article class="legal-card">
        <div class="eyebrow">LEGAL · ORANGEVC</div>
        <h1>{{ document.title }}</h1>
        <p class="summary">{{ document.summary }}</p>
        <dl class="meta">
          <div><dt>更新日期</dt><dd>{{ document.updatedAt }}</dd></div>
          <div><dt>生效日期</dt><dd>{{ document.effectiveAt }}</dd></div>
          <div><dt>联系邮箱</dt><dd><a :href="`mailto:${document.contact}`">{{ document.contact }}</a></dd></div>
        </dl>

        <div v-if="document.callout" class="callout" role="note">{{ document.callout }}</div>

        <section v-for="(section, index) in document.sections" :id="`section-${index}`" :key="section.title">
          <h2>{{ section.title }}</h2>
          <p v-for="paragraph in section.paragraphs || []" :key="paragraph">{{ paragraph }}</p>
          <div v-if="section.table" class="table-wrap" tabindex="0">
            <table>
              <thead><tr><th v-for="head in section.table.headers" :key="head">{{ head }}</th></tr></thead>
              <tbody>
                <tr v-for="(row, rowIndex) in section.table.rows" :key="rowIndex">
                  <td v-for="(cell, cellIndex) in row" :key="cellIndex">
                    <template v-if="urlFrom(cell)">
                      <span>{{ textWithoutUrl(cell) }} </span>
                      <a :href="urlFrom(cell)" target="_blank" rel="noopener noreferrer">查看服务政策</a>
                    </template>
                    <template v-else>{{ cell }}</template>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>

        <footer class="legal-footer">
          <p>如页面内容与实际启用的服务不一致，请暂停相关功能并联系运营者更新。</p>
          <router-link to="/support">账号注销、数据删除、举报与申诉 →</router-link>
        </footer>
      </article>
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument, legalNav } from '../content/legal'

const route = useRoute()
const document = computed(() => getDocument(route.params.document))
const homePath = computed(() => sessionStorage.getItem('appToken') ? '/market' : '/login')
const urlFrom = (value) => typeof value === 'string' ? value.match(/https?:\/\/\S+/)?.[0] : undefined
const textWithoutUrl = (value) => value.replace(urlFrom(value) || '', '').replace(/[；;]\s*$/, '')
</script>

<style scoped>
.legal-page { min-height: 100vh; background: #fffaf4; color: #30251e; }
.legal-header { position: sticky; top: 0; z-index: 5; display: flex; align-items: center; justify-content: space-between; gap: 24px; padding: 14px clamp(20px, 5vw, 72px); border-bottom: 1px solid #fed7aa; background: rgba(255,250,244,.96); backdrop-filter: blur(12px); }
.brand { color: #c2410c; font: 900 20px/1 var(--font-mono, monospace); text-decoration: none; }
nav { display: flex; flex-wrap: wrap; gap: 8px 18px; }
nav a { color: #6b4c3b; font-size: 14px; text-decoration: none; }
nav a.router-link-active { color: #c2410c; font-weight: 800; }
.legal-shell { width: min(1180px, calc(100% - 32px)); margin: 0 auto; padding: 48px 0 80px; display: grid; grid-template-columns: 220px minmax(0, 1fr); gap: 36px; outline: none; }
.toc { position: sticky; top: 92px; align-self: start; display: grid; gap: 10px; padding: 18px; border: 1px solid #fed7aa; border-radius: 18px; background: #fff; }
.toc strong { margin-bottom: 4px; color: #9a3412; }
.toc a { color: #786356; font-size: 13px; line-height: 1.45; text-decoration: none; }
.toc a:hover { color: #c2410c; }
.legal-card { min-width: 0; padding: clamp(26px, 5vw, 64px); border: 1px solid #fed7aa; border-radius: 28px; background: #fff; box-shadow: 0 18px 48px rgba(124,45,18,.08); }
.eyebrow { color: #ea580c; font: 800 12px/1 var(--font-mono, monospace); letter-spacing: .14em; }
h1 { margin: 12px 0 14px; font-size: clamp(34px, 5vw, 54px); line-height: 1.08; letter-spacing: -.04em; }
.summary { max-width: 760px; color: #6b5a50; font-size: 17px; line-height: 1.8; }
.meta { display: flex; flex-wrap: wrap; gap: 14px 28px; margin: 28px 0 8px; padding: 18px 0; border-block: 1px solid #ffedd5; }
.meta div { display: flex; gap: 8px; }
.meta dt { color: #9a7c6b; }
.meta dd { margin: 0; font-weight: 700; }
a { color: #c2410c; }
.callout { margin: 26px 0; padding: 18px 20px; border-left: 4px solid #ea580c; border-radius: 12px; background: #fff7ed; color: #7c2d12; line-height: 1.75; }
section { scroll-margin-top: 100px; padding-top: 24px; }
h2 { margin: 22px 0 14px; color: #431d0c; font-size: 22px; }
p { margin: 10px 0; line-height: 1.9; }
.table-wrap { margin: 18px 0; overflow-x: auto; border: 1px solid #fed7aa; border-radius: 14px; }
table { width: 100%; min-width: 760px; border-collapse: collapse; font-size: 14px; }
th, td { padding: 14px; border-bottom: 1px solid #ffedd5; text-align: left; vertical-align: top; line-height: 1.65; }
th { background: #fff7ed; color: #7c2d12; white-space: nowrap; }
tbody tr:last-child td { border-bottom: 0; }
.legal-footer { margin-top: 44px; padding-top: 22px; border-top: 1px solid #fed7aa; color: #786356; }
.legal-footer a { font-weight: 800; text-decoration: none; }
@media (max-width: 860px) {
  .legal-header { align-items: flex-start; flex-direction: column; gap: 10px; }
  .legal-shell { grid-template-columns: 1fr; padding-top: 24px; }
  .toc { position: static; grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .toc strong { grid-column: 1 / -1; }
}
@media (max-width: 520px) {
  .legal-card { padding: 24px 18px; border-radius: 20px; }
  .toc { grid-template-columns: 1fr; }
}
</style>
