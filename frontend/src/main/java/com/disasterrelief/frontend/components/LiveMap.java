package com.disasterrelief.frontend.components;

import java.util.Map;

import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;

/**
 * Interactive map (Leaflet, OpenStreetMap tiles, optional satellite layer). Data is the backend's GET /api/map
 * response. The library loads from a public CDN when the page opens, so the browser needs internet access.
 * Names and details are inserted as text, never as HTML.
 */
public class LiveMap extends Div {

    private static final String SCRIPT = """
            const el = this;
            const data = JSON.parse($0);
            function load(done) {
              if (window.L) { done(); return; }
              if (!document.getElementById('leaflet-css')) {
                const l = document.createElement('link');
                l.id = 'leaflet-css'; l.rel = 'stylesheet';
                l.href = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.css';
                document.head.appendChild(l);
              }
              if (!window.__drrcLeaflet) {
                window.__drrcLeaflet = new Promise(function (resolve, reject) {
                  const s = document.createElement('script');
                  s.src = 'https://unpkg.com/leaflet@1.9.4/dist/leaflet.js';
                  s.onload = resolve; s.onerror = reject;
                  document.head.appendChild(s);
                });
              }
              window.__drrcLeaflet.then(done).catch(function () {
                el.textContent = 'The map library could not be loaded. Check the internet connection.';
              });
            }
            function popup(title, detail) {
              const box = document.createElement('div');
              const b = document.createElement('b'); b.textContent = title; box.appendChild(b);
              if (detail) { const p = document.createElement('div'); p.textContent = detail; box.appendChild(p); }
              return box;
            }
            const sev = { CRITICAL: '#c62828', HIGH: '#ef6c00', MEDIUM: '#f9a825', LOW: '#2e7d32' };
            load(function () {
              if (el.__map) { el.__map.remove(); el.__map = null; }
              el.innerHTML = '';
              const map = L.map(el).setView([22.5, 79], 5);
              el.__map = map;
              const street = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png',
                { maxZoom: 18, attribution: '&copy; OpenStreetMap contributors' }).addTo(map);
              const sat = L.tileLayer('https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
                { maxZoom: 18, attribution: 'Tiles &copy; Esri' });
              const bounds = [];
              function layer(points, radius, fixed) {
                const group = L.layerGroup();
                (points || []).forEach(function (p) {
                  const colour = fixed || sev[p.severity] || '#546e7a';
                  L.circleMarker([p.latitude, p.longitude],
                    { radius: radius, color: '#ffffff', weight: 2, fillColor: colour, fillOpacity: 0.9 })
                    .bindPopup(popup(p.name, p.detail)).addTo(group);
                  bounds.push([p.latitude, p.longitude]);
                });
                return group;
              }
              const overlays = {
                'Disasters': layer(data.disasters, 11, null),
                'Affected areas': layer(data.areas, 8, null),
                'Warehouses': layer(data.warehouses, 8, '#1565c0'),
                'Shelters': layer(data.shelters, 8, '#2e7d32')
              };
              const routes = L.layerGroup();
              (data.routes || []).forEach(function (r) {
                L.polyline([[r.fromLatitude, r.fromLongitude], [r.toLatitude, r.toLongitude]],
                  { color: '#7cb342', weight: 3, dashArray: '6 8' }).bindPopup(popup(r.label, r.status)).addTo(routes);
              });
              overlays['Relief routes'] = routes;
              Object.keys(overlays).forEach(function (k) { overlays[k].addTo(map); });
              L.control.layers({ 'Map': street, 'Satellite': sat }, overlays, { collapsed: true }).addTo(map);
              if (bounds.length > 0) { map.fitBounds(bounds, { padding: [30, 30], maxZoom: 11 }); }
              setTimeout(function () { map.invalidateSize(); }, 250);
            });
            """;

    private String json = "{}";
    private boolean attached;

    public LiveMap(String height) {
        addClassName("live-map");
        getStyle().set("height", height);
    }

    /** Draws the backend's map response. Safe to call before the component is on screen. */
    public void show(Map<String, Object> data) {
        json = J.toJson(data);
        if (attached) {
            draw();
        }
    }

    /** Colour key that matches the markers. */
    public static Div legend() {
        String[][] items = {{"#c62828", "Critical"}, {"#ef6c00", "High"}, {"#f9a825", "Medium"}, {"#2e7d32", "Low / shelter"},
            {"#1565c0", "Warehouse"}};
        Div box = new Div();
        box.addClassName("legend");
        for (String[] item : items) {
            Span dot = new Span();
            dot.addClassName("dot");
            dot.getStyle().set("background", item[0]);
            box.add(new Div(dot, new Span(item[1])));
        }
        return box;
    }

    @Override
    protected void onAttach(AttachEvent event) {
        super.onAttach(event);
        attached = true;
        draw();
    }

    private void draw() {
        getElement().executeJs(SCRIPT, json);
    }
}
