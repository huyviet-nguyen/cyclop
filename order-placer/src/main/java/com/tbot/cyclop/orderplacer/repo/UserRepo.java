package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.User;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface UserRepo extends ReactiveMongoRepository<User, String> {
}
