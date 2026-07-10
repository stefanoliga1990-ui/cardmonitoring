package com.example.cardmonitoring.telegram;

import java.net.http.HttpClient;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(TelegramProperties.class)
class TelegramClientConfiguration {

	@Bean
	@Qualifier("telegramRestClient")
	RestClient telegramRestClient(RestClient.Builder builder, TelegramProperties properties) {
		properties.validate();

		HttpClient httpClient = HttpClient.newBuilder()
				.connectTimeout(properties.getConnectTimeout())
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(properties.getReadTimeout());

		return builder
				.baseUrl(properties.getBaseUrl().toString())
				.requestFactory(requestFactory)
				.build();
	}
}
