package com.tbot.cyclop.orderplacer.service;

import com.tbot.cyclop.Cyclop.model.HttpProxy;
import com.tbot.cyclop.orderplacer.repo.HttpProxyRepo;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class HttpProxyService {
    private final HttpProxyRepo repo;

    private final ArrayList<HttpProxy> proxyArrayList = new ArrayList<>();

    private int invokeCount = 0;

    public HttpProxyService(HttpProxyRepo repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void initProxyList() {
        repo.findAll().toIterable().forEach(proxyArrayList::add);
    }

    public HttpProxy getProxyRoundRobin() {
        int index = invokeCount % proxyArrayList.size();
        invokeCount++;
        return proxyArrayList.get(index);
    }
}
