package com.nexa.integration.slack;

import com.nexa.integration.IntegrationException;
import com.nexa.integration.IntegrationProperties;
import com.nexa.integration.IntegrationProvider;
import com.nexa.integration.ProviderHttp;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Slack via OAuth v2 and the Web API (PRD section 18). Slack reports most errors as HTTP 200
 * with {@code "ok": false}, so every response is checked.
 */
@Component
public class SlackClient {

    /** chat:write.public lets Nexa post to public channels without being invited first. */
    static final String SCOPES = "chat:write,chat:write.public,channels:read";
    private static final String NAME = "Slack";
    private static final Set<String> AUTH_ERRORS = Set.of("invalid_auth", "token_revoked", "token_expired", "account_inactive", "not_authed");

    private final IntegrationProperties properties;
    private final RestClient client;

    public SlackClient(IntegrationProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.client = ProviderHttp.client(builder, properties.slack().baseUrl());
    }

    public record Installation(String accessToken, String teamId, String teamName) {
    }

    public record Channel(String id, String name) {
    }

    public record PostedMessage(String channel, String ts, String permalink) {
    }

    public String authorizeUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.slack().baseUrl()).path("/oauth/v2/authorize")
                .queryParam("client_id", properties.slack().clientId())
                .queryParam("scope", SCOPES)
                .queryParam("redirect_uri", properties.redirectUri(IntegrationProvider.SLACK))
                .queryParam("state", state)
                .encode().build().toUriString();
    }

    public Installation exchangeCode(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.slack().clientId());
        form.add("client_secret", properties.slack().clientSecret());
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri(IntegrationProvider.SLACK));
        JsonNode response = checked(ProviderHttp.call(NAME, () -> client.post().uri("/api/oauth.v2.access")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(JsonNode.class)));
        return new Installation(response.get("access_token").asString(), response.path("team").path("id").asString(),
                response.path("team").path("name").asString(null));
    }

    public List<Channel> channels(String token) {
        List<Channel> channels = new ArrayList<>();
        String cursor = "";
        for (int page = 0; page < 10; page++) {
            String pageCursor = cursor;
            JsonNode response = checked(ProviderHttp.call(NAME, () -> client.get()
                    .uri(b -> b.path("/api/conversations.list").queryParam("types", "public_channel")
                            .queryParam("exclude_archived", "true").queryParam("limit", "200")
                            .queryParam("cursor", pageCursor).build())
                    .header("Authorization", "Bearer " + token).retrieve().body(JsonNode.class)));
            response.path("channels").forEach(c -> channels.add(new Channel(c.get("id").asString(), c.get("name").asString())));
            cursor = response.path("response_metadata").path("next_cursor").asString("");
            if (cursor.isEmpty()) {
                break;
            }
        }
        channels.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return channels;
    }

    public PostedMessage postMessage(String token, String channel, String fallbackText, List<Map<String, Object>> blocks) {
        JsonNode response = checked(ProviderHttp.call(NAME, () -> client.post().uri("/api/chat.postMessage")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("channel", channel, "text", fallbackText, "blocks", blocks, "unfurl_links", false))
                .retrieve().body(JsonNode.class)));
        String postedChannel = response.path("channel").asString(channel);
        String ts = response.get("ts").asString();
        return new PostedMessage(postedChannel, ts, permalink(token, postedChannel, ts).orElse(null));
    }

    private Optional<String> permalink(String token, String channel, String ts) {
        try {
            JsonNode response = ProviderHttp.call(NAME, () -> client.get()
                    .uri(b -> b.path("/api/chat.getPermalink").queryParam("channel", channel).queryParam("message_ts", ts).build())
                    .header("Authorization", "Bearer " + token).retrieve().body(JsonNode.class));
            return response != null && response.path("ok").asBoolean() ? Optional.of(response.get("permalink").asString()) : Optional.empty();
        } catch (IntegrationException e) {
            return Optional.empty(); // The message was posted; a missing link isn't worth failing over.
        }
    }

    private static JsonNode checked(JsonNode response) {
        if (response != null && response.path("ok").asBoolean(false)) {
            return response;
        }
        String error = response == null ? "empty_response" : response.path("error").asString("unknown_error");
        if (AUTH_ERRORS.contains(error)) {
            throw new IntegrationException(IntegrationException.Code.AUTH_FAILED,
                    "Slack access was revoked. An admin needs to reconnect Slack in Integrations.");
        }
        if ("ratelimited".equals(error)) {
            throw new IntegrationException(IntegrationException.Code.RATE_LIMITED, "Slack is rate limiting requests. Nexa will retry.");
        }
        String message = switch (error) {
            case "channel_not_found" -> "The configured Slack channel no longer exists. Pick another in Integrations.";
            case "not_in_channel", "restricted_action" -> "Nexa isn't allowed to post in the configured Slack channel. Invite the Nexa app or pick a public channel.";
            case "is_archived" -> "The configured Slack channel is archived. Pick another in Integrations.";
            case "invalid_code", "bad_redirect_uri", "code_already_used" -> "The Slack connection couldn't be completed. Please try connecting again.";
            default -> "Slack rejected the request (" + error + ").";
        };
        throw new IntegrationException(IntegrationException.Code.REJECTED, message);
    }
}
