# AI Evaluation Dataset

Fixed meeting transcripts (`meetings/`) and the results a correct extraction should produce (`expected/`). They measure extraction quality before and after prompt or model changes (PRD sections 48–49).

| File | What it tests |
|------|---------------|
| `meeting_001` | The PRD demo: clear owners, "by Friday" on a Saturday meeting, one decision, one risk |
| `meeting_002` | Unclear ownership ("someone from backend"), a vague deadline ("soon"), and a suggestion that must **not** become a decision ("maybe we should use Redis") |
| `meeting_003` | A real decision after debate, a proposal nobody accepted, "end of the month", and an explicit calendar date |
| `meeting_004` | Owners who accept work when asked ("Priya, can you take that?" / "Sure"), "tomorrow", an unresolved ownership question, and risks |
| `meeting_005` | Small talk with nothing to extract: checks that nothing is made up |

## Expected-file format

```json
{
  "meetingDate": "2026-10-03",
  "members": ["John Smith"],
  "actionItems": [{"title": "…", "owner": "John" | null, "deadline": "YYYY-MM-DD" | null}],
  "decisions": ["…"],
  "risks": ["…"],
  "mustNotDecide": ["Redis"]
}
```

`members` are the organization members used for owner resolution. `mustNotDecide` lists phrases that must not appear in any extracted decision.

## Running

The evaluation calls the real model and is billed to the key you provide, so it never runs in the normal test suite or in CI:

```bash
cd backend
LLM_API_KEY=sk-ant-... ./mvnw test -Pevaluation
```

It prints a report and writes `evaluation/results/latest.json`. Items are matched to expectations by word overlap. The report shows:

- **Precision, recall and F1** for action items, decisions and risks
- **Owner accuracy**: matched action items whose owner is exactly right, including correctly leaving it empty
- **Deadline accuracy**: matched action items whose final resolved deadline is exactly right
- **Decision traps**: decisions that contain a `mustNotDecide` phrase. This should be 0.
