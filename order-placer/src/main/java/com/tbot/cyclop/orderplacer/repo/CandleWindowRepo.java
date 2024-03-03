package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.CandleWindow;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CandleWindowRepo extends ReactiveMongoRepository<CandleWindow, String> {
}
