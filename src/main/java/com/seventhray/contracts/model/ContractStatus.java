package com.seventhray.contracts.model;

public enum ContractStatus {
    DRAFT, REVIEW, APPROVED;

    public ContractStatus next() {
        return switch (this) {
            case DRAFT -> REVIEW;
            case REVIEW -> APPROVED;
            case APPROVED -> null;
        };
    }

    public boolean canMoveTo(ContractStatus target) {
        return next() == target;
    }
}
