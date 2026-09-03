package za.co.trademesh.modules.notification.infrastructure;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import za.co.trademesh.modules.notification.application.InfobipNotificationProperties;

@Configuration(proxyBeanMethods = false)
@ConditionalOnExpression(
        "'${TRADEMESH_CI_READINESS:false}' != 'true' and '${trademesh.notifications.mobile.provider:local}' == 'infobip'")
class InfobipRestClientConfiguration {

    @Bean
    RestClient infobipRestClient(InfobipNotificationProperties properties) {
        InfobipMobileDeliveryProvider.validate(properties);
        SimpleClientHttpRequestFactory requests = new SimpleClientHttpRequestFactory();
        requests.setConnectTimeout(properties.connectTimeout());
        requests.setReadTimeout(properties.readTimeout());
        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "App " + properties.apiKey())
                .requestFactory(requests)
                .build();
    }
}
