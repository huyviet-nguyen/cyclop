package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.OrderAckHistory;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface OrderAckHistoryRepo extends ReactiveMongoRepository<OrderAckHistory, String> {
    Mono<OrderAckHistory> findFirstByStrategyIdOrderByTimestampDesc(String strategyId);
}
