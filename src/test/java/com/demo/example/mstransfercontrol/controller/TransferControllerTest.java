package com.demo.example.mstransfercontrol.controller;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.entity.Transfer;
import com.demo.example.mstransfercontrol.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferControllerTest {

    @Mock
    private TransferService transferService;

    @InjectMocks
    private TransferController transferController;

    @Captor
    private ArgumentCaptor<TransferRequest> requestCaptor;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUp() {
        webTestClient = WebTestClient.bindToController(transferController).build();
    }

    @Test
    void shouldReturnApprovedTransferAndInvokeServiceOnceWithRequest() {
        TransferRequest request = TransferRequest.builder()
                .customerId("customer-1")
                .sourceAccount("001-100001")
                .targetAccount("001-200001")
                .amount(new BigDecimal("250.00"))
                .build();
        TransferResponse response = TransferResponse.builder()
                .status("APPROVED")
                .sourceAccount(request.getSourceAccount())
                .targetAccount(request.getTargetAccount())
                .amount(request.getAmount())
                .build();
        when(transferService.validateAndTransfer(any(TransferRequest.class))).thenReturn(Mono.just(response));

        webTestClient.post()
                .uri("/api/transfers/control")
                .bodyValue(request)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("APPROVED")
                .jsonPath("$.sourceAccount").isEqualTo(request.getSourceAccount())
                .jsonPath("$.targetAccount").isEqualTo(request.getTargetAccount())
                .jsonPath("$.amount").isEqualTo(250.00);

        verify(transferService, times(1)).validateAndTransfer(requestCaptor.capture());
        TransferRequest actualRequest = requestCaptor.getValue();
        assertEquals(request.getCustomerId(), actualRequest.getCustomerId());
        assertEquals(request.getSourceAccount(), actualRequest.getSourceAccount());
        assertEquals(request.getTargetAccount(), actualRequest.getTargetAccount());
        assertEquals(request.getAmount(), actualRequest.getAmount());
    }

    @Test
    void shouldReturnTransferById() {
        Transfer transfer = Transfer.builder()
                .id(42L)
                .accountNumber("001-100001")
                .accountDestination("001-200001")
                .customerId("customer-1")
                .status("successful")
                .amount(new BigDecimal("250.00"))
                .build();
        when(transferService.getTransferById(42L)).thenReturn(Mono.just(transfer));

        webTestClient.get()
                .uri("/api/transfers/42")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo(42)
                .jsonPath("$.accountNumber").isEqualTo("001-100001")
                .jsonPath("$.accountDestination").isEqualTo("001-200001")
                .jsonPath("$.customerId").isEqualTo("customer-1")
                .jsonPath("$.status").isEqualTo("successful")
                .jsonPath("$.amount").isEqualTo(250.00);

        verify(transferService).getTransferById(42L);
    }

    @Test
    void shouldReturnNotFoundWhenTransferDoesNotExist() {
        when(transferService.getTransferById(404L)).thenReturn(Mono.empty());

        webTestClient.get()
                .uri("/api/transfers/404")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody().isEmpty();

        verify(transferService).getTransferById(404L);
    }
}
