package com.disasterrelief.frontend.views.requests;

import com.disasterrelief.frontend.api.AffectedAreaClient;
import com.disasterrelief.frontend.api.AffectedAreaDto;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.api.DisasterClient;
import com.disasterrelief.frontend.api.DisasterDto;
import com.disasterrelief.frontend.api.NeedRequestDto;
import com.disasterrelief.frontend.api.NeedRequestForm;
import com.disasterrelief.frontend.api.RequestClient;
import com.disasterrelief.frontend.api.ResourceClient;
import com.disasterrelief.frontend.api.ResourceDto;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.disasterrelief.frontend.views.disasters.DisasterListView;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import java.util.List;
import java.util.Map;

/** Submit a need request. OFFICER, FIELD_WORKER and NGO only (the backend enforces it too). */
@Route(value = "requests/new", layout = MainLayout.class)
@PageTitle("DRRC - New request")
public class RequestFormView extends VerticalLayout implements BeforeEnterObserver {

    private final RequestClient client;
    private final DisasterClient disasters;
    private final AffectedAreaClient areas;
    private final ResourceClient resources;
    private final UserSession session;

    private final ComboBox<DisasterDto> disaster = new ComboBox<>("Disaster");
    private final ComboBox<AffectedAreaDto> area = new ComboBox<>("Affected area");
    private final ComboBox<ResourceDto> resource = new ComboBox<>("Resource needed");
    private final IntegerField quantity = new IntegerField("Quantity");
    private final IntegerField population = new IntegerField("People affected (optional)");
    private final ComboBox<String> severity = new ComboBox<>("Severity");
    private final TextArea description = new TextArea("What is needed and why");
    private final Button save = new Button("Submit request");

    public RequestFormView(RequestClient client, DisasterClient disasters, AffectedAreaClient areas,
                           ResourceClient resources, UserSession session) {
        this.client = client;
        this.disasters = disasters;
        this.areas = areas;
        this.resources = resources;
        this.session = session;
        setPadding(true);

        disaster.setItemLabelGenerator(DisasterDto::name);
        area.setItemLabelGenerator(AffectedAreaDto::name);
        area.setEnabled(false);
        resource.setItemLabelGenerator(r -> r.name() + " (" + r.unit() + ")");
        disaster.addValueChangeListener(event -> loadAreas(event.getValue()));
        quantity.setMin(1);
        quantity.setStepButtonsVisible(true);
        population.setMin(0);
        severity.setItems(List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"));
        severity.setItemLabelGenerator(Formats::label);
        description.setMaxLength(2000);
        description.setWidthFull();

        FormLayout form = new FormLayout(disaster, area, resource, quantity, population, severity, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("40rem", 2));
        form.setColspan(description, 2);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> submit());
        Button cancel = new Button("Cancel", event -> UI.getCurrent().navigate(RequestListView.class));
        add(new H2("New request"), form, new HorizontalLayout(save, cancel));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
            return;
        }
        if (!RequestActions.canSubmit(session.user().role())) {
            event.forwardTo(RequestListView.class);
            return;
        }
        try {
            disaster.setItems(disasters.list(session.token(), "ACTIVE", null, 0, 100).content());
            resource.setItems(resources.list(session.token()));
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
            event.forwardTo(DisasterListView.class);
        }
    }

    private void loadAreas(DisasterDto selected) {
        area.clear();
        area.setItems(List.of());
        area.setEnabled(selected != null);
        if (selected == null) {
            return;
        }
        try {
            area.setItems(areas.list(session.token(), selected.id(), 0, 100).content());
        } catch (BackendException ex) {
            Feedback.handle(ex, session);
        }
    }

    private void submit() {
        List<HasValidation> all = List.of(disaster, area, resource, quantity, population, severity, description);
        all.forEach(f -> f.setInvalid(false));
        boolean ok = true;
        ok &= require(disaster.isEmpty(), disaster, "Choose a disaster");
        ok &= require(area.isEmpty(), area, "Choose an affected area");
        ok &= require(resource.isEmpty(), resource, "Choose a resource");
        ok &= require(quantity.isEmpty() || quantity.getValue() < 1, quantity, "Enter a quantity of 1 or more");
        ok &= require(severity.isEmpty(), severity, "Choose a severity");
        ok &= require(description.getValue() == null || description.getValue().isBlank(), description, "Describe the need");
        if (!ok) {
            return;
        }
        NeedRequestForm body = new NeedRequestForm(disaster.getValue().id(), area.getValue().id(),
                resource.getValue().id(), quantity.getValue(), population.isEmpty() ? 0 : population.getValue(),
                severity.getValue(), description.getValue().trim());
        save.setEnabled(false);
        try {
            NeedRequestDto created = client.submit(session.token(), body);
            Feedback.success("Request submitted. It is now pending verification.");
            UI.getCurrent().navigate(RequestDetailView.class, new RouteParameters("id", String.valueOf(created.id())));
        } catch (BackendException ex) {
            if (!showFieldErrors(ex)) {
                Feedback.handle(ex, session);
            }
        } finally {
            save.setEnabled(true);
        }
    }

    private boolean showFieldErrors(BackendException ex) {
        Map<String, HasValidation> fields = Map.of("disasterId", disaster, "affectedAreaId", area,
                "resourceId", resource, "quantity", quantity, "affectedPopulation", population,
                "severity", severity, "description", description);
        boolean shown = false;
        for (Map.Entry<String, String> error : ex.fieldErrors().entrySet()) {
            HasValidation field = fields.get(error.getKey());
            if (field != null) {
                mark(field, error.getValue());
                shown = true;
            }
        }
        if (!shown && ex.status() == 400) {
            Feedback.error(ex.getMessage());
            return true;
        }
        return shown;
    }

    private static boolean require(boolean missing, HasValidation field, String message) {
        if (missing) {
            mark(field, message);
        }
        return !missing;
    }

    private static void mark(HasValidation field, String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }
}
