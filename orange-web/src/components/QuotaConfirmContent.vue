<template>
  <div class="quota-confirm-content">
    <p class="quota-confirm-action">{{ actionLabel }}</p>

    <dl class="quota-confirm-summary" aria-label="额度明细">
      <div class="quota-metric quota-metric--balance">
        <dt>当前可用额度</dt>
        <dd>
          <PixelOrangeSlice :size="18" aria-hidden="true" />
          <strong>{{ formatQuota(balance) }}</strong>
        </dd>
      </div>
      <div class="quota-metric">
        <dt>本次预计消耗</dt>
        <dd>
          <PixelOrangeSlice :size="18" aria-hidden="true" />
          <strong>{{ formatQuota(cost) }}</strong>
        </dd>
      </div>
    </dl>

    <div class="quota-confirm-result" :class="{ 'is-insufficient': insufficient }">
      <span>{{ insufficient ? '当前还差额度' : '完成后预计剩余' }}</span>
      <strong>{{ formatQuota(resultAmount) }}</strong>
    </div>

    <p v-if="notice" class="quota-confirm-notice">{{ notice }}</p>

    <section class="quota-earn-tip" aria-labelledby="quota-earn-title">
      <div class="quota-earn-heading">
        <strong id="quota-earn-title">获取更多额度</strong>
        <span>审核通过后发放</span>
      </div>
      <ul aria-label="获取额度的投稿方式">
        <li>投稿作品</li>
        <li>投稿比赛</li>
        <li>投稿岗位</li>
      </ul>
      <p>奖励额度以平台当前规则为准。</p>
    </section>

    <p class="quota-confirm-footnote">实际余额以提交请求时的服务端校验结果为准。</p>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import PixelOrangeSlice from './PixelOrangeSlice.vue'

const props = defineProps({
  actionLabel: { type: String, required: true },
  balance: { type: Number, required: true },
  cost: { type: Number, required: true },
  notice: { type: String, default: '' },
})

const insufficient = computed(() => props.balance < props.cost)
const resultAmount = computed(() => Math.abs(props.balance - props.cost))
const formatQuota = value => new Intl.NumberFormat('zh-CN', { maximumFractionDigits: 2 }).format(value)
</script>

<style scoped>
.quota-confirm-content {
  color: #3f2a1e;
}

.quota-confirm-action {
  margin: 0 0 14px;
  color: #6f5443;
  font-size: 14px;
  line-height: 1.6;
}

.quota-confirm-summary {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  margin: 0;
}

.quota-metric {
  min-width: 0;
  padding: 12px 14px;
  border: 1px solid #eadfd7;
  border-radius: 10px;
  background: #faf8f6;
}

.quota-metric--balance {
  border-color: #f3bb81;
  background: #fff7ed;
}

.quota-metric dt {
  margin-bottom: 7px;
  color: #806b5e;
  font-size: 12px;
  line-height: 1.4;
}

.quota-metric dd {
  display: flex;
  align-items: center;
  gap: 7px;
  margin: 0;
  color: #452718;
}

.quota-metric strong {
  overflow: hidden;
  font-size: 22px;
  line-height: 1.2;
  text-overflow: ellipsis;
}

.quota-confirm-result {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-top: 12px;
  padding: 10px 12px;
  border-radius: 8px;
  background: #eef8f0;
  color: #276238;
  font-size: 13px;
}

.quota-confirm-result strong {
  font-size: 17px;
}

.quota-confirm-result.is-insufficient {
  background: #fff0ee;
  color: #a7352a;
}

.quota-confirm-notice {
  margin: 12px 0 0;
  padding-left: 10px;
  border-left: 3px solid #e98a2f;
  color: #72513e;
  font-size: 13px;
  line-height: 1.6;
}

.quota-earn-tip {
  margin-top: 12px;
  padding: 11px 12px;
  border: 1px solid #dce9d9;
  border-radius: 9px;
  background: #f6faf4;
}

.quota-earn-heading {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
}

.quota-earn-heading strong {
  color: #325b39;
  font-size: 13px;
}

.quota-earn-heading span {
  color: #6d856f;
  font-size: 11px;
  white-space: nowrap;
}

.quota-earn-tip ul {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 6px;
  margin: 9px 0 7px;
  padding: 0;
  list-style: none;
}

.quota-earn-tip li {
  padding: 6px 4px;
  border: 1px solid #dfead9;
  border-radius: 6px;
  background: #fff;
  color: #45634a;
  font-size: 12px;
  font-weight: 600;
  text-align: center;
}

.quota-earn-tip p {
  margin: 0;
  color: #7a8b7b;
  font-size: 11px;
  line-height: 1.4;
}

.quota-confirm-footnote {
  margin: 10px 0 0;
  color: #958278;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 480px) {
  .quota-confirm-summary {
    grid-template-columns: 1fr;
  }

  .quota-earn-heading {
    align-items: flex-start;
    flex-direction: column;
    gap: 2px;
  }
}
</style>

<style>
.quota-confirm-dialog {
  width: min(440px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
}
</style>
