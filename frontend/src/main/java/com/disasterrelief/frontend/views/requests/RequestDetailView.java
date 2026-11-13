package com.disasterrelief.frontend.views.requests;

import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.api.HistoryDto;
import com.disasterrelief.frontend.api.NeedRequestDto;
import com.disasterrelief.frontend.api.RequestClient;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.List;
import java.util.function.Consumer;

/** One request with its history and the actions this user may take (role-aware; the backend re-checks every action). */
@Route(value = "request/:id", layout = MainLayout.class)
@PageTitle("DRRC - Request")
public class RequestDetailView extends VerticalLayout implements BeforeEnterObserver {

    private final RequestClient client;
    private final UserSession session;
    private final VerticalLayout details = new VerticalLayout();
    private final HorizontalLayout actions = new HorizontalLayout();
    private final Grid<HistoryDto> historyGrid = new Grid<>(HistoryDto.class, false);
    private long requestId;

    public RequestDetailView(RequestClient client, UserSession session) {
        this.client = client;
        this.session = session;
        setPadding(true);
        details.setPadding(false);
        details.setSpacing(false);

        historyGrid.addColumn(h -> Formats.dateTime(h.createdAt())).setHeader("When").setFlexGrow(2);
        historyGrid.addColumn(h -> Formats.label(h.action())).setHeader("Action");
        historyGrid.addColumn(h -> h.actorName() + " (" + Formats.label(h.actorRole()) + ")").setHeader("By").setFlexGrow(2);
        historyGrid.addColumn(h -> h.reason() == null ? "" : h.reason()).setHeader("Reason").setFlexGrow(3);
        historyGrid.setAllRowsVisible(true);

        add(new H2("Request"), details, actions, new H3("History"), historyGrid,
                new Button("Back to requests", e -> UI.getCurrent().navigate(RequestListView.class)));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
            return;
        }
        try {
            requestId = Long.parseLong(event.getRouteParameters().get("id").orElse(""));
        } catch (NumberFormatException ex) {
            event.forwardTo(RequestListView.class);
            return;
        }
        load();
    }

    private void load() {
        try {
            show(client.get(session.token(), requestId));
        } catch (BackendException ex) {
            details.removeAll();
            actions.removeAll();
            historyGrid.setItems(List.of());
            details.add(new Paragraph(ex.status() == 404 ? "This request does not exist." : Feedback.message(ex)));
            Feedback.handle(ex, session);
        }
    }

    private void show(NeedRequestDto r) {
        details.removeAll();
        details.add(line("Status", Formats.label(r.status())),
                line("Need", r.resourceName() + " x " + r.quantity() + " " + r.unit()),
                line("Severity", Formats.label(r.severity())),
                line("Disaster", r.disasterName()),
                line("Affected area", r.affectedAreaName()),
                line("People affected", String.valueOf(r.affectedPopulation())),
                line("Submitted by", r.requestedByName() + ", " + Formats.dateTime(r.createdAt())),
                line("Description", r.description()));
        if (r.statusReason() != null) {
            details.add(line("Reason / note", r.statusReason()));
        }
        details.add(line("Priority", r.priorityScore() == null ? "Not calculated yet" : r.priorityScore() + " (" + r.priorityCategory() + ")"));
        historyGrid.setItems(r.history() == null ? List.of() : r.history());

        actions.removeAll();
        var allowed = RequestActions.available(session.user().role(), session.user().id(), r.status(), r.requestedBy());
        if (allowed.contains(RequestActions.Action.VERIFY)) {
            actions.add(primary("Verify", () -> run(() -> client.verify(session.token(), requestId, "VERIFY", null), "Request verified.")));
        }
        if (allowed.contains(RequestActions.Action.APPROVE)) {
            actions.add(primary("Approve", () -> run(() -> client.approve(session.token(), requestId, null), "Request approved.")));
        }
        if (allowed.contains(RequestActions.Action.REQUEST_CLARIFICATION)) {
            actions.add(new Button("Ask for clarification", e -> askReason("Ask for clarification", "What is unclear?",
                    reason -> run(() -> client.verify(session.token(), requestId, "REQUEST_CLARIFICATION", reason), "Clarification requested."))));
        }
        if (allowed.contains(RequestActions.Action.REJECT)) {
            boolean pending = "PENDING".equals(r.status());
            actions.add(new Button("Reject", e -> askReason("Reject request", "Why is it rejected?",
                    reason -> run(() -> pending
                            ? client.verify(session.token(), requestId, "REJECT", reason)
                            : client.reject(session.token(), requestId, reason), "Request rejected."))));
        }
        if (allowed.contains(RequestActions.Action.CANCEL)) {
            actions.add(new Button("Cancel request", e -> askReason("Cancel request", "Why is it cancelled?",
                    reason -> run(() -> client.cancel(session.token(), requestId, reason), "Request cancelled."))));
        }
    }

    private static Paragraph line(String label, String value) {
        Paragraph p = new Paragraph(label + ": " + value);
        p.getStyle().set("margin", "0.2em 0");
        return p;
    }

    private static Button primary(String text, Runnable action) {
        Button b = new Button(text, e -> action.run());
        b.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        return b;
    }

    private void run(java.util.function.Supplier<NeedRequestDto> call, String success) {
        try {
            show(call.get());
            Feedback.success(success);
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            load();
        }
    }

    /** Small dialog asking for a mandatory reason before a rejecting or cancelling action. */
    private void askReason(String title, String label, Consumer<String> onConfirm) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(title);
        TextArea reason = new TextArea(label);
        reason.setMaxLength(1000);
        reason.setWidth("24rem");
        Button confirm = new Button("Confirm", e -> {
            if (reason.getValue() == null || reason.getValue().isBlank()) {
                reason.setErrorMessage("A reason is required");
                reason.setInvalid(true);
                return;
            }
            dialog.close();
            onConfirm.accept(reason.getValue().trim());
        });
        confirm.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        dialog.add(reason);
        dialog.getFooter().add(new Button("Back", e -> dialog.close()), confirm);
        dialog.open();
    }
}
