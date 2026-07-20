package com.orangejd.server.config;

import com.orangejd.common.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionConfigurationValidatorTest {

    @Test
    void acceptsSecureConfigurationWithPaymentDisabled() {
        ProductionConfigurationValidator validator = validator(new EpayProperties());
        assertDoesNotThrow(validator::afterPropertiesSet);
    }

    @Test
    void rejectsInsecurePaymentEndpoint() {
        EpayProperties epay = configuredEpay();
        epay.setGateway("http://pay.example.test/submit.php");
        ProductionConfigurationValidator validator = validator(epay);
        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void rejectsOrderPaymentsWithoutCompleteProviderConfiguration() {
        EpayProperties epay = new EpayProperties();
        epay.setOrderPaymentsEnabled(true);
        ProductionConfigurationValidator validator = validator(epay);
        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    @Test
    void rejectsCorsOriginWithCredentialsOrPath() {
        ProductionConfigurationValidator validator = validator(new EpayProperties());
        ReflectionTestUtils.setField(validator, "allowedOrigins", "https://trusted.example@evil.example/path");
        assertThrows(IllegalStateException.class, validator::afterPropertiesSet);
    }

    private ProductionConfigurationValidator validator(EpayProperties epay) {
        JwtProperties jwt = new JwtProperties();
        jwt.setAdminSecretKey("admin-secret-abcdefghijklmnopqrstuvwxyz-123456");
        jwt.setAppSecretKey("app-secret-abcdefghijklmnopqrstuvwxyz-789012");
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(jwt, epay);
        ReflectionTestUtils.setField(validator, "allowedOrigins", "https://app.example.test,https://admin.example.test");
        ReflectionTestUtils.setField(validator, "turnstileSecret", "turnstile-secret");
        ReflectionTestUtils.setField(validator, "publicBaseUrl", "https://app.example.test");
        return validator;
    }

    private EpayProperties configuredEpay() {
        EpayProperties epay = new EpayProperties();
        epay.setPid("merchant-1");
        epay.setKey("payment-secret-1234567890");
        epay.setGateway("https://pay.example.test/submit.php");
        epay.setNotifyUrl("https://api.example.test/api/epay/notify");
        epay.setReturnUrl("https://app.example.test/me/quota");
        return epay;
    }
}
