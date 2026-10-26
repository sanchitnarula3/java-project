package com.omr.desktop;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.Scene;
import javafx.scene.web.WebView;
import javafx.concurrent.Worker;
import javafx.stage.Stage;
import netscape.javascript.JSObject;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** JavaFX desktop window hosting the existing Jakarta EE app on embedded Tomcat. */
public final class DesktopLauncher extends Application {
    private static final String CONTEXT_PATH = "/omr-mcq-system";
    private Tomcat tomcat;
    private Path runtimeDirectory;
    private final DesktopCameraBridge cameraBridge = new DesktopCameraBridge();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        try {
            String appUrl = startEmbeddedServer();
            WebView webView = new WebView();
            webView.setContextMenuEnabled(false);
            webView.setMinSize(900, 640);
            webView.getEngine().setUserAgent(webView.getEngine().getUserAgent() + " OMRScannerDesktop");
            webView.getEngine().getLoadWorker().stateProperty().addListener((property, oldState, newState) -> {
                if (newState == Worker.State.SUCCEEDED) {
                    JSObject window = (JSObject) webView.getEngine().executeScript("window");
                    window.setMember("omrDesktopCamera", cameraBridge);
                }
            });
            webView.getEngine().load(appUrl);

            Label cameraHint = new Label("Live camera runs in this desktop app. Click Enable Camera and allow access if macOS prompts.");
            HBox toolbar = new HBox(cameraHint);
            toolbar.setAlignment(Pos.CENTER_RIGHT);
            toolbar.setPadding(new Insets(10, 14, 10, 14));
            BorderPane content = new BorderPane(webView);
            content.setTop(toolbar);

            stage.setTitle("OMR Scanner");
            stage.setMinWidth(900);
            stage.setMinHeight(640);
            stage.setWidth(1280);
            stage.setHeight(860);
            stage.setScene(new Scene(content));
            stage.show();
        } catch (Exception error) {
            error.printStackTrace();
            Platform.exit();
            throw new IllegalStateException("Could not start OMR Scanner desktop app", error);
        }
    }

    private String startEmbeddedServer() throws Exception {
        runtimeDirectory = Files.createTempDirectory("omr-scanner-desktop-");
        Path webapps = Files.createDirectories(runtimeDirectory.resolve("webapps"));
        Path warFile = webapps.resolve("omr-mcq-system.war");
        try (InputStream war = DesktopLauncher.class.getResourceAsStream("/omr-mcq-system.war")) {
            if (war == null) throw new IllegalStateException("Packaged OMR web application was not found");
            Files.copy(war, warFile);
        }

        Path tomcatBase = Files.createDirectories(runtimeDirectory.resolve("tomcat"));
        System.setProperty("catalina.base", tomcatBase.toString());
        System.setProperty("catalina.home", tomcatBase.toString());

        tomcat = new Tomcat();
        tomcat.setBaseDir(tomcatBase.toString());
        tomcat.setPort(0);
        Connector connector = tomcat.getConnector();
        connector.setProperty("address", "127.0.0.1");
        tomcat.getHost().setAppBase(webapps.toString());
        Context context = tomcat.addWebapp(CONTEXT_PATH, warFile.toAbsolutePath().toString());
        context.setParentClassLoader(DesktopLauncher.class.getClassLoader());
        tomcat.start();

        int port = connector.getLocalPort();
        if (port <= 0) throw new IllegalStateException("Embedded Tomcat did not open a local port");
        return "http://127.0.0.1:" + port + CONTEXT_PATH + "/";
    }

    @Override
    public void stop() {
        cameraBridge.stop();
        if (tomcat != null) {
            try {
                tomcat.stop();
                tomcat.destroy();
            } catch (Exception error) {
                error.printStackTrace();
            }
        }
        if (runtimeDirectory != null) {
            try (var paths = Files.walk(runtimeDirectory)) {
                paths.sorted((a, b) -> b.compareTo(a)).forEach(path -> {
                    try { Files.deleteIfExists(path); } catch (Exception ignored) { }
                });
            } catch (Exception ignored) { }
        }
    }
}
