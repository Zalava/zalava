package org.zalava.fixture;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.Signature;
import java.sql.Driver;
import java.util.ServiceLoader;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import jdk.jfr.Recording;

/** Executed by the actual contributed JRE, with no application context or external services. */
public final class OciJvmProbe {
  public static void main(String[] args) throws Exception {
    var pair = KeyPairGenerator.getInstance("EC").generateKeyPair();
    var signature = Signature.getInstance("SHA256withECDSA");
    signature.initSign(pair.getPrivate());
    signature.update(new byte[] {1, 2, 3});
    byte[] signed = signature.sign();
    signature.initVerify(pair.getPublic());
    signature.update(new byte[] {1, 2, 3});
    if (!signature.verify(signed)) throw new AssertionError("EC signing failed");
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (var input = Files.newInputStream(Path.of(args[0]))) {
      store.load(input, "fixture".toCharArray());
    }
    var keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(store, "fixture".toCharArray());
    var trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
    trust.init(store);
    var tls = SSLContext.getInstance("TLS");
    tls.init(keys.getKeyManagers(), trust.getTrustManagers(), null);
    var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(tls));
    server.createContext(
        "/probe",
        exchange -> {
          byte[] body = "TLS works".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, body.length);
          try (var output = exchange.getResponseBody()) {
            output.write(body);
          }
        });
    server.start();
    try (var client = HttpClient.newBuilder().sslContext(tls).build();
        var recording = new Recording()) {
      recording.start();
      var response =
          client.send(
              HttpRequest.newBuilder(
                      URI.create("https://127.0.0.1:" + server.getAddress().getPort() + "/probe"))
                  .GET()
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      if (response.statusCode() != 200 || !response.body().equals("TLS works"))
        throw new AssertionError("TLS handshake failed");
      if (ServiceLoader.load(Driver.class).stream()
          .noneMatch(provider -> provider.type().getName().equals("org.postgresql.Driver")))
        throw new AssertionError("JDBC ServiceLoader failed");
      if (Class.forName("org.zalava.ZalavaApplication").getDeclaredMethods().length == 0)
        throw new AssertionError("Reflection failed");
      recording.stop();
      recording.dump(Path.of("/tmp/probe.jfr"));
      if (Files.size(Path.of("/tmp/probe.jfr")) == 0) throw new AssertionError("JFR unavailable");
    } finally {
      server.stop(0);
    }
    System.out.println(
        "OCI JVM probe passed: TLS, EC crypto, PKCS12, reflection, JDBC services, JFR");
  }
}
