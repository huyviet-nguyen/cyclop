package com.tbot.cyclop.orderplacer.service;


import com.tbot.cyclop.Cyclop.model.HttpProxy;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.transport.ProxyProvider;

import java.util.Map;

public abstract class OrderPlacerService {

    abstract HttpProxyService getProxyService();

//    abstract NotificationService getNotificationService();

    private ClientHttpConnector getConnector() {
        HttpProxy proxy = getProxyService().getProxyRoundRobin();
        if (proxy == null) {
            return new ReactorClientHttpConnector();
        }
        HttpClient httpClient = HttpClient.create()
                .proxy(proxySpec -> proxySpec
                        .type(ProxyProvider.Proxy.HTTP)
                        .host(proxy.getProxyUrl())
                        .port(proxy.getProxyPort())
                        .username(proxy.getProxyUsername())
                        .password(a -> proxy.getPassword()).connectTimeoutMillis(20000));
        return new ReactorClientHttpConnector(httpClient);
    }

    private WebClient getWebClient() {
        return WebClient.builder()
                .baseUrl(getUri())
                .defaultHeaders(headers -> getHeaders().forEach(headers::add))
                .clientConnector(getConnector())
                .build();
    }

//    public Mono<String> createOrder(String stringPayload) {
//        WebClient client = getWebClient();
//        return client.post()
//                .uri(UriBuilder::build)
//                .body(BodyInserters.fromValue(stringPayload))
//                .retrieve()
//                .bodyToMono(String.class)
//                .doOnError(e -> getNotificationService().sendNotification());
//    }

    public abstract String getUri();

    public abstract Map<String, String> getHeaders();


}
