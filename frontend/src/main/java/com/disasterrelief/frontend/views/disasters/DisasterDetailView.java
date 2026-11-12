package com.disasterrelief.frontend.views.disasters;

import com.disasterrelief.frontend.api.AffectedAreaClient;
import com.disasterrelief.frontend.api.AffectedAreaDto;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.api.DisasterClient;
import com.disasterrelief.frontend.api.DisasterDto;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** One disaster with its affected areas. ADMIN and OFFICER can add areas (not for CLOSED disasters). */
@Route(value = "disaster/:id", layout = MainLayout.class)
@PageTitle("DRRC - Disaster")
public class DisasterDetailView extends VerticalLayout implements BeforeEnterObserver {

    private static final int AREA_PAGE_SIZE = 100;

    private final DisasterClient disasters;
    private final AffectedAreaClient areas;
    private final UserSession session;

    private final H2 title = new H2();
    private final Paragraph summary = new Paragraph();
    private final Paragraph description = new Paragraph();
    private final Grid<AffectedAreaDto> grid = new Grid<>(AffectedAreaDto.class, false);
    private final Paragraph emptyMessage = new Paragraph("No affected areas recorded yet.");
    private final Button addArea = new Button("Add affected area");
    private DisasterDto disaster;

    public DisasterDetailView(DisasterClient disasters, AffectedAreaClient areas, UserSession session) {
        this.disasters = disasters;
        this.areas = areas;
        this.session = session;
        setPadding(true);

        Button back = new Button("Back to disasters", event -> UI.getCurrent().navigate(DisasterListView.class));
        back.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        grid.addColumn(a -> a.name()).setHeader("Area").setFlexGrow(2);
        grid.addColumn(a -> Formats.label(a.areaType())).setHeader("Type");
        grid.addColumn(a -> a.district() == null ? "-" : a.district()).setHeader("District");
        grid.addColumn(a -> Formats.number(a.populationEstimate() == null ? null : a.populationEstimate().longValue()))
                .setHeader("Population");
        grid.addColumn(a -> Formats.label(a.impactLevel())).setHeader("Impact");
        grid.addColumn(a -> a.latitude() + ", " + a.longitude()).setHeader("Location (lat, lon)");
        grid.setAllRowsVisible(true);

        addArea.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addArea.addClickListener(event -> new AffectedAreaDialog(areas, session, disaster.id(), this::loadAreas).open());
        addArea.setVisible(false);

        add(back, title, summary, description, new H3("Affected areas"), addArea, emptyMessage, grid);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
            return;
        }
        long id;
        try {
            id = Long.parseLong(event.getRouteParameters().get("id").orElse(""));
        } catch (NumberFormatException ex) {
            event.forwardTo(DisasterListView.class);
            return;
        }
        try {
            disaster = disasters.get(session.token(), id);
        } catch (BackendException ex) {
            if (ex.status() == 404) {
                Feedback.error("That disaster does not exist.");
            } else {
                Feedback.handle(ex, session);
            }
            event.forwardTo(DisasterListView.class);
            return;
        }
        title.setText(disaster.demo() ? disaster.name() + " (demo)" : disaster.name());
        summary.setText(Formats.label(disaster.disasterType()) + " | Severity: " + Formats.label(disaster.severity())
                + " | Status: " + Formats.label(disaster.status()) + " | Started: " + Formats.dateTime(disaster.startedAt())
                + ("CLOSED".equals(disaster.status()) ? " | Ended: " + Formats.dateTime(disaster.endedAt()) : ""));
        description.setText(disaster.description() == null ? "" : disaster.description());
        description.setVisible(disaster.description() != null && !disaster.description().isBlank());
        addArea.setVisible(session.user().canManageDisasters() && !"CLOSED".equals(disaster.status()));
        loadAreas();
    }

    private void loadAreas() {
        try {
            var page = areas.list(session.token(), disaster.id(), 0, AREA_PAGE_SIZE);
            grid.setItems(page.content());
            emptyMessage.setVisible(page.content().isEmpty());
            grid.setVisible(!page.content().isEmpty());
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }
}
