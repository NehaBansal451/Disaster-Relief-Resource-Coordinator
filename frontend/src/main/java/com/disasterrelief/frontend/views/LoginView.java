package com.disasterrelief.frontend.views;

import com.disasterrelief.frontend.api.AuthClient;
import com.disasterrelief.frontend.api.BackendException;
import com.disasterrelief.frontend.components.Feedback;
import com.disasterrelief.frontend.session.UserSession;
import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.EmailField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;

/** Login page. The only page reachable without a token. */
@Route("login")
@PageTitle("DRRC - Log in")
public class LoginView extends VerticalLayout {

    private final AuthClient authClient;
    private final UserSession session;

    private final EmailField email = new EmailField("Email");
    private final PasswordField password = new PasswordField("Password");
    private final Paragraph error = new Paragraph();
    private final Button submit = new Button("Log in");

    public LoginView(AuthClient authClient, UserSession session) {
        this.authClient = authClient;
        this.session = session;

        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        email.setWidth("20rem");
        email.setRequiredIndicatorVisible(true);
        email.setAutofocus(true);
        password.setWidth("20rem");
        password.setRequiredIndicatorVisible(true);
        error.getStyle().set("color", "var(--lumo-error-text-color)");
        error.setVisible(false);
        submit.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        submit.setWidth("20rem");
        submit.addClickShortcut(Key.ENTER);
        submit.addClickListener(event -> attemptLogin());

        add(new H1("Disaster Relief Resource Coordinator"),
                new Paragraph("Log in with your official account."),
                email, password, error, submit);
    }

    private void attemptLogin() {
        error.setVisible(false);
        if (email.isEmpty() || password.isEmpty()) {
            show("Enter your email and password.");
            return;
        }
        submit.setEnabled(false);
        try {
            session.login(authClient.login(email.getValue().trim(), password.getValue()));
            password.clear();
            UI.getCurrent().navigate("");
        } catch (BackendException ex) {
            password.clear();
            if (ex.isUnauthenticated()) {
                show("Invalid email or password.");
            } else {
                show(Feedback.message(ex));
            }
        } finally {
            submit.setEnabled(true);
        }
    }

    private void show(String text) {
        error.setText(text);
        error.setVisible(true);
    }
}
