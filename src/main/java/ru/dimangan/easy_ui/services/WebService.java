package ru.dimangan.easy_ui.services;

import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import ru.dimangan.easy_ui.enums.HttpRequestType;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.Function;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebService {
    private final WebClient webClient;
    private final Executor webServiceExecutor = Executors.newFixedThreadPool(2);

    /**
     * Отправка GET запроса
     * @param uri путь для отправки запроса
     * @param headersConsumer заголовки для запроса
     * @param responseClass класс ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1> CompletableFuture<T1> get(String uri, Consumer<HttpHeaders> headersConsumer, Class<T1> responseClass){
        return get(uri, headersConsumer, null, responseClass);
    }

    /**
     * Отправка GET запроса
     * @param uri путь для отправки запроса
     * @param headersConsumer заголовки для запроса
     * @param responseClass класс ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1> CompletableFuture<T1> get(String uri, Consumer<HttpHeaders> headersConsumer, ParameterizedTypeReference<T1> responseClass){
        return get(uri, headersConsumer, null, responseClass);
    }

    /**
     * Отправка GET запроса
     * @param uri путь для отправки запроса
     * @param headersConsumer заголовки для запроса
     * @param responseClass класс ответа на запрос
     * @param responseHeadersConsumer заголовки ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1> CompletableFuture<T1> get(String uri, Consumer<HttpHeaders> headersConsumer, Consumer<HttpHeaders> responseHeadersConsumer, Class<T1> responseClass){
        return CompletableFuture.supplyAsync(() -> {
            log.debug("[WEB.REQUEST] POST {}", uri);

            var get = buildRequest(HttpRequestType.GET, uri, headersConsumer, null);
            return get.exchangeToMono(getResponse(responseClass, responseHeadersConsumer)).block();
        }, webServiceExecutor);
    }

    /**
     * Отправка GET запроса
     * @param uri путь для отправки запроса
     * @param headersConsumer заголовки для запроса
     * @param responseClass класс ответа на запрос
     * @param responseHeadersConsumer заголовки ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1> CompletableFuture<T1> get(String uri, Consumer<HttpHeaders> headersConsumer, Consumer<HttpHeaders> responseHeadersConsumer, ParameterizedTypeReference<T1> responseClass){
        return CompletableFuture.supplyAsync(() -> {
            log.debug("[WEB.REQUEST] POST {}", uri);

            var get = buildRequest(HttpRequestType.GET, uri, headersConsumer, null);
            return get.exchangeToMono(getResponse(responseClass, responseHeadersConsumer)).block();
        }, webServiceExecutor);
    }

    /**
     * Отправка POST запроса
     * @param uri путь для отправки запроса
     * @param bodyRequest тело запроса
     * @param headersConsumer заголовки для запроса
     * @param responseClass класс ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1, T2> CompletableFuture<T2> post(String uri, T1 bodyRequest, Consumer<HttpHeaders> headersConsumer, Class<T2> responseClass) {
        return post(uri, bodyRequest, headersConsumer, null, responseClass);
    }

    /**
     * Отправка POST запроса
     * @param uri путь для отправки запроса
     * @param bodyRequest тело запроса
     * @param headersConsumer заголовки для запроса
     * @param responseHeadersConsumer заголовки ответа на запрос
     * @param responseClass класс ответа на запрос
     * @return CompletableFuture с объектом responseClass
     */
    public <T1, T2> CompletableFuture<T2> post(String uri, T1 bodyRequest, Consumer<HttpHeaders> headersConsumer, Consumer<HttpHeaders> responseHeadersConsumer, Class<T2> responseClass) {
        return CompletableFuture.supplyAsync(() -> {
            String bodyJson = new GsonBuilder().setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ").setStrictness(Strictness.LENIENT).create().toJson(bodyRequest);
            log.debug("[WEB.REQUEST] POST {}: {}", uri, bodyJson);

            var post = buildRequest(HttpRequestType.POST, uri, headersConsumer, bodyRequest);
            return post.exchangeToMono(getResponse(responseClass, responseHeadersConsumer)).block();
        }, webServiceExecutor);
    }

    /**
     * Построение http запроса
     * @param type типа запроса
     * @param uri путь для отправки запроса
     * @param headersConsumer заголовки
     * @param body тело запроса
     * @return WebClient.RequestHeadersSpec<
     */
    private <T> WebClient.RequestHeadersSpec<?> buildRequest(HttpRequestType type, String uri, Consumer<HttpHeaders> headersConsumer, T body){
        var request = switch (type){
            case GET -> webClient.get().uri(uri);
            case POST -> {
                if(body != null){
                    yield  webClient.post().uri(uri).body(Mono.just(body), body.getClass());
                }
                else{
                    yield webClient.post().uri(uri);
                }
            }
        };
        if(headersConsumer != null){
            request.headers(headersConsumer);
        }
        return request;
    }

    /**
     * Функция получения ответа на запрос
     * @param responseClass класс ответа
     * @return функция для получения ответа в виде заданного класса
     */
    private <T> Function<ClientResponse, Mono<@NonNull T>> getResponse(Class<T> responseClass, Consumer<HttpHeaders> responseHeadersConsumer){
        return response -> {
            if (response.statusCode().is2xxSuccessful()) {
                if(responseHeadersConsumer != null){
                    responseHeadersConsumer.accept(response.headers().asHttpHeaders());
                }
                return response.bodyToMono(responseClass);
            } else {
                return response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(bytes -> WebClientResponseException.create(
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                response.headers().asHttpHeaders(),
                                bytes,
                                StandardCharsets.UTF_8
                        )).flatMap(Mono::error);
            }
        };
    }

    /**
     * Функция получения ответа на запрос
     * @param responseClass класс ответа
     * @return функция для получения ответа в виде заданного класса
     */
    private <T> Function<ClientResponse, Mono<@NonNull T>> getResponse(ParameterizedTypeReference<T> responseClass, Consumer<HttpHeaders> responseHeadersConsumer){
        return response -> {
            if (response.statusCode().is2xxSuccessful()) {
                if(responseHeadersConsumer != null){
                    responseHeadersConsumer.accept(response.headers().asHttpHeaders());
                }
                return response.bodyToMono(responseClass);
            } else {
                return response.bodyToMono(byte[].class)
                        .defaultIfEmpty(new byte[0])
                        .map(bytes -> WebClientResponseException.create(
                                response.statusCode().value(),
                                response.statusCode().toString(),
                                response.headers().asHttpHeaders(),
                                bytes,
                                StandardCharsets.UTF_8
                        )).flatMap(Mono::error);
            }
        };
    }

}
