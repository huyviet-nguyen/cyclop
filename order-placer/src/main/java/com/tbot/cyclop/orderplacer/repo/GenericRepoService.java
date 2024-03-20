package com.tbot.cyclop.orderplacer.repo;

import com.tbot.cyclop.Cyclop.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;

@Service
public class GenericRepoService {
    private final HashMap<Class<?>, ReactiveMongoRepository<?, String>> repositoryMap = new HashMap<>();
    private final BotRepo botRepo;
    private final CandleWindowRepo candleWindowRepo;
    private final OrderAckHistoryRepo orderAckHistoryRepo;
    private final StrategyMarkerRepo strategyMarkerRepo;
    private final StrategyRepo strategyRepo;
    private final SymbolRepo symbolRepo;
    private final TelegramBotInfoRepo telegramBotInfoRepo;
    private final UserRepo userRepo;

    public GenericRepoService(BotRepo botRepo, CandleWindowRepo candleWindowRepo, OrderAckHistoryRepo orderAckHistoryRepo, StrategyMarkerRepo strategyMarkerRepo, StrategyRepo strategyRepo, SymbolRepo symbolRepo, TelegramBotInfoRepo telegramBotInfoRepo, UserRepo userRepo) {
        this.botRepo = botRepo;
        this.candleWindowRepo = candleWindowRepo;
        this.orderAckHistoryRepo = orderAckHistoryRepo;
        this.strategyMarkerRepo = strategyMarkerRepo;
        this.strategyRepo = strategyRepo;
        this.symbolRepo = symbolRepo;
        this.telegramBotInfoRepo = telegramBotInfoRepo;
        this.userRepo = userRepo;
    }

    @PostConstruct
    public void init() {
        repositoryMap.put(Bot.class, botRepo);
        repositoryMap.put(CandleWindow.class, candleWindowRepo);
        repositoryMap.put(OrderAckHistory.class, orderAckHistoryRepo);
        repositoryMap.put(StrategyMarker.class, strategyMarkerRepo);
        repositoryMap.put(Strategy.class, strategyRepo);
        repositoryMap.put(Symbol.class, symbolRepo);
        repositoryMap.put(TelegramBotInfo.class, telegramBotInfoRepo);
        repositoryMap.put(User.class, userRepo);
    }

    public HashMap<Class<?>, ReactiveMongoRepository<?, String>> repoMap() {
        return this.repositoryMap;
    }
}
