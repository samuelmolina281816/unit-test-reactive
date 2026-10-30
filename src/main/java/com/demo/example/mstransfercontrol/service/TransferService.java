package com.demo.example.mstransfercontrol.service;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.entity.Transfer;
import reactor.core.publisher.Mono;

public interface TransferService {
    Mono<TransferResponse> validateAndTransfer(TransferRequest request);
    Mono<Transfer> getTransferById(Long id);
}
