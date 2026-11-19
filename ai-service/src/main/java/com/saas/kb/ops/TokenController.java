package com.saas.kb.ops;

import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/ai/token")
public class TokenController {

    private final TokenMeterService tokenMeterService;

    public TokenController(TokenMeterService tokenMeterService) {
        this.tokenMeterService = tokenMeterService;
    }

    @PostMapping("/record")
    public String record(@RequestParam String provider,
                         @RequestParam String tenantId,
                         @RequestParam long input,
                         @RequestParam long output) {
        tokenMeterService.record(provider, tenantId, input, output);
        return "已记录: provider=" + provider + ", tenant=" + tenantId + ", tokens=" + (input + output);
    }

    @GetMapping("/provider/{provider}")
    public long provider(@PathVariable String provider) {
        return tokenMeterService.providerTotal(provider);
    }

    @GetMapping("/tenant/{tenantId}")
    public long tenant(@PathVariable String tenantId) {
        return tokenMeterService.tenantTotal(tenantId);
    }
    
    // 🌟 课后作业：成本报表接口
    @GetMapping("/cost-report")
    public Map<String, Double> costReport() {
        return tokenMeterService.getCostReport();
    }
}