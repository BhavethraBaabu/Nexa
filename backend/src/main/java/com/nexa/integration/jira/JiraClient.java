package com.nexa.integration.jira;

import com.nexa.integration.IntegrationException;
import com.nexa.integration.IntegrationProperties;
import com.nexa.integration.IntegrationProvider;
import com.nexa.integration.ProviderHttp;
import com.nexa.integration.TokenRefresher;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Jira Cloud via OAuth 2.0 (3LO) and REST API v3 (PRD section 17). All requests go through
 * api.atlassian.com/ex/jira/{cloudId}.
 */
@Component
public class JiraClient implements TokenRefresher {

    static final String SCOPES = "read:jira-work write:jira-work read:jira-user offline_access";
    private static final String NAME = "Jira";

    private final IntegrationProperties properties;
    private final RestClient auth;
    private final RestClient api;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public JiraClient(IntegrationProperties properties, RestClient.Builder builder, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.auth = ProviderHttp.client(builder, properties.jira().authBaseUrl());
        this.api = ProviderHttp.client(builder, properties.jira().apiBaseUrl());
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public record Site(String cloudId, String name, String url) {
    }

    public record Project(String key, String name) {
    }

    public record IssueType(String id, String name) {
    }

    public record CreatedIssue(String key, String url) {
    }

    public record IssueRequest(String projectKey, String issueTypeId, String summary, List<String> descriptionParagraphs,
                               String priorityName, String dueDate, String assigneeAccountId, List<String> labels) {
    }

    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.jira().authBaseUrl()).path("/authorize")
                .queryParam("audience", "api.atlassian.com")
                .queryParam("client_id", properties.jira().clientId())
                .queryParam("scope", SCOPES)
                .queryParam("redirect_uri", properties.redirectUri(IntegrationProvider.JIRA))
                .queryParam("state", state)
                .queryParam("response_type", "code")
                .queryParam("prompt", "consent")
                .encode().build().toUriString();
    }

    public Tokens exchangeCode(String code) {
        return token(Map.of("grant_type", "authorization_code", "client_id", properties.jira().clientId(),
                "client_secret", properties.jira().clientSecret(), "code", code,
                "redirect_uri", properties.redirectUri(IntegrationProvider.JIRA)));
    }

    @Override
    public IntegrationProvider provider() {
        return IntegrationProvider.JIRA;
    }

    /** Atlassian rotates refresh tokens: the response carries a new one that replaces the old. */
    @Override
    public Tokens refresh(String refreshToken) {
        try {
            return token(Map.of("grant_type", "refresh_token", "client_id", properties.jira().clientId(),
                    "client_secret", properties.jira().clientSecret(), "refresh_token", refreshToken));
        } catch (IntegrationException e) {
            if (e.code() == IntegrationException.Code.REJECTED) {
                throw new IntegrationException(IntegrationException.Code.AUTH_FAILED,
                        "Jira access has expired. An admin needs to reconnect Jira in Integrations.", e);
            }
            throw e;
        }
    }

    /** The first Jira site the user granted access to. */
    public Site primarySite(String accessToken) {
        JsonNode sites = ProviderHttp.call(NAME, () -> api.get().uri("/oauth/token/accessible-resources")
                .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class));
        if (sites == null || sites.isEmpty()) {
            throw new IntegrationException(IntegrationException.Code.REJECTED, "This Atlassian account has no Jira site Nexa can access.");
        }
        JsonNode site = sites.get(0);
        return new Site(site.get("id").asString(), site.path("name").asString(null), site.path("url").asString(null));
    }

    public List<Project> projects(String cloudId, String accessToken) {
        JsonNode page = ProviderHttp.call(NAME, () -> api.get()
                .uri("/ex/jira/{cloudId}/rest/api/3/project/search?maxResults=100&orderBy=name", cloudId)
                .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class));
        List<Project> projects = new ArrayList<>();
        page.path("values").forEach(p -> projects.add(new Project(p.get("key").asString(), p.get("name").asString())));
        return projects;
    }

    public List<IssueType> issueTypes(String cloudId, String accessToken, String projectKey) {
        JsonNode page = ProviderHttp.call(NAME, () -> api.get()
                .uri("/ex/jira/{cloudId}/rest/api/3/issue/createmeta/{project}/issuetypes", cloudId, projectKey)
                .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class));
        List<IssueType> types = new ArrayList<>();
        JsonNode values = page.has("issueTypes") ? page.get("issueTypes") : page.path("values");
        values.forEach(t -> {
            if (!t.path("subtask").asBoolean(false)) {
                types.add(new IssueType(t.get("id").asString(), t.get("name").asString()));
            }
        });
        return types;
    }

    /**
     * Looks for an issue Nexa already created for this action (by its unique label). Makes
     * creation idempotent even if a previous attempt created the issue but crashed before
     * recording it (PRD section 52).
     */
    public Optional<CreatedIssue> findByLabel(String cloudId, String siteUrl, String accessToken, String label) {
        JsonNode result = ProviderHttp.call(NAME, () -> api.post()
                .uri("/ex/jira/{cloudId}/rest/api/3/search/jql", cloudId)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jql", "labels = \"" + label + "\"", "fields", List.of("key"), "maxResults", 1))
                .retrieve().body(JsonNode.class));
        JsonNode issues = result == null ? null : result.path("issues");
        if (issues == null || issues.isEmpty()) {
            return Optional.empty();
        }
        String key = issues.get(0).get("key").asString();
        return Optional.of(new CreatedIssue(key, browseUrl(siteUrl, key)));
    }

    /** Best-effort lookup of an assignee by email; Jira may hide emails, in which case the issue is unassigned. */
    public Optional<String> findAccountIdByEmail(String cloudId, String accessToken, String email) {
        try {
            JsonNode users = ProviderHttp.call(NAME, () -> api.get()
                    .uri(b -> b.path("/ex/jira/{cloudId}/rest/api/3/user/search").queryParam("query", email).build(cloudId))
                    .header("Authorization", "Bearer " + accessToken).retrieve().body(JsonNode.class));
            List<String> exact = new ArrayList<>();
            if (users != null) {
                users.forEach(u -> {
                    if (email.equalsIgnoreCase(u.path("emailAddress").asString("")) && "atlassian".equals(u.path("accountType").asString("atlassian"))) {
                        exact.add(u.get("accountId").asString());
                    }
                });
            }
            return exact.size() == 1 ? Optional.of(exact.getFirst()) : Optional.empty();
        } catch (IntegrationException e) {
            if (!e.retryable() && e.code() != IntegrationException.Code.AUTH_FAILED) {
                return Optional.empty();
            }
            throw e;
        }
    }

    /**
     * Creates the issue. If Jira rejects optional fields (priority, due date or assignee are not
     * on every project's screen), retries once without them rather than failing the action.
     */
    public CreatedIssue createIssue(String cloudId, String siteUrl, String accessToken, IssueRequest request) {
        try {
            return create(cloudId, siteUrl, accessToken, fields(request, true));
        } catch (OptionalFieldRejected e) {
            return create(cloudId, siteUrl, accessToken, fields(request, false));
        }
    }

    private CreatedIssue create(String cloudId, String siteUrl, String accessToken, Map<String, Object> fields) {
        JsonNode created;
        try {
            created = api.post().uri("/ex/jira/{cloudId}/rest/api/3/issue", cloudId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("fields", fields))
                    .retrieve().body(JsonNode.class);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 400 && rejectsOptionalField(e.getResponseBodyAsString()) && fields.containsKey("priority")) {
                throw new OptionalFieldRejected();
            }
            if (e.getStatusCode().value() == 400) {
                throw new IntegrationException(IntegrationException.Code.REJECTED,
                        "Jira rejected the issue" + rejectedFields(e.getResponseBodyAsString()) + ". Check the project and issue type in Integrations.", e);
            }
            return ProviderHttp.call(NAME, () -> {
                throw e;
            });
        } catch (RuntimeException e) {
            return ProviderHttp.call(NAME, () -> {
                throw e;
            });
        }
        String key = created.get("key").asString();
        return new CreatedIssue(key, browseUrl(siteUrl, key));
    }

    private Map<String, Object> fields(IssueRequest r, boolean includeOptional) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("project", Map.of("key", r.projectKey()));
        fields.put("issuetype", Map.of("id", r.issueTypeId()));
        fields.put("summary", r.summary().length() > 255 ? r.summary().substring(0, 255) : r.summary());
        fields.put("description", adf(r.descriptionParagraphs()));
        fields.put("labels", r.labels());
        if (includeOptional) {
            if (r.priorityName() != null) {
                fields.put("priority", Map.of("name", r.priorityName()));
            }
            if (r.dueDate() != null) {
                fields.put("duedate", r.dueDate());
            }
            if (r.assigneeAccountId() != null) {
                fields.put("assignee", Map.of("accountId", r.assigneeAccountId()));
            }
        }
        return fields;
    }

    /** Atlassian Document Format: one paragraph per line of text. */
    private static Map<String, Object> adf(List<String> paragraphs) {
        List<Object> content = new ArrayList<>();
        for (String p : paragraphs) {
            content.add(Map.of("type", "paragraph", "content", List.of(Map.of("type", "text", "text", p))));
        }
        Map<String, Object> doc = new HashMap<>();
        doc.put("type", "doc");
        doc.put("version", 1);
        doc.put("content", content);
        return doc;
    }

    private boolean rejectsOptionalField(String body) {
        try {
            JsonNode errors = objectMapper.readTree(body).path("errors");
            return errors.has("priority") || errors.has("duedate") || errors.has("assignee");
        } catch (RuntimeException e) {
            return false;
        }
    }

    private String rejectedFields(String body) {
        try {
            List<String> names = new ArrayList<>();
            objectMapper.readTree(body).path("errors").propertyNames().forEach(names::add);
            return names.isEmpty() ? "" : " (fields: " + String.join(", ", names) + ")";
        } catch (RuntimeException e) {
            return "";
        }
    }

    private Tokens token(Map<String, String> form) {
        JsonNode response = ProviderHttp.call(NAME, () -> auth.post().uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON).body(form).retrieve().body(JsonNode.class));
        long expiresIn = response.path("expires_in").asLong(3600);
        return new Tokens(response.get("access_token").asString(), response.path("refresh_token").asString(null),
                clock.instant().plusSeconds(expiresIn));
    }

    private static String browseUrl(String siteUrl, String key) {
        return siteUrl == null ? null : siteUrl.replaceAll("/+$", "") + "/browse/" + key;
    }

    private static final class OptionalFieldRejected extends RuntimeException {
    }
}
