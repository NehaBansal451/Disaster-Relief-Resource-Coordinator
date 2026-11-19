package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.Api;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.AppView;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.components.J;
import com.disasterrelief.frontend.components.MapGrid;
import com.disasterrelief.frontend.components.Pick;
import com.disasterrelief.frontend.components.Ui;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.security.Roles;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Report analyzer. Paste a field report; the model suggests a severity and needs. The result is advice only: nothing
 * is created or changed until a person acts on it. Do not paste names or phone numbers (they are removed anyway).
 */
@Route(value = "analyzer", layout = MainLayout.class)
@PageTitle("DRRC - Report analyzer")
public class AnalyzerView extends AppView {

    private final TextArea text = new TextArea("Field report");
    private final Select<String> review = Pick.choice("Review", "All", "UNREVIEWED", "ACCEPTED", "REJECTED");
    private final Div latest = new Div();
    private final MapGrid grid;

    public AnalyzerView(UserSession session, Api api) {
        super(session, api, "Report analyzer",
                "Suggests severity and needs from a written report. Advice only: a person decides what to do.");
        text.setWidthFull();
        text.setMinHeight("9rem");
        text.setMaxLength(5000);
        text.setHelperText("Up to 5000 characters. Personal details such as names and phone numbers are removed before analysis.");
        grid = new MapGrid(session)
                .col("#", a -> String.valueOf(J.lng(a, "id")))
                .badge("Analysis", "status")
                .badge("Severity", "severity")
                .col("Summary", a -> J.str(J.map(a, "result"), "summary"))
                .badge("Review", "reviewStatus")
                .when("When", "createdAt")
                .onRowClick(this::open)
                .loader(page -> api.page(token(), "/api/ai/analyses", page, 10, "reviewStatus", Pick.value(review)));
        review.addValueChangeListener(e -> grid.reload(0));
        latest.addClassName("stack");
        add(Ui.card("Analyse a report", text, Ui.primary("Analyse", this::analyse), latest),
                Ui.card("Earlier analyses (click one to review)", Ui.row(review), grid));
    }

    @Override
    protected boolean allowed(String role) {
        return Roles.in(role, Roles.STAFF);
    }

    @Override
    protected void enter(BeforeEnterEvent event) {
        grid.reload(0);
    }

    private void analyse() {
        if (text.getValue().isBlank()) {
            Feedback.error("Paste a report first.");
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("text", text.getValue());
        try {
            Map<String, Object> result = api.post(token(), "/api/ai/analyses", payload);
            latest.removeAll();
            latest.add(body(result));
            grid.reload(0);
        } catch (BackendException ex) {
            // 503 means no API key is configured on the server; the message says so plainly.
            Feedback.handle(ex, session);
        }
    }

    private Div body(Map<String, Object> analysis) {
        Map<String, Object> result = J.map(analysis, "result");
        Div box = new Div();
        box.add(new Paragraph(J.str(analysis, "advisory").isEmpty()
                ? "Advice only. A person must check this before acting." : J.str(analysis, "advisory")));
        box.add(new Div(Ui.status(J.str(result, "severity")), new Span("  " + J.str(result, "summary"))));
        box.add(new MapGrid(session)
                .text("Resource", "resource")
                .col("Quantity", n -> n.get("quantity") == null ? "-" : Formats.number(J.lng(n, "quantity")))
                .badge("Urgency", "urgency")
                .rows(J.list(result, "needs")));
        for (String u : J.strings(result, "uncertainties")) {
            box.add(new Paragraph("Not sure about: " + u));
        }
        return box;
    }

    private void open(Map<String, Object> analysis) {
        long id = J.lng(analysis, "id");
        Div content = new Div(Ui.line("Original text (scrubbed)", J.str(analysis, "text")), body(analysis));
        Dialog dialog = Ui.info("Analysis #" + id, content);
        dialog.setWidth("48rem");
        boolean canReview = Roles.in(role(), Roles.DECIDERS);
        if (canReview && "UNREVIEWED".equals(J.str(analysis, "reviewStatus"))) {
            Button accept = Ui.primary("Mark useful", () -> decide(id, "ACCEPTED", dialog));
            Button reject = Ui.small("Mark not useful", () -> decide(id, "REJECTED", dialog));
            dialog.getFooter().add(reject, accept);
        }
    }

    private void decide(long id, String decision, Dialog dialog) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("decision", decision);
        if (run(() -> api.patch(token(), "/api/ai/analyses/" + id + "/review", payload), "Review saved")) {
            dialog.close();
            grid.reload();
        }
    }
}
