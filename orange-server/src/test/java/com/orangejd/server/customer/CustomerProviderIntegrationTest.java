package com.orangejd.server.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orangejd.server.foundation.external.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

/** Two synthetic capability requests only; never sends platform documents or user history. */
@EnabledIfEnvironmentVariable(named="RUN_CUSTOMER_PROVIDER_PROBE",matches="true")
class CustomerProviderIntegrationTest {
    @Test void recordActualMessageSchemaToolsAndUsage() throws Exception {
        StandardEnvironment env=new StandardEnvironment();
        for(String name:List.of("application-dev.yml","application.yml")) {
            var resource=new ClassPathResource(name);
            if(resource.exists()) for(var source:new YamlPropertySourceLoader().load(name,resource)) env.getPropertySources().addLast(source);
        }
        LettuceConnectionFactory connection=new LettuceConnectionFactory(env.resolveRequiredPlaceholders("${orange.redis.host:redis}"),Integer.parseInt(env.resolveRequiredPlaceholders("${orange.redis.port:6379}")));
        connection.setPassword(env.resolveRequiredPlaceholders("${orange.redis.password:}"));
        connection.setDatabase(Integer.parseInt(env.resolveRequiredPlaceholders("${orange.redis.database:0}")));
        connection.afterPropertiesSet();
        try {
            StringRedisTemplate redis=new StringRedisTemplate(connection);
            AiConfigCrypto crypto=new AiConfigCrypto(env.resolveRequiredPlaceholders("${orange.ai-config.encryption-key:}"));
            var constructor=AiRuntimeConfigService.class.getConstructors()[0]; Object[] args=new Object[constructor.getParameterCount()];
            var parameters=constructor.getParameters();
            for(int i=0;i<parameters.length;i++) args[i]=parameters[i].getType()==StringRedisTemplate.class?redis:
                parameters[i].getType()==AiConfigCrypto.class?crypto:env.resolveRequiredPlaceholders(parameters[i].getAnnotation(Value.class).value());
            var runtime=(AiRuntimeConfigService)constructor.newInstance(args);
            String customerModel=env.resolveRequiredPlaceholders("${orange.customer.model:deepseek-flash}");
            ExternalAiClient ai=new ExternalAiClient(runtime,customerModel); ObjectMapper mapper=new ObjectMapper();
            var settings=runtime.resolveText(null); Map<String,Object> report=new LinkedHashMap<>();
            report.put("model",customerModel); report.put("textModel",settings.model()); report.put("configurationVersion",settings.version()); report.put("scope","Synthetic input only; no platform evidence");
            List<Map<String,Object>> calls=new ArrayList<>();
            for(boolean nativeAction:List.of(false,true)) {
                Map<String,Object> call=new LinkedHashMap<>(); call.put("protocol",nativeAction?"NATIVE_ACTION_V1":"SCHEMA_ACTION_V1");
                try {
                    var response=nativeAction?ai.callCustomerAction(CustomerState.PROMPT,"{\"message\":\"你好\",\"state\":{},\"profile\":\"\",\"history\":[]}",45,500):
                        ai.callCustomer(CustomerState.PROMPT,"{\"message\":\"你好\",\"state\":{},\"profile\":\"\",\"history\":[]}",45,500);
                    call.put("durationMs",response.durationMs()); call.put("usage",response.usage());
                    Map<String,Object> action=mapper.readValue(response.text(),new com.fasterxml.jackson.core.type.TypeReference<>(){});
                    CustomerState.update(Map.of(),action,"你好","");
                    call.put("supported",true); call.put("durationMs",response.durationMs()); call.put("usage",response.usage());
                } catch(Exception ex) { call.put("supported",false); call.put("errorClass",ex.getClass().getSimpleName()); if(ex instanceof com.orangejd.server.foundation.aitool.AiToolProviderException provider) call.put("httpStatus",provider.httpStatus()); }
                calls.add(call);
            }
            report.put("calls",calls); report.put("costUsd","UNKNOWN");
            String reportName="deepseek-flash".equals(customerModel)?"provider-flash.json":"provider.json";
            Path output=Path.of("docs/customer/acceptance/"+reportName);
            if(!Files.isDirectory(Path.of("docs"))) output=Path.of("../docs/customer/acceptance/"+reportName);
            Files.createDirectories(output.getParent()); mapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
            assertThat(calls.getFirst().get("supported")).isEqualTo(true);
        } finally { connection.destroy(); }
    }
}
