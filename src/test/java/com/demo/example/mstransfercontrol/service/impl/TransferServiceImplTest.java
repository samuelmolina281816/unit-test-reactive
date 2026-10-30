package com.demo.example.mstransfercontrol.service.impl;

import com.demo.example.mstransfercontrol.dto.TransferRequest;
import com.demo.example.mstransfercontrol.dto.TransferResponse;
import com.demo.example.mstransfercontrol.entity.Account;
import com.demo.example.mstransfercontrol.entity.Transfer;
import com.demo.example.mstransfercontrol.mapper.TransferMapper;
import com.demo.example.mstransfercontrol.mapper.impl.TransferMapperImpl;
import com.demo.example.mstransfercontrol.repository.AccountRepository;
import com.demo.example.mstransfercontrol.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TransferRepository transferRepository;

    @Spy
    private TransferMapper transferMapper = new TransferMapperImpl();

    @InjectMocks
    private TransferServiceImpl transferService;

    @Captor
    private ArgumentCaptor<Transfer> transferCaptor;

    @Test
    void shouldFindTransferById() {
        Transfer transfer = Transfer.builder().id(42L).status("successful").build();
        when(transferRepository.findById(42L)).thenReturn(Mono.just(transfer));

        StepVerifier.create(transferService.getTransferById(42L))
                .expectNext(transfer)
                .verifyComplete();

        verify(transferRepository).findById(42L);
    }

    @Test
    void shouldCompleteEmptyWhenTransferIdDoesNotExist() {
        when(transferRepository.findById(404L)).thenReturn(Mono.empty());

        StepVerifier.create(transferService.getTransferById(404L))
                .verifyComplete();

        verify(transferRepository).findById(404L);
    }

    @Test
    void shouldApproveAndSaveTransferWhenDailyTotalReachesLimitExactly() {
        TransferRequest request = request("2000.00");
        Account activeAccount = account("ACTIVE");
        Transfer savedTransfer = new Transfer();
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(activeAccount));
        when(transferRepository.totalTransferredToday(any(), any(), any()))
                .thenReturn(Mono.just(new BigDecimal("15000.00")));
        when(transferRepository.save(any(Transfer.class))).thenReturn(Mono.just(savedTransfer));

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .assertNext(response -> {
                    assertEquals("APPROVED", response.getStatus());
                    assertEquals(request.getSourceAccount(), response.getSourceAccount());
                    assertEquals(request.getTargetAccount(), response.getTargetAccount());
                    assertEquals(request.getAmount(), response.getAmount());
                })
                .verifyComplete();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verify(transferRepository).totalTransferredToday(any(), any(), any());
        verify(transferRepository).save(transferCaptor.capture());
        assertEquals(request.getSourceAccount(), transferCaptor.getValue().getAccountNumber());
        assertEquals(request.getTargetAccount(), transferCaptor.getValue().getAccountDestination());
        assertEquals(request.getAmount(), transferCaptor.getValue().getAmount());
        verify(transferMapper).toTransferEntity(request);
        verify(transferMapper).toApprovedResponse(request);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1", "-0.01"})
    void shouldRejectNonPositiveAmountWithoutCallingRepositories(String amount) {
        TransferRequest request = request(amount);

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Monto debe ser mayor a cero".equals(error.getMessage()))
                .verify();
        verifyNoInteractions(accountRepository, transferRepository);
        verifyNoInteractions(transferMapper);
    }

    @Test
    void shouldRejectNullAmountWithoutCallingRepositories() {
        TransferRequest request = request(null);

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Monto debe ser mayor a cero".equals(error.getMessage()))
                .verify();
        verifyNoInteractions(accountRepository, transferRepository);
        verifyNoInteractions(transferMapper);
    }

    @Test
    void shouldReturnNoSuchElementWhenSourceAccountDoesNotExist() {
        TransferRequest request = request("100.00");
        when(accountRepository.findByAccountNumber(request.getSourceAccount())).thenReturn(Mono.empty());

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof NoSuchElementException
                        && "Cuenta origen no existe".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verifyNoInteractions(transferRepository, transferMapper);
    }

    @Test
    void shouldRejectInactiveAccountWithoutQueryingOrSavingTransfers() {
        TransferRequest request = request("100.00");
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(account("BLOCKED")));

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalStateException
                        && "Cuenta origen no esta activa".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verifyNoInteractions(transferRepository, transferMapper);
    }

    @Test
    void shouldRejectTransferThatExceedsDailyLimitWithoutSaving() {
        TransferRequest request = request("1001.00");
        when(accountRepository.findByAccountNumber(request.getSourceAccount()))
                .thenReturn(Mono.just(account("ACTIVE")));
        when(transferRepository.totalTransferredToday(any(), any(), any()))
                .thenReturn(Mono.just(new BigDecimal("16000.00")));

        Mono<TransferResponse> result = transferService.validateAndTransfer(request);

        StepVerifier.create(result)
                .expectErrorMatches(error -> error instanceof IllegalArgumentException
                        && "Limite diario excedido".equals(error.getMessage()))
                .verify();
        verify(accountRepository).findByAccountNumber(request.getSourceAccount());
        verify(transferRepository).totalTransferredToday(any(), any(), any());
        verify(transferRepository, never()).save(any(Transfer.class));
        verifyNoInteractions(transferMapper);
    }

    private TransferRequest request(String amount) {
        return TransferRequest.builder()
                .customerId("customer-1")
                .sourceAccount("001-100001")
                .targetAccount("001-200001")
                .amount(amount == null ? null : new BigDecimal(amount))
                .build();
    }

    private Account account(String status) {
        return Account.builder()
                .accountNumber("001-100001")
                .customerId("customer-1")
                .status(status)
                .build();
    }
}
