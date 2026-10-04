# Nexa
AI-Powered Meeting-to-Work Agent  
Where meetings become momentum.  
Document: Product Requirements Document  
Version: 1.0  
Status: Build Specification  
Product: Nexa  
Repository: nexa-ai  
Primary Goal: Build a production-quality AI SaaS application that converts meeting conversations into structured work and executes approved actions across workplace tools.  


## 1. Executive Summary
Nexa is an AI-powered meeting-to-work platform.  
Traditional meeting assistants stop at:  
Meeting  
   ↓  
Transcript  
   ↓  
Summary  
Nexa goes further:  
Meeting  
   ↓  
Transcript  
   ↓  
AI Understanding  
   ↓  
Tasks + Decisions + Owners + Deadlines + Risks  
   ↓  
Human Review  
   ↓  
Approved Actions  
   ↓  
Jira + Slack + GitHub + Email  
   ↓  
Tracking + Follow-up  
Nexa's purpose is to eliminate the gap between what teams discuss and what teams actually execute.  
The system uses AI to understand meeting context, extract structured information, propose actions, and execute approved actions through controlled integrations.  


## 2. Product Vision
Nexa should become an AI execution layer for modern teams.  
The long-term product vision is:  
Any meaningful conversation should be capable of becoming structured, trackable, executable work.  
Nexa should eventually understand conversations across:  
- meetings
- Slack
- email
- project management tools
- GitHub
- documents
- calendars
and connect them into one intelligent work graph.  


## 3. Problem Statement
Teams lose valuable information during and after meetings.  
Common problems:  
Problem 1 — Lost action items  
People say:  
"I'll take care of that."  
but the task is never recorded.  
Problem 2 — Unclear ownership  
Meetings often contain ambiguous statements such as:  
"Someone should update the documentation."  
Nexa should identify that ownership is unclear rather than hallucinating an owner.  
Problem 3 — Manual administrative work  
Employees manually:  
- create Jira tickets
- send Slack updates
- write meeting follow-up emails
- schedule follow-ups
- update project documentation
Problem 4 — Decisions become difficult to find  
Weeks later:  
"Why did we choose PostgreSQL?"  
The team has to search old meeting notes.  
Problem 5 — No connection between meetings and execution  
Meeting tools record what happened.  
Project management tools record what needs to happen.  
Nexa connects the two.  


## 4. Product Principles

### 4.1 AI proposes, humans control
Nexa must not blindly execute consequential actions.  
The default workflow is:  
AI Suggestion  
      ↓  
Human Review  
      ↓  
Approval  
      ↓  
Execution  

### 4.2 Never hallucinate ownership
If the transcript does not provide enough information to identify an owner:  
Owner: Unassigned  
Confidence: LOW  
Never invent a person.  

### 4.3 Structured AI output
AI responses should be returned as validated structured JSON wherever possible.  
Do not rely on parsing free-form LLM responses.  

### 4.4 Every external action is auditable
Every Jira ticket, Slack message, GitHub issue, or email action must have:  
- requester
- approver
- timestamp
- action type
- integration
- result
- external ID

### 4.5 Integrations are replaceable
The AI layer must not directly depend on Jira/Slack implementation details.  
Use an abstraction:  
Action  
 ↓  
Tool Interface  
 ↓  
Integration Adapter  
 ↓  
External Service  


## 5. Target Users

### 5.1 Software Engineer
Wants:  
- clear responsibilities
- relevant meeting decisions
- automatically created tasks

### 5.2 Engineering Manager
Wants:  
- visibility into action items
- ownership
- deadlines
- project risks

### 5.3 Product Manager
Wants:  
- requirements extracted from meetings
- decisions documented
- Jira tickets created

### 5.4 Startup Founder
Wants:  
- fewer administrative tasks
- meeting accountability
- team execution visibility


## 6. MVP Definition
The MVP must allow a user to:  
- Register/login.
- Create an organization.
- Invite members.
- Upload/paste a meeting transcript.
- Process the transcript using AI.
- Generate a meeting summary.
- Extract tasks.
- Extract decisions.
- Extract owners.
- Extract deadlines.
- Extract risks.
- Assign confidence scores.
- Review AI-generated actions.
- Edit actions.
- Approve/reject actions.
- Connect Jira.
- Create Jira issues.
- Connect Slack.
- Send Slack messages.
- Generate follow-up email drafts.
- Search previous meetings.
- View task history.
- View AI execution history.
- View system activity/audit logs.


## 7. Features

### 7.1 Authentication
Users can:  
- register
- login
- logout
- refresh sessions
- reset password
Authentication:  
Spring Security  
Preferred:  
- OAuth 2.0
- JWT access tokens
- secure refresh-token mechanism


### 7.2 Organizations
Each user belongs to an organization.  
Example:  
Acme  

Members  
├── Alice — ADMIN  
├── Bob — MANAGER  
├── Sarah — MEMBER  
└── John — MEMBER  
Organizations provide tenant isolation.  
A user must never access another organization's:  
- meetings
- tasks
- integrations
- AI results
- audit logs


### 7.3 Role-Based Access Control
Roles:  
ADMIN  
Can:  
- manage organization
- invite/remove members
- manage integrations
- view all meetings
- manage settings
MANAGER  
Can:  
- create meetings
- view team meetings
- approve actions
- manage tasks
MEMBER  
Can:  
- upload meetings
- view permitted meetings
- view assigned tasks
- approve permitted actions


