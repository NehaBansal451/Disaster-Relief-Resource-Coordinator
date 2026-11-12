package com.disasterrelief.frontend.views.disasters;

import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.api.DisasterClient;
import com.disasterrelief.frontend.api.DisasterDto;
import com.disasterrelief.frontend.api.DisasterForm;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.components.Formats;
import com.disasterrelief.frontend.layouts.MainLayout;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.HasValidation;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.NumberField;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteParameters;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/** Create a disaster. ADMIN and OFFICER only (others are sent back to the list; the backend enforces it too). */
@Route(value = "disasters/new", layout = MainLayout.class)
@PageTitle("DRRC - New disaster")
public class DisasterFormView extends VerticalLayout implements BeforeEnterObserver {

    private final DisasterClient client;
    private final UserSession session;

    private final TextField name = new TextField("Name");
    private final ComboBox<String> type = new ComboBox<>("Disaster type");
    private final ComboBox<String> severity = new ComboBox<>("Severity");
    private final DateTimePicker startedAt = new DateTimePicker("Started at (India time)");
    private final NumberField latitude = new NumberField("Epicentre latitude (optional)");
    private final NumberField longitude = new NumberField("Epicentre longitude (optional)");
    private final TextArea description = new TextArea("Description");
    private final Button save = new Button("Create disaster");

    public DisasterFormView(DisasterClient client, UserSession session) {
        this.client = client;
        this.session = session;
        setPadding(true);

        name.setRequired(true);
        name.setMaxLength(200);
        type.setItems(List.of("FLOOD", "EARTHQUAKE", "CYCLONE", "LANDSLIDE", "FIRE", "DROUGHT", "HEATWAVE", "OTHER"));
        type.setItemLabelGenerator(Formats::label);
        type.setRequired(true);
        severity.setItems(List.of("CRITICAL", "HIGH", "MEDIUM", "LOW"));
        severity.setItemLabelGenerator(Formats::label);
        severity.setRequired(true);
        startedAt.setValue(LocalDateTime.now(Formats.ZONE).withSecond(0).withNano(0));
        latitude.setMin(-90);
        latitude.setMax(90);
        longitude.setMin(-180);
        longitude.setMax(180);
        description.setMaxLength(5000);
        description.setWidthFull();

        FormLayout form = new FormLayout(name, type, severity, startedAt, latitude, longitude, description);
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("40rem", 2));
        form.setColspan(name, 2);
        form.setColspan(description, 2);

        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        save.addClickListener(event -> submit());
        Button cancel = new Button("Cancel", event -> UI.getCurrent().navigate(DisasterListView.class));

        add(new H2("New disaster"), form, new com.vaadin.flow.component.orderedlayout.HorizontalLayout(save, cancel));
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (session.current().isEmpty()) {
            event.forwardTo(com.disasterrelief.frontend.views.LoginView.class);
        } else if (!session.user().canManageDisasters()) {
            event.forwardTo(DisasterListView.class);
        }
    }

    private void submit() {
        clearErrors();
        if (!requiredFilled()) {
            return;
        }
        DisasterForm body = new DisasterForm(name.getValue().trim(), type.getValue(), severity.getValue(),
                Formats.toIso(startedAt.getValue()), blankToNull(description.getValue()),
                latitude.getValue(), longitude.getValue());
        save.setEnabled(false);
        try {
            DisasterDto created = client.create(session.token(), body);
            Feedback.success("Disaster created.");
            UI.getCurrent().navigate(DisasterDetailView.class, new RouteParameters("id", String.valueOf(created.id())));
        } catch (BackendException ex) {
            if (!showFieldErrors(ex)) {
                Feedback.handle(ex, session);
            }
        } finally {
            save.setEnabled(true);
        }
    }

    /** Client-side check for friendlier messages; the backend validates again and is the authority. */
    private boolean requiredFilled() {
        boolean ok = true;
        if (name.getValue() == null || name.getValue().isBlank()) {
            mark(name, "Name is required");
            ok = false;
        }
        if (type.isEmpty()) {
            mark(type, "Choose a type");
            ok = false;
        }
        if (severity.isEmpty()) {
            mark(severity, "Choose a severity");
            ok = false;
        }
        if (startedAt.isEmpty()) {
            mark(startedAt, "Start time is required");
            ok = false;
        }
        return ok;
    }

    /** Puts backend field errors next to the matching inputs. Returns false if none matched. */
    private boolean showFieldErrors(BackendException ex) {
        Map<String, HasValidation> fields = Map.of(
                "name", name, "disasterType", type, "severity", severity, "startedAt", startedAt,
                "latitude", latitude, "longitude", longitude, "description", description);
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

    private void clearErrors() {
        for (HasValidation field : List.<HasValidation>of(name, type, severity, startedAt, latitude, longitude, description)) {
            field.setInvalid(false);
        }
    }

    private static void mark(HasValidation field, String message) {
        field.setErrorMessage(message);
        field.setInvalid(true);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
