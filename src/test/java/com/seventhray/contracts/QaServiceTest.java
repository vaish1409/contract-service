package com.seventhray.contracts;

import com.seventhray.contracts.dto.AnswerResponse;
import com.seventhray.contracts.service.QaService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QaServiceTest {

    private static final String CONTRACT = """
            SERVICES AGREEMENT

            1. Term
            This Agreement begins on 1 March 2026 and continues for twelve (12) months unless terminated earlier.

            2. Payment Terms
            The Client shall pay a monthly fee of INR 150,000. Invoices are due within 30 days of receipt.

            3. Termination
            Either party may terminate this Agreement by giving 60 days written notice.

            4. Governing Law
            This Agreement is governed by the laws of India. Courts in Bengaluru have exclusive jurisdiction.
            """;

    private final QaService qa = new QaService();

    @Test
    void findsPaymentClause() {
        AnswerResponse r = qa.answer("What are the payment terms?", CONTRACT);
        assertTrue(r.found());
        assertTrue(r.answer().contains("150,000"));
    }

    @Test
    void findsTerminationNotice() {
        AnswerResponse r = qa.answer("How much notice to terminate?", CONTRACT);
        assertTrue(r.found());
        assertTrue(r.answer().contains("60 days"));
    }

    @Test
    void findsJurisdiction() {
        AnswerResponse r = qa.answer("Which court has jurisdiction?", CONTRACT);
        assertTrue(r.answer().contains("Bengaluru"));
    }

    @Test
    void synonymHelpsDurationQuestion() {
        AnswerResponse r = qa.answer("How long does the agreement last?", CONTRACT);
        assertTrue(r.answer().contains("twelve (12) months"));
    }

    @Test
    void unknownTopicIsNotFound() {
        AnswerResponse r = qa.answer("Who provides the warranty?", CONTRACT);
        assertFalse(r.found());
        assertTrue(r.passages().isEmpty());
    }

    @Test
    void emptyContractIsHandled() {
        assertFalse(qa.answer("payment?", "").found());
    }
}
