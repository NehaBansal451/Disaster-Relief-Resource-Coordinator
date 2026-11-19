package com.disasterrelief.frontend.components;

import com.vaadin.flow.component.UI;

/** Loads the shared stylesheet (drrc.css) and the saved light/dark choice when a page opens. */
public final class Assets {

    private static final String INSTALL = """
            if (!document.getElementById('drrc-css')) {
              const l = document.createElement('link');
              l.id = 'drrc-css'; l.rel = 'stylesheet'; l.href = 'drrc.css';
              document.head.appendChild(l);
            }
            try {
              const t = window.localStorage.getItem('drrc-theme');
              if (t === 'dark') { document.documentElement.setAttribute('theme', 'dark'); }
            } catch (e) { }
            """;

    private static final String TOGGLE = """
            const dark = document.documentElement.getAttribute('theme') === 'dark';
            if (dark) { document.documentElement.removeAttribute('theme'); } else { document.documentElement.setAttribute('theme', 'dark'); }
            try { window.localStorage.setItem('drrc-theme', dark ? 'light' : 'dark'); } catch (e) { }
            """;

    private Assets() {
    }

    public static void install(UI ui) {
        ui.getPage().executeJs(INSTALL);
    }

    public static void toggleTheme(UI ui) {
        ui.getPage().executeJs(TOGGLE);
    }
}
