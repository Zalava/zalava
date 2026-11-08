package org.zalava.packaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentMatchers;

class ReleasedArtifactDownloadTest {
  @TempDir Path diagnostics;

  @Test
  void retriesConnectionFailureAndReturnsSuccessfulResponse() throws Exception {
    HttpClient client = mock(HttpClient.class);
    @SuppressWarnings("unchecked")
    HttpResponse<byte[]> response = mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenThrow(new IOException("connection reset"))
        .thenReturn(response);

    assertThat(
            ReleasedModuleDistributableAcceptanceTest.download(
                client, URI.create("https://example.com/artifact"), diagnostics, 0))
        .isSameAs(response);
    verify(client, times(2)).send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
  }

  @Test
  void failsAfterBoundedConnectionRetries() throws Exception {
    HttpClient client = mock(HttpClient.class);
    when(client.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenThrow(new IOException("connection reset"));

    assertThatThrownBy(
            () ->
                ReleasedModuleDistributableAcceptanceTest.download(
                    client, URI.create("https://example.com/artifact"), diagnostics, 0))
        .isInstanceOf(IOException.class)
        .hasMessage("connection reset");
    verify(client, times(3)).send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any());
  }
}
