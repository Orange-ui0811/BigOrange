<template>
  <div class="analysis-started-content" role="status" aria-live="polite">
    <div class="analysis-started-status">
      <span class="analysis-pulse" aria-hidden="true"></span>
      分析任务已经开始
    </div>

    <div class="analysis-time-card">
      <span>此次分析预计耗时</span>
      <strong>{{ estimatedTime }}</strong>
    </div>

    <p>分析期间您可以体验平台的其他功能，不需要停留在当前页面等待。</p>
    <div class="analysis-record-hint">
      完成后将自动保存到左侧“AI 记录”中，方便随时回来查看。
    </div>
    <div class="analysis-retry-hint">
      调用暂时失败时，系统会自动更换可用线路继续分析，无需手动重试。
    </div>
  </div>
</template>

<script setup>
defineProps({
  estimatedTime: { type: String, required: true },
})
</script>

<style scoped>
.analysis-started-content {
  color: #3f2a1e;
}

.analysis-started-status {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
  color: #28623a;
  font-size: 13px;
  font-weight: 700;
}

.analysis-pulse {
  width: 9px;
  height: 9px;
  border: 2px solid #fff;
  border-radius: 50%;
  background: #42a35c;
  box-shadow: 0 0 0 4px #dff3e4;
}

.analysis-time-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 15px;
  border: 1px solid #f1c394;
  border-radius: 10px;
  background: #fff7ed;
}

.analysis-time-card span {
  color: #745443;
  font-size: 13px;
}

.analysis-time-card strong {
  color: #b95718;
  font-size: 20px;
  white-space: nowrap;
}

.analysis-started-content > p {
  margin: 14px 0 10px;
  color: #6f5c50;
  font-size: 13px;
  line-height: 1.7;
}

.analysis-record-hint {
  padding: 10px 12px;
  border-left: 3px solid #4f9b5d;
  background: #f4faf5;
  color: #48644d;
  font-size: 12px;
  line-height: 1.6;
}

.analysis-retry-hint {
  margin-top: 10px;
  color: #716158;
  font-size: 12px;
  line-height: 1.65;
}

</style>

<style>
.analysis-started-dialog {
  width: min(420px, calc(100vw - 32px));
  max-width: calc(100vw - 32px);
}
</style>
