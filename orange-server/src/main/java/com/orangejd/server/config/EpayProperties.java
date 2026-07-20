package com.orangejd.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orange.epay")
public class EpayProperties {
    /** 商户ID */
    private String pid = "";
    /** 商户密钥 */
    private String key = "";
    /** 支付网关地址 */
    private String gateway = "";
    /** 回调通知地址（需公网可达） */
    private String notifyUrl = "";
    /** 支付完成跳转地址 */
    private String returnUrl = "";
    /** 产品订单真实支付开关；服务商查单、退款和结算能力验收前必须保持关闭。 */
    private boolean orderPaymentsEnabled;

    public String getPid() { return pid; }
    public void setPid(String pid) { this.pid = pid; }
    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getReturnUrl() { return returnUrl; }
    public void setReturnUrl(String returnUrl) { this.returnUrl = returnUrl; }
    public boolean isOrderPaymentsEnabled() { return orderPaymentsEnabled; }
    public void setOrderPaymentsEnabled(boolean orderPaymentsEnabled) { this.orderPaymentsEnabled = orderPaymentsEnabled; }

    public boolean isConfigured() {
        return hasText(pid) && hasText(key) && hasText(gateway) && hasText(notifyUrl) && hasText(returnUrl);
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