## 8. Meeting Management
Users can create meetings manually.  
Fields:  
Meeting ID  
Organization ID  
Title  
Date  
Duration  
Participants  
Transcript  
Created By  
Created At  
Example:  
Payment Architecture Review  

Participants:  
Sarah  
John  
Mike  

Date:  
2026-10-03  


## 9. Transcript Input
MVP supports:  
- pasted transcript
- TXT
- PDF
- DOCX
Future support:  
- Zoom
- Google Meet
- Microsoft Teams


## 10. AI Meeting Processing
When the user clicks:  
Analyze Meeting  
Nexa runs:  
Transcript  
    ↓  
Preprocessing  
    ↓  
Chunking  
    ↓  
AI Extraction  
    ↓  
Schema Validation  
    ↓  
Entity Resolution  
    ↓  
Confidence Scoring  
    ↓  
Save Results  


## 11. AI Output
The AI must identify:  

### 11.1 Summary
Generate:  
- executive summary
- key discussion points
- important context


### 11.2 Action Items
Each action should contain:  
{  
  "title": "Implement Redis caching",  
  "description": "Add Redis caching to reduce repeated database queries.",  
  "owner": "John",  
  "deadline": "2026-10-10",  
  "priority": "HIGH",  
  "confidence": 0.94  
}  


### 11.3 Decisions
Example:  
{  
  "decision": "Use PostgreSQL instead of MongoDB",  
  "context": "Primary application database",  
  "confidence": 0.97  
}  


### 11.4 Risks
Example:  
{  
  "risk": "Database migration may delay release",  
  "severity": "HIGH",  
  "confidence": 0.89  
}  


### 11.5 Unresolved Questions
Example:  
{  
  "question": "Who owns the production migration?",  
  "status": "UNRESOLVED"  
}  


## 12. Confidence Model
Every extracted entity must have a confidence score.  
Range:  

### 0.00 → 1.00
UI representation:  
90–100%   HIGH  
70–89%    MEDIUM  
0–69%     LOW  
Low-confidence results must be clearly marked.  


## 13. Owner Resolution
Nexa must resolve owners against organization members.  
Example transcript:  
John will implement Redis caching.  
AI extracts:  
John  
System searches organization members.  
John Smith  
john@company.com  
Resolved owner:  
user_id: 123  
If no matching member exists:  
owner = null  
owner_status = UNRESOLVED  
Nexa must never invent an organization member.  


## 14. Deadline Resolution
Relative dates must be resolved using the meeting date.  
Example:  
"Finish this by Friday."  
Meeting date:  
October 3, 2026  
System resolves Friday to:  
October 9, 2026  
If the deadline is ambiguous:  
deadline_status = NEEDS_REVIEW  


## 15. Human Review
After AI processing, the user sees:  
AI Suggested Actions  

☑ Create Jira ticket  
☑ Send Slack notification  
☐ Create GitHub issue  
☑ Draft email  

[Approve Selected]  
[Reject]  
Users can edit:  
- title
- description
- owner
- priority
- deadline
before approval.  


## 16. Action Lifecycle
Every action follows:  
SUGGESTED  
    ↓  
EDITED  
    ↓  
APPROVED  
    ↓  
EXECUTING  
    ↓  
COMPLETED  
Failure:  
EXECUTING  
    ↓  
FAILED  
    ↓  
RETRY  
Possible states:  
PENDING  
APPROVED  
REJECTED  
EXECUTING  
COMPLETED  
FAILED  
CANCELLED  


## 17. Jira Integration
Users can connect Jira through OAuth.  
Nexa should allow users to select:  
- Jira workspace
- project
- issue type
- default priority
When an action is approved:  
Nexa  
 ↓  
Jira Adapter  
 ↓  
Jira REST API  
 ↓  
Create Issue  
Example:  
PAY-102  

Title:  
Implement Redis caching  

Description:  
Add Redis caching to Payment Service.  

Assignee:  
John  

Priority:  
High  

Due:  
October 10  
Store:  
external_issue_id  
external_url  
integration_id  


## 18. Slack Integration
Users connect Slack.  
Nexa should allow selection of:  
- workspace
- channel
Example:  
📌 Nexa Meeting Action  

Task:  
Implement Redis caching  

Owner:  
John  

Due:  
October 10  

Priority:  
High  

Source:  
Payment Architecture Review  
Store:  
channel_id  
message_id  
timestamp  


## 19. Email Generation
Nexa generates follow-up drafts.  
Example:  
Subject:  
Payment Architecture Review — Action Items  

Hi team,  

Here are the key action items from today's meeting:  

• John — Implement Redis caching — Oct 10  
• Sarah — Review implementation — Oct 11  

Decision:  
PostgreSQL was selected for the new service.  

Thanks,  
Nexa  
MVP:  
Draft only.  
Future:  
- Gmail
- Outlook
- automatic sending with approval


## 20. GitHub Integration
MVP+ feature.  
Approved actions can become GitHub issues.  
Example:  
Issue:  
Improve API error handling  

Description:  
Implement centralized exception handling.  

Acceptance Criteria:  
- Standard error format  
- HTTP status mapping  
- Structured logging  
- Unit tests  


