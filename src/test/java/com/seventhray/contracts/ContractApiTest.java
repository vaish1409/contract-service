package com.seventhray.contracts;

import com.jayway.jsonpath.JsonPath;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayOutputStream;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class ContractApiTest {

    private static final String DOCX_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Autowired MockMvc mvc;

    private byte[] sampleDocx() throws Exception {
        try (XWPFDocument d = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            d.createParagraph().createRun().setText("Payment Terms");
            d.createParagraph().createRun().setText(
                    "The Client shall pay a monthly fee of INR 150,000. Invoices are due within 30 days.");
            d.createParagraph().createRun().setText("Termination");
            d.createParagraph().createRun().setText(
                    "Either party may terminate this Agreement by giving 60 days written notice.");
            d.write(out);
            return out.toByteArray();
        }
    }

    private long upload() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "services.docx", DOCX_TYPE, sampleDocx());
        String body = mvc.perform(multipart("/api/contracts").file(f).param("title", "Services"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.nextStatus").value("REVIEW"))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.id")).longValue();
    }

    @Test
    void uploadFetchAndWorkflow() throws Exception {
        long id = upload();

        mvc.perform(get("/api/contracts/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Services"));

        mvc.perform(patch("/api/contracts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/contracts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"REVIEW\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEW"));

        mvc.perform(patch("/api/contracts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(patch("/api/contracts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history.length()").value(3));
    }

    @Test
    void askQuestion() throws Exception {
        long id = upload();
        mvc.perform(post("/api/contracts/{id}/ask", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"How much notice is needed to terminate?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(true))
                .andExpect(jsonPath("$.answer").value(containsString("60 days")));
    }

    @Test
    void rejectsUnsupportedFile() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());
        mvc.perform(multipart("/api/contracts").file(f)).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsFakePdf() throws Exception {
        MockMultipartFile f = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());
        mvc.perform(multipart("/api/contracts").file(f)).andExpect(status().isBadRequest());
    }

    @Test
    void missingContractIs404() throws Exception {
        mvc.perform(get("/api/contracts/{id}", 999999)).andExpect(status().isNotFound());
    }

    @Test
    void invalidStatusValueIs400() throws Exception {
        long id = upload();
        mvc.perform(patch("/api/contracts/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isBadRequest());
    }
}
