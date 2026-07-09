package com.example.cardmonitoring.pokemontcg;

import java.net.http.HttpClient;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PokemonTcgProperties.class)
class PokemonTcgClientConfiguration {

	@Bean
	RestClient pokemonTcgRestClient(RestClient.Builder builder, PokemonTcgProperties properties) {
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
