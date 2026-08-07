import { ElMessage, ElMessageBox } from 'element-plus'
import { inspectExternalUrl } from '../utils/safeExternalUrl'

export async function openExternalSafely(rawValue, options = {}) {
  const result = inspectExternalUrl(rawValue)
  if (!result.safe) {
    ElMessage.warning('该链接不符合平台安全规则，已停止打开')
    return false
  }
  if (!result.trusted || options.confirmTrusted === true) {
    try {
      await ElMessageBox.confirm(
        `即将离开 OrangeVC，前往 ${result.hostname}。请勿在外部网站泄露本站密码或验证码。`,
        '离站安全提示',
        { confirmButtonText: '继续访问', cancelButtonText: '取消', type: 'warning' },
      )
    } catch {
      return false
    }
  }
  const opened = window.open(result.url.href, '_blank', 'noopener,noreferrer')
  if (opened) opened.opener = null
  return true
}