## 21. RAG / Meeting Memory
Nexa should maintain searchable historical meeting knowledge.  
Pipeline:  
Meeting  
 ↓  
Chunk  
 ↓  
Embedding  
 ↓  
Vector Store  
 ↓  
Semantic Retrieval  
 ↓  
LLM  
Technology:  
PostgreSQL + pgvector  
Example:  
"What database did we decide to use?"  
Nexa retrieves relevant historical decisions before answering.  
Responses should cite the source meeting:  
According to the October 3 Architecture Review,  
the team selected PostgreSQL.  

Source:  
Payment Architecture Review  
October 3, 2026  


## 22. AI Agent
The AI agent should have controlled tools.  
Tools:  
searchMeetings()  
searchTasks()  
getTeamMembers()  
createJiraIssue()  
sendSlackMessage()  
createGitHubIssue()  
draftEmail()  
The AI cannot directly access:  
- database
- access tokens
- arbitrary HTTP endpoints
All tool calls go through Spring Boot.  
LLM  
 ↓  
Tool Request  
 ↓  
Tool Executor  
 ↓  
Authorization  
 ↓  
Integration Adapter  
 ↓  
External API  


## 23. Agent Safety
The agent must distinguish between:  
Read operations  
Generally safe:  
searchMeetings()  
getTeamMembers()  
searchTasks()  
Write operations  
Require approval:  
createJiraIssue()  
sendSlackMessage()  
createGitHubIssue()  
sendEmail()  
MVP must require explicit user approval for all write actions.  


## 24. Dashboard
Main dashboard:  
---------------------------------------------  
Nexa  
---------------------------------------------  

Good evening, Bhavethra  

Meetings  
12  

Action Items  
37  

Completed  
24  

Overdue  
5  

Pending Approval  
8  
---------------------------------------------  

Recent Meetings  

Payment Architecture Review  
Oct 3  

Sprint Planning  
Oct 2  

Product Roadmap  
Oct 1  
---------------------------------------------  


## 25. Meeting Detail Page
------------------------------------------------  
Payment Architecture Review  
October 3, 2026  
------------------------------------------------  

SUMMARY  

The team discussed...  

------------------------------------------------  

ACTION ITEMS  

John  
Implement Redis caching  
Due: Oct 10  
Confidence: 94%  

Sarah  
Review implementation  
Due: Oct 11  
Confidence: 91%  

------------------------------------------------  

DECISIONS  

✓ PostgreSQL selected  
✓ Redis approved for caching  

------------------------------------------------  

RISKS  

⚠ Migration could delay release  

------------------------------------------------  

AI ACTIONS  

[Create Jira]  
[Send Slack]  
[Draft Email]  
------------------------------------------------  


## 26. Task Dashboard
Filters:  
- All
- My Tasks
- Team Tasks
- Overdue
- Completed
- Pending Approval
Each task displays:  
Task  
Owner  
Priority  
Deadline  
Source Meeting  
Status  
External Links  


## 27. Meeting Search
Users can search:  
database decision  
Redis  
deployment  
payment service  
Sarah  
Search should support:  
Keyword search  
PostgreSQL full-text search.  
Semantic search  
pgvector embeddings.  


## 28. Database Schema
organizations  
id UUID PK  
name VARCHAR  
created_at TIMESTAMP  
updated_at TIMESTAMP  
users  
id UUID PK  
organization_id UUID FK  
name VARCHAR  
email VARCHAR UNIQUE  
password_hash VARCHAR  
role VARCHAR  
created_at TIMESTAMP  
updated_at TIMESTAMP  
meetings  
id UUID PK  
organization_id UUID FK  
title VARCHAR  
meeting_date TIMESTAMP  
transcript TEXT  
summary TEXT  
status VARCHAR  
created_by UUID FK  
created_at TIMESTAMP  
updated_at TIMESTAMP  
meeting_participants  
id UUID PK  
meeting_id UUID FK  
user_id UUID FK  
tasks  
id UUID PK  
meeting_id UUID FK  
organization_id UUID FK  
title VARCHAR  
description TEXT  
owner_id UUID FK NULL  
priority VARCHAR  
status VARCHAR  
deadline TIMESTAMP NULL  
ai_confidence DECIMAL  
created_at TIMESTAMP  
updated_at TIMESTAMP  
decisions  
id UUID PK  
meeting_id UUID FK  
decision TEXT  
context TEXT  
ai_confidence DECIMAL  
created_at TIMESTAMP  
risks  
id UUID PK  
meeting_id UUID FK  
description TEXT  
severity VARCHAR  
ai_confidence DECIMAL  
created_at TIMESTAMP  
questions  
id UUID PK  
meeting_id UUID FK  
question TEXT  
status VARCHAR  
created_at TIMESTAMP  
ai_actions  
id UUID PK  
organization_id UUID FK  
meeting_id UUID FK  
task_id UUID FK NULL  
action_type VARCHAR  
status VARCHAR  
requested_by UUID FK  
approved_by UUID FK NULL  
executed_at TIMESTAMP NULL  
error_message TEXT NULL  
created_at TIMESTAMP  
updated_at TIMESTAMP  
integrations  
id UUID PK  
organization_id UUID FK  
provider VARCHAR  
status VARCHAR  
encrypted_access_token TEXT  
encrypted_refresh_token TEXT  
expires_at TIMESTAMP  
created_at TIMESTAMP  
updated_at TIMESTAMP  
external_actions  
id UUID PK  
ai_action_id UUID FK  
provider VARCHAR  
external_id VARCHAR  
external_url TEXT  
created_at TIMESTAMP  
meeting_embeddings  
id UUID PK  
meeting_id UUID FK  
chunk TEXT  
embedding VECTOR  
created_at TIMESTAMP  
audit_logs  
id UUID PK  
organization_id UUID FK  
user_id UUID FK  
action VARCHAR  
resource_type VARCHAR  
resource_id UUID  
metadata JSONB  
created_at TIMESTAMP  


