package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mussonindustrial.testcontainers.ignition.IgnitionContainer;
import com.mussonindustrial.testcontainers.ignition.IgnitionGatewayEdition;
import java.util.Arrays;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.Container;

/**
 * Verifies that a running device accepts allowed peers and promptly closes connections from
 * addresses outside its configured allow list.
 *
 * <p>Requests originate inside the Gateway container so loopback and non-loopback source addresses
 * can be tested without depending on Docker host-network addressing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AllowedIpAddressesIT {

  private static final String LOOPBACK_ADDRESS = "127.0.0.1";
  private static final String EXPECTED_RESPONSE = "0001000000050003020000";
  private static final int TIMEOUT_EXIT_CODE = 124;

  // Opens a TCP socket, marks a completed handshake on stderr, sends a one-register Modbus read,
  // and writes any response bytes as lowercase hexadecimal on stdout.
  private static final String MODBUS_READ_SCRIPT =
      """
      if ! exec 3<>"/dev/tcp/$1/502"; then
        exit 90
      fi
      printf 'CONNECTED\n' >&2
      printf '\\x00\\x01\\x00\\x00\\x00\\x06\\x00\\x03\\x00\\x00\\x00\\x01' >&3 || exit 91
      dd bs=1 count=11 <&3 2>/dev/null | od -An -tx1 | tr -d ' \\n'
      """;

  private IgnitionContainer ignitionContainer;

  @BeforeAll
  void setUpContainer() throws Exception {
    ignitionContainer =
        new IgnitionContainer(IgnitionTestSupport.IGNITION_IMAGE)
            .acceptLicense()
            .withCredentials("admin", "password")
            .withEdition(IgnitionGatewayEdition.STANDARD)
            .withAllowUnsignedModules()
            .withGatewayBackup(IgnitionTestSupport.LOOPBACK_WHITELIST_GATEWAY_BACKUP, false)
            .withThirdPartyModule(IgnitionTestSupport.requireModuleArchive())
            .withAdditionalExposedPort(IgnitionTestSupport.MODBUS_PORT);

    ignitionContainer.start();
  }

  @AfterAll
  void tearDownContainer() {
    if (ignitionContainer != null) {
      ignitionContainer.stop();
    }
  }

  // Repeated rejections must close promptly and must not interfere with later allowed connections.
  @Test
  void rejectedConnectionDoesNotPreventTwoSubsequentAllowedConnections() throws Exception {
    String containerAddress = findContainerIpv4Address();

    for (int i = 0; i < 3; i++) {
      assertRejected(executeModbusRead(containerAddress));
    }

    assertAllowed(executeModbusRead(LOOPBACK_ADDRESS));
    assertAllowed(executeModbusRead(LOOPBACK_ADDRESS));
  }

  private String findContainerIpv4Address() throws Exception {
    // Connecting to the container's own address gives the Gateway a non-loopback source address.
    Container.ExecResult result = ignitionContainer.execInContainer("hostname", "-I");
    assertEquals(0, result.getExitCode(), describe(result));

    return Arrays.stream(result.getStdout().strip().split("\\s+"))
        .filter(address -> address.indexOf(':') < 0)
        .filter(address -> !address.startsWith("127."))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "container has no non-loopback IPv4 address: " + describe(result)));
  }

  private Container.ExecResult executeModbusRead(String address) throws Exception {
    return ignitionContainer.execInContainer(
        "timeout", "5", "bash", "-c", MODBUS_READ_SCRIPT, "modbus-read", address);
  }

  private static void assertAllowed(Container.ExecResult result) {
    assertEquals(0, result.getExitCode(), describe(result));
    assertEquals(EXPECTED_RESPONSE, normalizedOutput(result), describe(result));
  }

  private static void assertRejected(Container.ExecResult result) {
    assertTrue(result.getStderr().contains("CONNECTED"), describe(result));
    assertNotEquals(
        TIMEOUT_EXIT_CODE,
        result.getExitCode(),
        "rejected connection remained open: " + describe(result));
    assertEquals(
        "",
        normalizedOutput(result),
        "non-loopback connection received bytes: " + describe(result));
  }

  private static String normalizedOutput(Container.ExecResult result) {
    return result.getStdout().strip();
  }

  private static String describe(Container.ExecResult result) {
    return "exit=%d, stdout=%s, stderr=%s"
        .formatted(result.getExitCode(), result.getStdout(), result.getStderr());
  }
}
