package com.nexa.action;

import com.nexa.task.Task;
import com.nexa.user.User;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Builds the follow-up email draft (PRD section 19). Draft only: nothing is sent. */
@Component
class EmailDraftHandler implements ActionHandler {

    private static final DateTimeFormatter SHORT = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter LONG = DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH);

    @Override
    public ActionType type() {
        return ActionType.DRAFT_EMAIL;
    }

    @Override
    public ActionOutcome execute(ActionContext context) {
        StringBuilder body = new StringBuilder();
        body.append("Subject: ").append(context.meeting().getTitle()).append(" — Action Items\n\n");
        body.append("Hi team,\n\n");
        body.append("Here are the key action items from our ").append(context.meeting().getMeetingDate().format(LONG)).append(" meeting:\n\n");
        if (context.tasks().isEmpty()) {
            body.append("• No action items were recorded.\n");
        }
        for (Task task : context.tasks()) {
            body.append("• ").append(ownerLabel(task, context)).append(" — ").append(task.getTitle());
            if (task.getDeadline() != null) {
                body.append(" — ").append(task.getDeadline().format(SHORT));
            }
            body.append('\n');
        }
        if (!context.decisions().isEmpty()) {
            body.append(context.decisions().size() == 1 ? "\nDecision:\n" : "\nDecisions:\n");
            context.decisions().forEach(d -> body.append("• ").append(d.getDecision()).append('\n'));
        }
        if (context.meeting().getSummary() != null) {
            body.append("\nSummary:\n").append(context.meeting().getSummary()).append('\n');
        }
        body.append("\nThanks,\nNexa\n");
        return ActionOutcome.content(body.toString());
    }

    private static String ownerLabel(Task task, ActionContext context) {
        User owner = task.getOwnerId() == null ? null : context.users().get(task.getOwnerId());
        if (owner != null) {
            return owner.getName().split(" ")[0];
        }
        return task.getOwnerName() != null ? task.getOwnerName() : "Unassigned";
    }
}