## 29. API Design
Authentication  
POST /api/v1/auth/register  
POST /api/v1/auth/login  
POST /api/v1/auth/refresh  
POST /api/v1/auth/logout  
Users  
GET /api/v1/users/me  
PATCH /api/v1/users/me  
Organizations  
GET /api/v1/organizations/current  
PATCH /api/v1/organizations/current  
GET /api/v1/organizations/members  
POST /api/v1/organizations/members/invite  
DELETE /api/v1/organizations/members/{id}  
Meetings  
POST /api/v1/meetings  
GET /api/v1/meetings  
GET /api/v1/meetings/{id}  
PATCH /api/v1/meetings/{id}  
DELETE /api/v1/meetings/{id}  

POST /api/v1/meetings/{id}/analyze  
GET /api/v1/meetings/{id}/analysis  
Tasks  
GET /api/v1/tasks  
GET /api/v1/tasks/{id}  
PATCH /api/v1/tasks/{id}  
AI Actions  
GET /api/v1/actions/pending  
POST /api/v1/actions/{id}/approve  
POST /api/v1/actions/{id}/reject  
POST /api/v1/actions/{id}/execute  
POST /api/v1/actions/{id}/retry  
Search  
GET /api/v1/search/meetings?q=  
GET /api/v1/search/tasks?q=  
GET /api/v1/search/semantic?q=  
Integrations  
GET /api/v1/integrations  
POST /api/v1/integrations/jira/connect  
GET /api/v1/integrations/jira/callback  
DELETE /api/v1/integrations/jira  

POST /api/v1/integrations/slack/connect  
GET /api/v1/integrations/slack/callback  
DELETE /api/v1/integrations/slack  


## 30. API Standards
All APIs should:  
- use /api/v1
- return JSON
- use consistent error responses
- validate input
- return appropriate HTTP status codes
- use pagination for collections
- include correlation IDs
Example error:  
{  
  "timestamp": "2026-10-03T20:30:00Z",  
  "status": 400,  
  "error": "VALIDATION_ERROR",  
  "message": "Meeting title is required",  
  "path": "/api/v1/meetings"  
}  


## 31. Backend Architecture
Use a modular Spring Boot architecture.  
Recommended:  
com.nexa  
│  
├── auth  
├── user  
├── organization  
├── meeting  
├── task  
├── decision  
├── risk  
├── ai  
├── action  
├── integration  
│   ├── jira  
│   ├── slack  
│   └── github  
├── search  
├── notification  
├── audit  
└── common  
Do NOT immediately create many independent microservices.  
The MVP should use a:  
Modular monolith  
This keeps development manageable while maintaining clean boundaries.  


## 32. AI Module
Suggested structure:  
ai/  
├── controller/  
├── service/  
├── model/  
├── prompt/  
├── parser/  
├── validator/  
├── embedding/  
└── agent/  
Responsibilities:  
AIService  
Coordinates AI processing.  
PromptService  
Stores/version prompts.  
ExtractionService  
Extracts:  
- tasks
- decisions
- risks
- questions
ValidationService  
Validates structured AI output.  
EmbeddingService  
Generates embeddings.  
AgentService  
Manages tool calling.  


## 33. AI Prompt Requirements
The system prompt must enforce:  
- Never invent people.
- Never invent deadlines.
- Return null when information is missing.
- Separate decisions from suggestions.
- Identify uncertainty.
- Return structured JSON.
- Quote or reference supporting transcript context internally.
- Assign confidence scores.
- Treat meeting date as the reference date for relative dates.


## 34. AI Structured Schema
The AI response should conform to:  
{  
  "summary": "",  
  "actionItems": [  
    {  
      "title": "",  
      "description": "",  
      "ownerName": null,  
      "deadline": null,  
      "priority": "LOW",  
      "confidence": 0.0,  
      "evidence": ""  
    }  
  ],  
  "decisions": [  
    {  
      "decision": "",  
      "context": "",  
      "confidence": 0.0,  
      "evidence": ""  
    }  
  ],  
  "risks": [  
    {  
      "description": "",  
      "severity": "LOW",  
      "confidence": 0.0,  
      "evidence": ""  
    }  
  ],  
  "unresolvedQuestions": [  
    {  
      "question": "",  
      "confidence": 0.0,  
      "evidence": ""  
    }  
  ]  
}  


## 35. Asynchronous AI Processing
AI analysis should be asynchronous.  
Flow:  
POST /meetings/{id}/analyze  
          ↓  
Create Analysis Job  
          ↓  
Return 202 Accepted  
          ↓  
Background Worker  
          ↓  
