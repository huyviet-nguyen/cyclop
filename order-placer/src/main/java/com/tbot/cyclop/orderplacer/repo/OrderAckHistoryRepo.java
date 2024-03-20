package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Order;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface OrderAckHistoryRepo extends ReactiveMongoRepository<Order, String> {
    Mono<Order> findFirstByStrategyIdOrderByCreatedAtDesc(String strategyId);
}
