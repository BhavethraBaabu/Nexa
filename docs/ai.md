# AI

Not implemented yet (Phase 3). The design follows PRD sections 10–14 and 32–35:

- Structured JSON output validated against a schema. Free-form LLM text is never parsed.
- The model never invents owners or deadlines. Missing information is returned as `null` and flagged for review.
- Every extracted entity carries a confidence score from 0.00 to 1.00 (HIGH ≥ 0.90, MEDIUM ≥ 0.70, LOW below that) and supporting evidence.
- Analysis runs asynchronously (`202 Accepted` and a background job).
- Accuracy is measured against the dataset in `/evaluation`.
