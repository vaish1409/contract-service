package com.seventhray.contracts.dto;

import com.seventhray.contracts.model.ContractStatus;
import jakarta.validation.constraints.NotNull;

public record StatusUpdateRequest(@NotNull ContractStatus status) {}
