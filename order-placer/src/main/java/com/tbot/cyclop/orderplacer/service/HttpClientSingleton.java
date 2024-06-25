package com.tbot.cyclop.orderplacer.service;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

public class HttpClientSingleton {
    private static final int MAX_CONNECTIONS = 75;
    private static final int MAX_PER_ROUTE = 20;
    private static PoolingHttpClientConnectionManager connectionManager;
    private static final CloseableHttpClient httpClient;

    static {
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(MAX_CONNECTIONS);
        connectionManager.setDefaultMaxPerRoute(MAX_PER_ROUTE);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10000)
                .setConnectionRequestTimeout(10000)
                .build();

        httpClient = HttpClientBuilder.create()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .build();
    }

    public static CloseableHttpClient getHttpClient() {
        return httpClient;
    }

}
