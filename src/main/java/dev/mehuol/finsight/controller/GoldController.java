package dev.mehuol.finsight.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.mehuol.finsight.dto.ChartData;
import dev.mehuol.finsight.service.GoldMarketService;

@RestController
@RequestMapping("/api/gold")
public class GoldController {

    private final GoldMarketService market;

    public GoldController(GoldMarketService market) {
        this.market = market;
    }

    /** Errors (bad interval, provider down, missing key) are mapped by GlobalExceptionHandler. */
    @GetMapping("/chart")
    public ChartData chart(@RequestParam(defaultValue = "1h") String interval) {
        return market.chart(interval);
    }
}
