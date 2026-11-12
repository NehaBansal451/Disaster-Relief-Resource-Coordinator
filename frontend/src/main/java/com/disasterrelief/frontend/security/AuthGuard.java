package com.disasterrelief.frontend.security;

import com.disasterrelief.frontend.session.UserSession;
import com.disasterrelief.frontend.views.LoginView;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/** Every page except the login page requires a valid login; otherwise the user is sent to the login page. */
@Component
public class AuthGuard implements VaadinServiceInitListener {

    private final UserSession session;

    public AuthGuard(UserSession session) {
        this.session = session;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(uiEvent -> uiEvent.getUI().addBeforeEnterListener(this::beforeEnter));
    }

    void beforeEnter(BeforeEnterEvent event) {
        if (!LoginView.class.equals(event.getNavigationTarget()) && session.current().isEmpty()) {
            event.forwardTo(LoginView.class);
        }
    }
}
