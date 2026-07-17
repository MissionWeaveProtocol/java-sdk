package org.missionweaveprotocol.examples;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.missionweaveprotocol.sdk.FrameCodec;

/** Strictly validate and canonically encode a generic WebSocket frame. */
public final class FrameRoundTripExample {
  private FrameRoundTripExample() {}

  public static void main(String[] arguments) throws Exception {
    run(System.out);
  }

  public static void run(PrintStream output) throws Exception {
    Objects.requireNonNull(output, "output");
    byte[] incoming =
        """
        {
          "protocolVersion": "0.1",
          "frameId": "urn:uuid:00000000-0000-4000-8000-000000000010",
          "frameType": "PING",
          "nonce": "cGluZw",
          "sentAt": "2026-07-17T08:00:00Z"
        }
        """
            .getBytes(StandardCharsets.UTF_8);

    FrameCodec codec = new FrameCodec();
    var frame = codec.decode(incoming);
    byte[] canonical = codec.encode(frame);

    output.println(frame.get("frameType").textValue());
    output.println(new String(canonical, StandardCharsets.UTF_8));
  }
}
