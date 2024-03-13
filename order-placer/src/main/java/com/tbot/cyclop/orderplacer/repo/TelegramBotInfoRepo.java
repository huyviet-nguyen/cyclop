package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.TelegramBotInfo;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TelegramBotInfoRepo extends ReactiveMongoRepository<TelegramBotInfo, String> {
}
