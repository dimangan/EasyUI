package ru.dimangan.easy_ui.config;

import io.netty.channel.ChannelOption;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@Configuration
@Slf4j
public class WebClientConfiguration {

    @Bean
    public WebClient webClient(){
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(5000));

        return WebClient.builder()
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .exchangeStrategies(ExchangeStrategies.builder().codecs(configurer ->
                        configurer.defaultCodecs().maxInMemorySize(-1)).build())
                .filter((request, next) -> {
                    String url = request.url().toString();
                    long start = System.currentTimeMillis();
                    return next.exchange(request)
                            .doFinally(signalType -> {
                                long duration = System.currentTimeMillis() - start;
                                log.debug("[WEB.TIME] {} {} {} ms", request.method(), url, duration);
                            });
                })
                .filter((request, next) -> {
                    String url = request.url().toString();
                    return next.exchange(request)
                            .flatMap(response -> {
                                HttpStatus status = (HttpStatus) response.statusCode();
                                if (status.is2xxSuccessful() || status.is4xxClientError() || status.is5xxServerError()) {
                                    return response.bodyToMono(byte[].class)
                                            .defaultIfEmpty(new byte[0])
                                            .doOnNext(bytes -> log.debug("[WEB.RESPONSE] {} {} {}", request.method(), url, new String(bytes, StandardCharsets.UTF_8).replace("\r\n", "")))
                                            .map(bytes -> response.mutate()
                                                    .body(Flux.just(new DefaultDataBufferFactory().wrap(bytes)))
                                                    .build());
                                }
                                return Mono.just(response);
                            });
                })
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
