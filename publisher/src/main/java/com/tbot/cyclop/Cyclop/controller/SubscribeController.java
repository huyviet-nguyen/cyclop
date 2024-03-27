package com.tbot.cyclop.Cyclop.controller;

import com.tbot.cyclop.Cyclop.service.BybitSocketService;
import com.tbot.cyclop.Cyclop.service.MexcSocketService;
import com.tbot.cyclop.Cyclop.service.PlatformSocketService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.lang.reflect.Method;
import java.util.*;

@RestController
@RequestMapping("/api")
public class SubscribeController {

    @Value("${app.apiUser}")
    private String apiKey;

    @Value("${app.apiPassword}")
    private String apiPassword;

    private final MexcSocketService mexcSocketService;
    private final BybitSocketService bybitSocketService;
    private final Map<String, HashMap<String, Set<String>>> activeMap;
    public SubscribeController(MexcSocketService mexcSocketService, BybitSocketService bybitSocketService, @Qualifier("activeMap") Map<String, HashMap<String, Set<String>>> activeMap) {
        this.mexcSocketService = mexcSocketService;
        this.bybitSocketService = bybitSocketService;
        this.activeMap = activeMap;
    }


    @PostMapping("/listen")
    public ResponseEntity<?> subscribe(@RequestParam("platform") String platform, @RequestHeader("Authorization") String authHeader, @RequestBody List<String> listSubscribe, @RequestParam("action") String action) {
        String decodedAuth = new String(Base64.getDecoder().decode(authHeader.replace("Basic ", "")));
        if (!decodedAuth.equals(String.join(":", apiKey, apiPassword))) {
            return ResponseEntity.badRequest().build();
        }
        try {
            PlatformSocketService service = switch (platform.toLowerCase()) {
                case "mexc" -> mexcSocketService;
                case "bybit" -> bybitSocketService;
                default -> throw new IllegalStateException("Unexpected value: " + platform.toLowerCase());
            };
            Method actionMethod = getActionMethod(action);
            for (String subscribe : listSubscribe) {
                String symbol = subscribe.split("\\.")[0];
                String interval = subscribe.split("\\.")[1];
                actionMethod.invoke(service, symbol, Integer.parseInt(interval));
                switch (action) {
                    case "sub": {
                        activeMap.get(platform).get(interval).add(symbol);
                    }
                    break;
                    case "unsub": {
                        activeMap.get(platform).get(interval).remove(symbol);
                    }
                    break;
                }
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e);
        }
        return ResponseEntity.ok("{\"success\": \"true\"}");
    }


    @GetMapping("/active")
    public ResponseEntity<?> getActiveMap(@RequestHeader("Authorization") String authHeader) {
        String decodedAuth = new String(Base64.getDecoder().decode(authHeader.replace("Basic ", "")));
        if (!decodedAuth.equals(String.join(":", apiKey, apiPassword))) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(activeMap);
    }

    private Method getActionMethod(String action) throws NoSuchMethodException {
        return switch (action.toLowerCase()) {
            case "sub" -> PlatformSocketService.class.getDeclaredMethod("subscribe", String.class, int.class);
            case "unsub" -> PlatformSocketService.class.getDeclaredMethod("unsubscribe", String.class, int.class);
            default -> throw new IllegalStateException("Unexpected action: " + action.toLowerCase());
        };
    }
}
