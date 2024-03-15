package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Bot;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BotRepo extends ReactiveMongoRepository<Bot, String> { }
