package org.zalava.packaging;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.util.concurrent.TimeUnit;

/** Disposable HTTPS fixture serving an actual released module artifact. */
final class CatalogReleaseProxy implements AutoCloseable {
  private static final String REVISION = "d35aa8a8c7e662b6e890f3833c94c7bf0d527d0f";
  private static final String ASSET =
      "/Zalava/zalava-module-time/releases/download/v0.1.0-alpha.4/zalava-module-time-0.1.0-alpha.4.jar";
  private final WireMockServer server;
  private final Path trustStore;

  CatalogReleaseProxy(Path diagnostics, Path artifact) throws Exception {
    Path caKeyStore = diagnostics.resolve("catalog-proxy-ca.p12");
    Process keytool =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin/keytool").toString(),
                "-genkeypair",
                "-alias",
                "wiremock-ca",
                "-keyalg",
                "RSA",
                "-keysize",
                "2048",
                "-dname",
                "CN=Disposable catalog fixture",
                "-ext",
                "BC:critical=ca:true",
                "-ext",
                "KU:critical=digitalSignature,keyEncipherment,keyCertSign,cRLSign",
                "-ext",
                "SAN=dns:raw.githubusercontent.com,dns:api.github.com,dns:github.com",
                "-validity",
                "2",
                "-keystore",
                caKeyStore.toString(),
                "-storetype",
                "PKCS12",
                "-storepass",
                "fixture",
                "-keypass",
                "fixture",
                "-noprompt")
            .redirectErrorStream(true)
            .redirectOutput(diagnostics.resolve("catalog-proxy-keytool.log").toFile())
            .start();
    if (!keytool.waitFor(30, TimeUnit.SECONDS)) {
      keytool.destroyForcibly();
      throw new IllegalStateException("Timed out creating disposable proxy CA");
    }
    if (keytool.exitValue() != 0)
      throw new IllegalStateException("Unable to create disposable proxy CA");
    server =
        new WireMockServer(
            wireMockConfig()
                .dynamicPort()
                .enableBrowserProxying(true)
                .notifier(new ConsoleNotifier(false))
                .caKeystorePath(caKeyStore.toString())
                .caKeystorePassword("fixture")
                .caKeystoreType("PKCS12"));
    server.start();
    try {
      restoreLocator();
      server.stubFor(
          get(urlPathEqualTo("/repos/Zalava/zalava-module-time/commits"))
              .willReturn(okJson("[{\"sha\":\"" + REVISION + "\"}]")));
      try (var index = getClass().getResourceAsStream("/public-time-release-index.yaml")) {
        if (index == null) throw new IllegalStateException("Missing release-index fixture");
        server.stubFor(
            get(urlPathEqualTo("/Zalava/zalava-module-time/" + REVISION + "/releases/index.yaml"))
                .willReturn(aResponse().withStatus(200).withBody(index.readAllBytes())));
      }
      artifact(artifact);
      var ca =
          HttpClient.newHttpClient()
              .send(
                  HttpRequest.newBuilder(
                          URI.create(
                              "http://127.0.0.1:"
                                  + server.port()
                                  + "/__admin/certs/wiremock-ca.crt"))
                      .GET()
                      .build(),
                  HttpResponse.BodyHandlers.ofInputStream());
      if (ca.statusCode() != 200) {
        try (var body = ca.body()) {
          throw new IllegalStateException(
              "Missing proxy test CA: "
                  + ca.statusCode()
                  + " "
                  + new String(body.readAllBytes(), StandardCharsets.UTF_8));
        }
      }
      KeyStore store = KeyStore.getInstance("PKCS12");
      store.load(null, "fixture".toCharArray());
      try (var body = ca.body()) {
        store.setCertificateEntry(
            "test-proxy", CertificateFactory.getInstance("X.509").generateCertificate(body));
      }
      trustStore = diagnostics.resolve("catalog-proxy-trust.p12");
      try (var output = Files.newOutputStream(trustStore)) {
        store.store(output, "fixture".toCharArray());
      }
    } catch (Exception failure) {
      server.stop();
      throw failure;
    }
  }

  void artifact(Path artifact) throws Exception {
    server.stubFor(
        get(urlPathEqualTo(ASSET))
            .willReturn(aResponse().withStatus(200).withBody(Files.readAllBytes(artifact))));
  }

  void corruptArtifact() {
    server.stubFor(
        get(urlPathEqualTo(ASSET))
            .willReturn(aResponse().withStatus(200).withBody("invalid artifact")));
  }

  private void restoreLocator() {
    server.stubFor(
        get(urlPathEqualTo("/Zalava/zalava-catalog/main/catalog.yaml"))
            .willReturn(
                okJson(
                    """
          {"schemaVersion":1,"repository":{"type":"module-locator","indexRepository":"https://github.com/Zalava/zalava-catalog","indexPath":"catalog.yaml"},
          "modules":[{"moduleId":"zalava-module-time","displayName":"Released time","description":"Packaged catalog acceptance",
          "repository":"https://github.com/Zalava/zalava-module-time","releaseIndexPath":"releases/index.yaml"}]}
          """)));
  }

  void invalidMetadata() {
    server.stubFor(
        get(urlPathEqualTo("/Zalava/zalava-catalog/main/catalog.yaml"))
            .willReturn(okJson("{\"schemaVersion\":1,\"modules\":[]}")));
  }

  void restoreMetadata() {
    restoreLocator();
  }

  int port() {
    return server.port();
  }

  Path trustStore() {
    return trustStore;
  }

  @Override
  public void close() {
    server.stop();
  }
}
