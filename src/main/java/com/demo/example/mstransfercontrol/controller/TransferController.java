package com.demo.example.mstransfercontrol.controller;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.entity.Transfer;
import com.demo.example.mstransfercontrol.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/")
public class TransferController {

    private final TransferService transferService;

    @PostMapping("/transfers/control")
    public Mono<TransferResponse> transfer(@RequestHeader Map<String, String> headers,
                                           @RequestBody TransferRequest request) {
        return transferService.validateAndTransfer(request);
    }

    @GetMapping("/transfers/{id}")
    public Mono<ResponseEntity<Transfer>> getTransferById(@PathVariable Long id) {
        return transferService.getTransferById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

}
