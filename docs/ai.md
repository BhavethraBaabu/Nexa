# AI

Meeting analysis (Phase 3) turns a transcript into a summary, key points, action items, decisions, risks and open questions. It follows PRD sections 10–14 and 32–35. The pipeline is described in [architecture.md](architecture.md#ai-analysis-phase-3).

## Model and settings

| Setting | Env var | Default |
|---|---|---|
| API key | `LLM_API_KEY` | none (analysis reports "not configured") |
| Model | `LLM_MODEL` | `claude-opus-5-5` |
| Effort | `LLM_EFFORT` | `medium` |
| Max output tokens | | 16,000 |
| Max transcript | | 300,000 characters |

Effort trades thoroughness for speed and cost. `medium` aims at the PRD's target of under 10 seconds for a normal transcript. Raise it if evaluation shows extraction errors on hard transcripts.

## Safeguards against hallucination

| Rule (PRD §33) | How it's enforced |
|---|---|
| Never invent people | The prompt asks for the name exactly as spoken, or null. `MemberNameResolver` accepts only a unique match among active members. Otherwise the owner is `UNRESOLVED` or `UNASSIGNED`. |
| Never invent deadlines | The model reports the exact phrase it heard (`deadlineText`). `DeadlineResolver` computes the date in code. Vague phrases are `NEEDS_REVIEW`, with no date. |
| Separate decisions from suggestions | The prompt defines decisions as agreed outcomes. The evaluation set includes a "maybe we should use Redis" trap. |
| Quote supporting context | Every item carries a verbatim `evidence` quote. Items whose quote isn't found in the transcript are capped at 0.50 confidence (LOW). |
| Structured JSON | Claude structured outputs with a strict schema. The output is then parsed and validated before anything is saved. |
| Transcript is data | Prompt-injection text inside the transcript is treated as something a participant said. |

## Evaluation

See [`/evaluation`](../evaluation/README.md). It runs with `LLM_API_KEY=... ./mvnw test -Pevaluation` and reports precision, recall, F1, owner accuracy, deadline accuracy and decision traps.
