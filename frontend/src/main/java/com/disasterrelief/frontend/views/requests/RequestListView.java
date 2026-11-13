package com.disasterrelief.frontend.views.requests;

import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.api.NeedRequestDto;
import com.disasterrelief.frontend.api.PageDto;
import com.disasterrelief.frontend.api.RequestClient;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import java.util.List;

/** Paged list of need requests, filterable by status and severity. Click a row to open it. */
@Route(value = "requests", layout = MainLayout.class)
@PageTitle("DRRC - Requests")
public class RequestListView extends VerticalLayout implements BeforeEnterObserver {

    private static final int PAGE_SIZE = 20;

    private final RequestClient client;
    private final UserSession session;
    private final Grid<NeedRequestDto> grid = new Grid<>(NeedRequestDto.class, false);
    private final ComboBox<String> status = new ComboBox<>("Status");
    private final ComboBox<String> severity = new ComboBox<>("Severity");
    private final Paragraph message = new Paragraph();
    private final Span pageLabel = new Span();
    private final Button previous = new Button("Previous");
    private final Button next = new Button("Next");
    private int page = 0;
    private int totalPages = 0;

    public RequestListView(RequestClient client, UserSession session) {
        this.client = client;
        this.session = session;
        setPadding(true);
        setSizeFull();

        status.setItems(List.of("PENDING", "VERIFIED", "APPROVED", "REJECTED", "CANCELLED"));
        status.setItemLabelGenerator(Formats::label);
        status.setClearButtonVisible(true);
        status.setPlaceholder("All");
        severity.setItems(List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"));
        severity.setItemLabelGenerator(Formats::label);
        severity.setClearButtonVisible(true);
        severity.setPlaceholder("All");
        status.addValueChangeListener(event -> reload(0));
        severity.addValueChangeListener(event -> reload(0));

        HorizontalLayout toolbar = new HorizontalLayout(status, severity);
        toolbar.setDefaultVerticalComponentAlignment(Alignment.END);
        if (session.current().map(login -> RequestActions.canSubmit(login.user().role())).orElse(false)) {
            Button create = new Button("New request", event -> UI.getCurrent().navigate(RequestFormView.class));
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            toolbar.add(create);
        }

        grid.addColumn(NeedRequestDto::id).setHeader("#").setFlexGrow(0);
        grid.addColumn(r -> r.resourceName() + " x " + Formats.number(r.quantity().longValue()) + " " + r.unit())
                .setHeader("Need").setFlexGrow(3);
        grid.addColumn(NeedRequestDto::affectedAreaName).setHeader("Area").setFlexGrow(2);
        grid.addColumn(r -> Formats.label(r.severity())).setHeader("Severity");
        grid.addColumn(r -> Formats.label(r.status())).setHeader("Status");
        grid.addColumn(r -> Formats.dateTime(r.createdAt())).setHeader("Submitted").setFlexGrow(2);
        grid.setSizeFull();
        grid.addItemClickListener(event -> UI.getCurrent().navigate(RequestDetailView.class,
                new RouteParameters("id", String.valueOf(event.getItem().id()))));

        previous.addClickListener(event -> reload(page - 1));
        next.addClickListener(event -> reload(page + 1));
        HorizontalLayout pager = new HorizontalLayout(previous, pageLabel, next);
        pager.setDefaultVerticalComponentAlignment(Alignment.CENTER);

        add(new H2("Requests"), toolbar, message, grid, pager);
    }

    /** Loaded here, not in the constructor: Vaadin builds the page before the login guard runs. */
    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
            return;
        }
        reload(0);
    }

    private void reload(int targetPage) {
        try {
            PageDto<NeedRequestDto> result = client.list(session.token(), status.getValue(), severity.getValue(), null,
                    Math.max(targetPage, 0), PAGE_SIZE);
            page = result.page();
            totalPages = result.totalPages();
            grid.setItems(result.content());
            message.setText(result.totalElements() == 0 ? "No requests match these filters." : "");
            message.setVisible(result.totalElements() == 0);
            pageLabel.setText("Page " + (totalPages == 0 ? 0 : page + 1) + " of " + totalPages
                    + "  (" + result.totalElements() + " total)");
            previous.setEnabled(page > 0);
            next.setEnabled(page + 1 < totalPages);
        } catch (BackendException ex) {
            grid.setItems(List.of());
            message.setText(Feedback.message(ex));
            message.setVisible(true);
            Feedback.handle(ex, session);
        }
    }
}
