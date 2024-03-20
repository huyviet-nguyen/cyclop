package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.Order;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepo extends ReactiveMongoRepository<Order, String> {
}
