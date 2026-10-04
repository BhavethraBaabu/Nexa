package com.nexa.action;

import com.nexa.decision.Decision;
import com.nexa.meeting.Meeting;
import com.nexa.task.Task;
import com.nexa.user.User;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything a handler needs, loaded fresh at execution time so edits made before approval are
 * what gets sent (PRD section 15).
 *
 * @param task  the action's task, or null for meeting-level actions
 * @param owner the task owner, if resolved to a member
 * @param tasks all non-cancelled tasks of the meeting (for summaries such as the follow-up email)
 * @param users members referenced by {@code tasks}, by ID
 */
public record ActionContext(AiAction action, Meeting meeting, Task task, User owner, List<Task> tasks,
                            List<Decision> decisions, Map<UUID, User> users) {
}