LLM  
          ↓  
Validate  
          ↓  
Persist  
          ↓  
Update Meeting Status  
Meeting statuses:  
UPLOADED  
PROCESSING  
COMPLETED  
FAILED  


## 36. Messaging
Recommended:  
Kafka  
Events:  
MeetingUploaded  
MeetingAnalysisRequested  
MeetingAnalysisCompleted  
ActionApproved  
ActionExecutionRequested  
ActionExecutionCompleted  
ActionExecutionFailed  
Example:  
MeetingAnalysisRequested  
          ↓  
AI Consumer  
          ↓  
Analysis Completed  
          ↓  
MeetingAnalysisCompleted  
If Kafka makes the initial MVP unnecessarily complex, implement a clean event interface and use Spring's asynchronous processing first, then add Kafka in Phase 2.  


## 37. Redis
Use Redis for:  
- caching
- rate limiting
- temporary job state
- integration state where appropriate
Potential cache:  
organization:{id}:members  
meeting:{id}:summary  
Do not cache sensitive data without an explicit expiration strategy.  


## 38. Security
Implement:  
Authentication  
Spring Security.  
Authorization  
RBAC + organization ownership checks.  
Passwords  
Use:  
BCrypt/Argon2  
Never store plaintext passwords.  
Integration Tokens  
Encrypt before storing.  
API Protection  
Implement:  
- rate limiting
- validation
- secure headers
- CORS configuration
- CSRF strategy appropriate to authentication architecture
Audit  
Record all sensitive actions.  


## 39. Multi-Tenant Security
Every organization-owned query must enforce:  
organization_id = authenticated_user.organization_id  
Never rely solely on frontend filtering.  
Example:  
GET /meetings/{id}  
must verify:  
meeting.organization_id == user.organization_id  


## 40. Observability
Use:  
- Spring Boot Actuator
- Micrometer
- Prometheus
- Grafana
- structured JSON logging
Track:  
HTTP request latency  
HTTP error rate  
AI latency  
AI failures  
AI token usage  
database latency  
Redis latency  
Kafka lag  
Jira API failures  
Slack API failures  
action execution failures  
Expose:  
/actuator/health  
/actuator/metrics  
Do not expose sensitive operational information publicly.  


## 41. Frontend Architecture
Use:  
Next.js + TypeScript  
Structure:  
src/  
├── app/  
├── components/  
├── features/  
│   ├── meetings/  
│   ├── tasks/  
│   ├── actions/  
│   ├── integrations/  
│   └── dashboard/  
├── lib/  
├── hooks/  
└── types/  


## 42. UI Requirements
The UI should feel like a modern B2B SaaS product.  
Design principles:  
- clean
- minimal
- professional
- information-dense
- responsive
- accessible
Avoid:  
- excessive gradients
- unnecessary animations
- AI gimmicks
- chatbot-centric UI
Nexa is a work execution platform, not a chatbot.  


## 43. Main Navigation
Nexa  

Dashboard  
Meetings  
Tasks  
Actions  
Search  
Integrations  
Team  
Settings  


## 44. Dashboard Widgets
Display:  
Meetings This Week  
Action Items  
Completed Tasks  
Overdue Tasks  
Pending AI Actions  
Recent Decisions  


## 45. Integration Page
Display:  
Integrations  

Jira  
Connected ✓  
[Configure]  

Slack  
Connected ✓  
[Configure]  

GitHub  
Not Connected  
[Connect]  
Never display access tokens.  


## 46. Error States
Every important operation requires a user-friendly failure state.  
Examples:  
AI failure  
Nexa couldn't analyze this meeting.  

Your transcript is safely stored.  

[Retry]  
Jira failure  
Jira couldn't create the issue.  

Your approved action has been preserved.  

[Retry]  
Slack failure  
Slack notification failed.  

No data was lost.  

[Retry]  


## 47. Audit Log UI
Admins can see:  
Timestamp  
User  
Action  
Resource  
Integration  
Status  
Example:  
Oct 3 8:42 PM  
John Smith  
Created Jira Issue  
PAY-102  
SUCCESS  


## 48. Testing Strategy
Unit Tests  
Test:  
- services
- AI parsers
- validators
- authorization
- deadline resolution
- owner resolution
Target:  
80%+ coverage for core business logic.  
Integration Tests  
Test:  
- PostgreSQL
- Redis
- REST APIs
- integration adapters
Use:  
Testcontainers  
Security Tests  
Test:  
- unauthorized access
- cross-organization access
- invalid JWT
- expired token
- privilege escalation
AI Evaluation  
Create a fixed dataset of meeting transcripts.  
For each transcript, compare expected:  
- tasks
- owners
- deadlines
- decisions
- risks
Track:  
Precision  
Recall  
F1  
Owner Accuracy  
Deadline Accuracy  


## 49. AI Evaluation Dataset
Create:  
/evaluation  
   meetings/  
      meeting_001.txt  
      meeting_002.txt  
      meeting_003.txt  
   expected/  
      meeting_001.json  
      meeting_002.json  
      meeting_003.json  
Include difficult cases:  
Ambiguous ownership  
"We should probably get someone from backend to fix this."  
Expected:  
owner = null  
Ambiguous deadline  
"Let's get this done soon."  
Expected:  
deadline = null  
Suggestion vs decision  
"Maybe we should use Redis."  
Should NOT automatically become:  
decision = Redis  


