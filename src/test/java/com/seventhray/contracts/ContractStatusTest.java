package com.seventhray.contracts;

import com.seventhray.contracts.model.ContractStatus;
import org.junit.jupiter.api.Test;

import static com.seventhray.contracts.model.ContractStatus.*;
import static org.junit.jupiter.api.Assertions.*;

class ContractStatusTest {

    @Test
    void onlyForwardOneStep() {
        assertTrue(DRAFT.canMoveTo(REVIEW));
        assertTrue(REVIEW.canMoveTo(APPROVED));
        assertFalse(DRAFT.canMoveTo(APPROVED));
        assertFalse(REVIEW.canMoveTo(DRAFT));
        assertFalse(APPROVED.canMoveTo(REVIEW));
        assertNull(APPROVED.next());
    }
}
