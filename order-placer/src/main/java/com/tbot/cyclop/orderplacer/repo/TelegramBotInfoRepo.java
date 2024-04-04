package com.tbot.cyclop.orderplacer.repo;

import com.mongodb.lang.NonNull;
import com.tbot.cyclop.Cyclop.model.TelegramBotInfo;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface TelegramBotInfoRepo extends ReactiveMongoRepository<TelegramBotInfo, String> {

    @NonNull
    @Override
    @Cacheable(cacheNames = "telegramBot")
    Flux<TelegramBotInfo> findAll();
}
