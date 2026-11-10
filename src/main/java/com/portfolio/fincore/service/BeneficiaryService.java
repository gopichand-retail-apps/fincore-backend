package com.portfolio.fincore.service;

import com.portfolio.fincore.api.ApiException;
import com.portfolio.fincore.api.ApiModels.AddBeneficiaryRequest;
import com.portfolio.fincore.api.ApiModels.BeneficiaryResponse;
import com.portfolio.fincore.api.ApiModels.RecipientOption;
import com.portfolio.fincore.domain.BankAccount;
import com.portfolio.fincore.domain.Beneficiary;
import com.portfolio.fincore.domain.DemoPrincipal;
import com.portfolio.fincore.domain.DemoRole;
import com.portfolio.fincore.domain.DemoUser;
import com.portfolio.fincore.repository.BankAccountRepository;
import com.portfolio.fincore.repository.BeneficiaryRepository;
import com.portfolio.fincore.repository.DemoUserRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {
    private final AccountService accountService;
    private final BankAccountRepository accounts;
    private final BeneficiaryRepository beneficiaries;
    private final DemoUserRepository users;

    public BeneficiaryService(
            AccountService accountService,
            BankAccountRepository accounts,
            BeneficiaryRepository beneficiaries,
            DemoUserRepository users) {
        this.accountService = accountService;
        this.accounts = accounts;
        this.beneficiaries = beneficiaries;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public List<BeneficiaryResponse> list(DemoPrincipal principal) {
        accountService.requireCustomer(principal);
        return beneficiaries.findByOwner_IdAndActiveTrueOrderByAliasAsc(principal.userId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RecipientOption> directory(DemoPrincipal principal) {
        accountService.requireCustomer(principal);
        return accounts.findAllWithOwner().stream()
                .filter(account -> account.getOwner().getRole() == DemoRole.CUSTOMER)
                .filter(account -> !account.getOwner().getId().equals(principal.userId()))
                .map(account -> new RecipientOption(
                        account.getId(),
                        account.getOwner().getDisplayName(),
                        account.getDisplayName(),
                        account.getMaskedAccountNumber()))
                .toList();
    }

    @Transactional
    public BeneficiaryResponse add(DemoPrincipal principal, AddBeneficiaryRequest request) {
        accountService.requireCustomer(principal);
        String alias = request.alias().strip();
        if (alias.length() < 2 || alias.codePoints().anyMatch(Character::isISOControl)) {
            throw ApiException.badRequest("INVALID_BENEFICIARY", "Enter a valid beneficiary name.");
        }

        BankAccount destination = accounts.findById(request.destinationAccountId())
                .orElseThrow(() -> ApiException.notFound("RECIPIENT_NOT_FOUND", "Recipient was not found."));
        if (destination.getOwner().getId().equals(principal.userId())) {
            throw ApiException.badRequest("OWN_ACCOUNT_BENEFICIARY", "Choose another synthetic customer account.");
        }
        if (destination.getOwner().getRole() != DemoRole.CUSTOMER) {
            throw ApiException.notFound("RECIPIENT_NOT_FOUND", "Recipient was not found.");
        }
        if (beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(
                principal.userId(), destination.getId())) {
            throw ApiException.conflict("BENEFICIARY_EXISTS", "That recipient is already in your beneficiary list.");
        }

        DemoUser owner = users.getReferenceById(principal.userId());
        return toResponse(beneficiaries.save(new Beneficiary(
                UUID.randomUUID().toString(), owner, destination, alias)));
    }

    @Transactional
    public void remove(DemoPrincipal principal, String beneficiaryId) {
        accountService.requireCustomer(principal);
        Beneficiary beneficiary = beneficiaries.findByIdAndOwner_IdAndActiveTrue(beneficiaryId, principal.userId())
                .orElseThrow(() -> ApiException.notFound("BENEFICIARY_NOT_FOUND", "Beneficiary was not found."));
        beneficiary.deactivate();
    }

    @Transactional(readOnly = true)
    public boolean isAuthorizedDestination(String ownerId, String accountId) {
        return beneficiaries.existsByOwner_IdAndDestinationAccount_IdAndActiveTrue(ownerId, accountId);
    }

    @Transactional(readOnly = true)
    public String destinationAlias(String ownerId, String accountId) {
        return beneficiaries.findByOwner_IdAndDestinationAccount_IdAndActiveTrue(ownerId, accountId)
                .map(Beneficiary::getAlias)
                .orElse(null);
    }

    private BeneficiaryResponse toResponse(Beneficiary beneficiary) {
        BankAccount destination = beneficiary.getDestinationAccount();
        return new BeneficiaryResponse(
                beneficiary.getId(),
                beneficiary.getAlias(),
                destination.getId(),
                destination.getDisplayName(),
                destination.getMaskedAccountNumber(),
                beneficiary.getCreatedAt());
    }
}