## 50. Performance Requirements
MVP targets:  
API  
P95 response time:  
<500ms for standard CRUD APIs excluding AI operations.  
AI  
Target:  
<10 seconds for normal meeting transcripts.  
Search  
P95:  
<1 second  
External Actions  
Jira/Slack execution should return within:  
10 seconds, excluding external service outages.  


## 51. Reliability
Approved actions must be durable.  
If Jira fails:  
Action  
↓  
FAILED  
↓  
Stored  
↓  
Retry  
Never lose the action.  
Implement:  
- idempotency keys
- retries
- exponential backoff
- dead-letter handling where applicable


## 52. Idempotency
External actions must be idempotent.  
Example:  
If the user clicks:  
Create Jira Ticket  
twice, Nexa must not create two Jira tickets.  
Use:  
idempotency_key  
Example:  
meetingId + taskId + actionType  


## 53. Rate Limiting
Protect APIs against abuse.  
Example:  
Authentication:  
10 requests/minute  

AI analysis:  
10 requests/hour/user  

Standard APIs:  
100 requests/minute/user  
Exact limits can be configurable.  


## 54. Logging
Use structured logs.  
Example:  
{  
  "timestamp": "...",  
  "level": "INFO",  
  "service": "nexa",  
  "traceId": "...",  
  "userId": "...",  
  "organizationId": "...",  
  "event": "AI_ANALYSIS_COMPLETED",  
  "meetingId": "...",  
  "durationMs": 4210  
}  
Never log:  
- passwords
- access tokens
- refresh tokens
- full sensitive transcripts unnecessarily


## 55. Deployment Architecture
Recommended:  
                   Internet  
                      │  
                      ▼  
                Load Balancer  
                      │  
                      ▼  
               Next.js Frontend  
                      │  
                      ▼  
              Spring Boot API  
                      │  
          ┌───────────┼───────────┐  
          ▼           ▼           ▼  
      PostgreSQL     Redis       Kafka  
          │  
       pgvector  

Spring Boot  
    │  
    ├── LLM API  
    ├── Jira  
    ├── Slack  
    └── GitHub  


## 56. AWS Deployment
Recommended initial deployment:  
Compute  
AWS ECS/Fargate or EC2.  
Database  
Amazon RDS PostgreSQL.  
Cache  
ElastiCache Redis.  
Storage  
Amazon S3.  
Secrets  
AWS Secrets Manager.  
Monitoring  
CloudWatch + Prometheus/Grafana.  
Networking  
VPC with private database resources.  


## 57. Infrastructure as Code
Use:  
Terraform  
Terraform should provision:  
VPC  
Subnets  
Security Groups  
RDS  
Redis  
S3  
ECS/EC2  
IAM  
Secrets Manager  
CloudWatch  


## 58. CI/CD
Use:  
GitHub Actions  
Pipeline:  
Pull Request  
     ↓  
Lint  
     ↓  
Unit Tests  
     ↓  
Integration Tests  
     ↓  
Security Scan  
     ↓  
Build Docker Image  
     ↓  
Push Image  
     ↓  
