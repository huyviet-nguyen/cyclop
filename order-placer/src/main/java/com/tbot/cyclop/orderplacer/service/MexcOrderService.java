package com.tbot.cyclop.orderplacer.service;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MexcOrderService extends OrderPlacerService {

    private final NotificationService notificationService;
    private final HttpProxyService proxyService;

    public MexcOrderService(NotificationService notificationService, HttpProxyService proxyService) {
        this.notificationService = notificationService;
        this.proxyService = proxyService;
    }

    @Override
    HttpProxyService getProxyService() {
        return proxyService;
    }

    @Override
    NotificationService getNotificationService() {
        return notificationService;
    }

    @Override
    public String getUri() {
        return "https://futures.mexc.com/api/v1/private/order/create";
    }

    @Override
    public Map<String, String> getHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("authority", "futures.mexc.com");
        headers.put("accept", "*/*");
        headers.put("accept-language", "en-US,en;q=0.9,vi-VN;q=0.8,vi;q=0.7");
        headers.put("content-type", "application/json");
        headers.put("language", "English");
        headers.put("origin", "https://futures.mexc.com");
        headers.put("pragma", "akamai-x-cache-on");
        headers.put("referer", "https://futures.mexc.com/vi-VN/exchange/TURBO_USDT?type=linear_swap");
        headers.put("sec-ch-ua", "\"Not A(Brand\";v=\"99\", \"Google Chrome\";v=\"121\", \"Chromium\";v=\"121\"");
        headers.put("sec-ch-ua-mobile", "?0");
        headers.put("sec-ch-ua-platform", "\"macOS\"");
        headers.put("sec-fetch-dest", "empty");
        headers.put("sec-fetch-mode", "cors");
        headers.put("sec-fetch-site", "same-origin");
        headers.put("trochilus-trace-id", "e531b4d1-05ad-4dd1-a26a-b2c245178fe1-0404");
        headers.put("trochilus-uid", "20738208");
        headers.put("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36");
        headers.put("x-mxc-nonce", "1710056884758");
        headers.put("x-mxc-sign", "3b879ee0f69f01afa1d1ce55d1f972c8");
        return headers;
    }
}
