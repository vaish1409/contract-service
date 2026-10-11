# Contract Service

A Java / Spring Boot backend for the Seventh Ray internship assignment.

**What it does**

1. Upload a contract (PDF, DOCX or DOC) and store it.
2. Move its status through the workflow **DRAFT → REVIEW → APPROVED**.
3. Fetch contracts and their status history.
4. Ask plain-English questions about a contract and get the matching clause back.

## Tech

Java 17+ · Spring Boot 3.3 · Spring Data JPA · H2 (file database) · Apache PDFBox 3 (PDF) · Apache POI 5 (DOCX/DOC) · JUnit 5 + MockMvc.

## Run it

Requires JDK 17 or newer and Maven 3.8+.

```bash
mvn test            # unit + API tests
mvn spring-boot:run # starts on http://localhost:8080
```

Data is kept in `./data` (database) and `./uploads` (original files). Delete both folders to reset.

## API

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/contracts` | Upload a contract (multipart: `file`, optional `title`). Returns 201, status `DRAFT`. |
| GET | `/api/contracts` | List contracts. Optional `?status=DRAFT\|REVIEW\|APPROVED`. |
| GET | `/api/contracts/{id}` | One contract: metadata, status, next allowed status, history, text preview. |
| PATCH | `/api/contracts/{id}/status` | Body `{"status":"REVIEW"}`. Moves one step forward only. |
| POST | `/api/contracts/{id}/ask` | Body `{"question":"..."}`. Returns the best answer and top 3 passages. |
| GET | `/api/contracts/{id}/text` | Full extracted text. |
| GET | `/api/contracts/{id}/file` | Download the original file. |

### Try it with curl

```bash
curl -F "file=@contract.pdf" -F "title=Vendor agreement" http://localhost:8080/api/contracts

curl http://localhost:8080/api/contracts/1

curl -X PATCH http://localhost:8080/api/contracts/1/status \
     -H "Content-Type: application/json" -d '{"status":"REVIEW"}'

curl -X POST http://localhost:8080/api/contracts/1/ask \
     -H "Content-Type: application/json" -d '{"question":"What is the notice period for termination?"}'
```

Example answer:

```json
{
  "question": "What is the notice period for termination?",
  "found": true,
  "answer": "3. Termination Either party may terminate this Agreement by giving 60 days written notice.",
  "passages": [ { "rank": 1, "score": 0.41, "text": "..." } ]
}
```

Errors always come back as JSON: `{ timestamp, status, error, message }`
(400 bad input or illegal status move, 404 unknown id, 413 file over 10MB, 422 unreadable document).

## How it works

- **Parsing.** The file type is checked from its first bytes *and* its extension, so a renamed `.txt` is rejected. PDFBox extracts PDF text; POI extracts DOCX (paragraphs and tables, in order) and DOC. Scanned, image-only PDFs have no text layer and are rejected with a clear message (OCR is out of scope).
- **Storage.** The original file is saved on disk under a random UUID name (the user's filename is never used as a path, which prevents path traversal). Metadata and extracted text are saved in the database.
- **Workflow.** `ContractStatus` allows exactly one move from each state; every change is recorded in a `status_changes` table.
- **Q&A (keyword search, no external AI).** The contract is split into passages (headings are joined to the paragraph below). The question and passages are lower-cased, stop-words removed and lightly stemmed ("terminate", "termination", "terminated" all match). Passages are ranked with BM25, so rare words count more than common ones, and a small synonym list maps everyday words to contract language ("how long" → term/duration, "cancel" → terminate). If nothing matches, it says so instead of guessing.

## Design decisions and limits

- **Forward-only workflow** follows the brief literally. Allowing "send back to DRAFT" would be a one-line change in `ContractStatus`.
- **No authentication.** Out of scope for the 48-hour brief; the next step would be Spring Security with roles (e.g. only a reviewer can approve).
- **H2 file database** keeps setup to zero. Switching to PostgreSQL only needs the datasource properties and driver.
- **Q&A is lexical.** It will miss questions whose words never appear in the contract. The natural upgrade is embeddings or an LLM over the top passages already returned by `QaService`; the retrieval step would stay the same.
- **Ideas for later:** pagination on the list endpoint, OCR for scanned files, Swagger UI, Dockerfile.

## Project layout

```
src/main/java/com/seventhray/contracts/
  controller/   REST endpoints
  service/      ContractService (workflow), QaService (search), FileStorageService
  parser/       DocumentParser (PDF / DOCX / DOC)
  model/        Contract, StatusChange, ContractStatus
  repository/   Spring Data repository
  dto/          request / response records
  exception/    error types + global JSON error handler
src/test/java/  QaServiceTest, ContractStatusTest, ContractApiTest
```