Deploy  
Branches:  
main  
develop  
feature/*  
Pull requests must pass CI before merging.  


## 59. Docker
Provide:  
Dockerfile  
docker-compose.yml  
Local development:  
Next.js  
Spring Boot  
PostgreSQL  
Redis  
Optional:  
Kafka  
should be included when asynchronous event processing is enabled.  


## 60. Environment Configuration
Use environment variables.  
Example:  
DATABASE_URL  
DATABASE_USERNAME  
DATABASE_PASSWORD  

JWT_SECRET  

LLM_API_KEY  

JIRA_CLIENT_ID  
JIRA_CLIENT_SECRET  

SLACK_CLIENT_ID  
SLACK_CLIENT_SECRET  

GITHUB_CLIENT_ID  
GITHUB_CLIENT_SECRET  

REDIS_URL  
Never commit secrets.  
Provide:  
.env.example  


## 61. Repository Structure
Recommended:  
nexa-ai/  
│  
├── backend/  
│   ├── src/  
│   ├── pom.xml  
│   └── Dockerfile  
│  
├── frontend/  
│   ├── src/  
│   ├── package.json  
│   └── Dockerfile  
│  
├── infrastructure/  
│   └── terraform/  
│  
├── docs/  
│   ├── architecture.md  
│   ├── api.md  
│   └── ai.md  
│  
├── evaluation/  
│  
├── docker-compose.yml  
├── .env.example  
├── README.md  
└── PRD.md  


## 62. GitHub README Requirements
README must contain:  
Nexa  
Where meetings become momentum.  
Features  
- AI meeting analysis
- Action-item extraction
- Decision tracking
- RAG-powered meeting search
- Jira automation
- Slack automation
- Human-in-the-loop AI
- Role-based access control
- Audit logging
- Observability
Architecture  
Include architecture diagram.  
Tech Stack  
Java  
Spring Boot  
PostgreSQL  
pgvector  
Redis  
Kafka  
Next.js  
TypeScript  
Docker  
Terraform  
AWS  
GitHub Actions  
Local Setup  
Clear commands for running the system locally.  
Screenshots  
Include:  
- dashboard
- meeting analysis
- action approval
- integrations
- task dashboard
Demo  
Provide deployed URL when available.  


## 63. Development Phases
Phase 0 — Foundation  
Build:  
- repository
- backend
- frontend
- PostgreSQL
- Docker
- CI
Deliverable:  
User can run Nexa locally.  

Phase 1 — Authentication & Organizations  
Build:  
- registration
- login
- JWT
- users
- organizations
- RBAC
Deliverable:  
Multi-tenant authentication works.  

Phase 2 — Meetings  
Build:  
- meeting CRUD
- transcript upload
- meeting dashboard
- meeting detail page
Deliverable:  
User can create and view meetings.  

Phase 3 — AI Analysis  
Build:  
- transcript preprocessing
- LLM integration
- structured extraction
- validation
- confidence scoring
Deliverable:  
Transcript → structured meeting intelligence.  

Phase 4 — Tasks & Decisions  
Build:  
- tasks
- decisions
- risks
- questions
- task dashboard
- editing
Deliverable:  
AI results become manageable work.  

Phase 5 — Human Approval  
Build:  
- action center
- approve
- reject
- edit
- action lifecycle
- audit logs
Deliverable:  
Human-controlled AI execution.  

Phase 6 — Jira  
Build:  
- OAuth
- project selection
- Jira adapter
- issue creation
- idempotency
- retries
Deliverable:  
Approved Nexa task → Jira issue.  

Phase 7 — Slack  
Build:  
- OAuth
- workspace
- channel selection
- message sending
- audit trail
Deliverable:  
Approved action → Slack notification.  

Phase 8 — RAG  
Build:  
- chunking
- embeddings
- pgvector
- semantic search
- historical meeting retrieval
Deliverable:  
User can ask questions about past meetings.  

Phase 9 — Agent  
Build:  
- tool registry
- tool execution
- permission layer
- action planning
- tool-call audit
Deliverable:  
AI can recommend controlled multi-step workflows.  

Phase 10 — Production Hardening  
Build:  
- observability
- Prometheus
- Grafana
- rate limiting
- security testing
- integration testing
- Terraform
- AWS deployment
- GitHub Actions deployment
Deliverable:  
Production-style SaaS deployment.  


## 64. Future Roadmap
Version 1.1  
- GitHub integration
- Gmail integration
- Outlook integration
- calendar follow-ups
Version 1.2  
- Zoom integration
- Google Meet integration
- Teams integration
- live transcription
Version 2.0  
AI autonomous workflows:  
Meeting  
 ↓  
AI identifies work  
 ↓  
AI plans workflow  
 ↓  
Human approves  
 ↓  
AI executes multiple tools  
 ↓  
AI verifies results  


## 65. Advanced Future Concept — Work Graph
Eventually Nexa should build a graph connecting:  
People  
 │  
 ├── Meetings  
 │      │  
 │      ├── Decisions  
 │      ├── Tasks  
 │      └── Risks  
 │  
 ├── Jira Issues  
 │  
 ├── GitHub PRs  
 │  
 └── Slack Discussions  
This allows Nexa to answer questions such as:  
"Why is this Jira ticket blocked?"  
or:  
"Which decision led to this implementation?"  
or:  
"Which tasks came from discussions about the payment migration?"  
This becomes the long-term intelligence layer of Nexa.  


## 66. Product Metrics
Track:  
Activation  
Percentage of users who process their first meeting.  
AI Accuracy  
- task precision
- task recall
- owner accuracy
- deadline accuracy
- decision accuracy
Automation  
- actions approved
- Jira issues created
- Slack messages sent
- emails drafted
Productivity  
- estimated manual time saved
- action completion rate
- overdue task rate
Reliability  
- AI failure rate
- integration failure rate
- action retry rate


## 67. MVP Success Criteria
Nexa MVP is successful if a user can complete:  
Sign Up  
   ↓  
Create Organization  
   ↓  
Upload Meeting  
   ↓  
AI Analysis  
   ↓  
Review Tasks  
   ↓  
Approve Task  
   ↓  
Create Jira Ticket  
   ↓  
Send Slack Notification  
   ↓  
View Audit Log  
with no manual database intervention.  


## 68. Example End-to-End Demo
Transcript:  
"John will implement Redis caching for the payment service by Friday. Sarah will review it. We decided to use PostgreSQL for the new service. Mike will document the deployment process."  
Nexa generates:  
Tasks  
John  
Implement Redis caching  
Due: Oct 9  
Priority: High  

Sarah  
Review Redis implementation  

Mike  
Document deployment process  
Decision  
PostgreSQL selected for the new service.  
User Approval  
☑ Create Jira tickets  
☑ Notify engineering Slack channel  
☑ Draft follow-up email  
Execution  
Jira  
├── PAY-101  
├── PAY-102  
└── PAY-103  

Slack  
└── #engineering  

Email  
└── Draft created  
Audit  
3 Jira actions  
3 Slack references  
1 email draft  

Execution:  
100% successful  


## 69. Definition of Done
A feature is complete only when it has:  
- backend implementation
- frontend implementation where applicable
- database migration
- validation
- authorization
- error handling
- unit tests
- integration tests where applicable
- logging
- documentation
- API documentation
- loading state
- empty state
- error state
Do not mark a feature complete simply because the happy path works.  


## 70. Claude Code Development Rules
Claude Code should treat this PRD as the source of truth.  
Rule 1  
Do not implement the entire system in one step.  
Build incrementally by phase.  
Rule 2  
Before modifying an existing module:  
- inspect current architecture
- understand dependencies
- preserve existing behavior
- avoid unnecessary rewrites
Rule 3  
Use production-quality Java.  
Prefer:  
- records where appropriate
- immutable DTOs
- constructor injection
- clear service boundaries
- meaningful exception types
- validation
- transactions where required
Avoid:  
- field injection
- giant service classes
- duplicated business logic
- hardcoded credentials
- hardcoded integration IDs
Rule 4  
Use DTOs between controllers and services.  
Do not expose JPA entities directly through APIs.  
Rule 5  
Use database migrations.  
Recommended:  
Flyway  
Never manually modify production database schemas.  
Rule 6  
Write tests alongside features.  
Do not postpone all testing until the end.  
Rule 7  
Never bypass authorization for convenience.  
Every organization-owned resource must be tenant-scoped.  
Rule 8  
Never allow an LLM to directly execute arbitrary HTTP requests.  
All tools must pass through controlled backend adapters.  
Rule 9  
Never store secrets in source control.  
Rule 10  
When uncertain about a product decision, follow this priority:  
Security  
↓  
Data integrity  
↓  
User control  
↓  
Correctness  
↓  
Performance  
↓  
Convenience  


## 71. First Claude Code Build Prompt
The first implementation task should NOT ask Claude Code to build all of Nexa.  
Use this initial prompt:  
Read PRD.md completely before making changes.  
We are building Nexa, an AI-powered meeting-to-work SaaS platform.  
For this first phase, implement ONLY the project foundation:  
- Create the Spring Boot backend using Java 21.
- Configure Maven.
- Configure PostgreSQL.
- Configure Flyway.
- Configure Spring Security foundation.
- Configure application profiles for local/test/prod.
- Create the base package structure described in the PRD.
- Create a global exception-handling mechanism.
- Create standard API response/error models.
- Configure validation.
- Add Actuator.
- Add structured logging.
- Add Docker support.
- Create docker-compose for the local PostgreSQL environment.
- Create initial frontend using Next.js + TypeScript.
- Add environment configuration.
- Add basic CI with GitHub Actions.
- Add unit-test configuration.
Do NOT implement AI, Jira, Slack, Kafka, RAG, or agent functionality yet.  
Before coding, inspect the repository and produce a short implementation plan.  
After implementation:  
- run tests
- run the application
- verify database connectivity
- verify health endpoint
- verify frontend starts
- report files changed
- report any remaining issues.
Follow the architecture and security principles in PRD.md.  
Do not make unrelated changes.  


## 72. Build Order
The recommended implementation order is:  
Foundation  
    ↓  
Authentication  
    ↓  
Organizations / RBAC  
    ↓  
Meetings  
    ↓  
AI Analysis  
    ↓  
Tasks / Decisions  
    ↓  
Human Approval  
    ↓  
Jira  
    ↓  
Slack  
    ↓  
RAG  
    ↓  
AI Agent  
    ↓  
Observability  
    ↓  
AWS  
    ↓  
Production Hardening  
Do not reverse this order.  


## 73. Final Product Definition
Nexa is NOT:  
❌ ChatGPT clone  
❌ Meeting summarizer  
❌ Simple CRUD application  
❌ Basic RAG chatbot  
❌ AI wrapper around an API  
Nexa IS:  
                    NEXA  

             Conversation Layer  
                    ↓  
              AI Intelligence  
                    ↓  
       ┌────────────┼────────────┐  
       ↓            ↓            ↓  
     Tasks       Decisions      Risks  
       │  
       ↓  
   Human Approval  
       │  
       ↓  
   Action Engine  
       │  
 ┌─────┼─────┬──────┐  
 ↓     ↓     ↓      ↓  
Jira Slack GitHub Email  
       │  
       ↓  
    Tracking  
       │  
       ↓  
   Work Memory  
       │  
       ↓  
       RAG  
Core product promise  
Nexa turns meetings from conversations people remember into work teams can execute.  


## 74. Resume Positioning
Once the project is fully implemented, position it as:  
Nexa — AI-Powered Meeting-to-Work Agent  
Java, Spring Boot, PostgreSQL, pgvector, Redis, Kafka, Next.js, TypeScript, Docker, AWS, Terraform  
Potential resume description:  
Engineered Nexa, a multi-tenant AI SaaS platform that converts meeting transcripts into structured tasks, decisions, risks, owners, and deadlines using LLM-based extraction and RAG, with human-in-the-loop execution across Jira and Slack.  
Second bullet:  
Designed secure Spring Boot REST APIs with RBAC, OAuth integrations, PostgreSQL, Redis, asynchronous event processing, idempotent action execution, and audit logging for reliable AI-driven workflows.  
Third bullet:  
Deployed containerized services to AWS using Terraform and GitHub Actions and implemented Prometheus/Grafana observability for API, AI, database, and integration workloads.  


## 75. North Star
The ultimate goal of Nexa is simple:  
Don't just tell me what happened in the meeting.  

Tell me what needs to happen next —  
and help me make it happen.  
Nexa — Where meetings become momentum.  

