package com.kevinherron.ignition.modbus;

import java.nio.file.Files;
import java.nio.file.Path;

/** Shared configuration for the integration tests. */
final class IgnitionTestSupport {

  /**
   * Ignition Docker image tag under test, overridable with {@code -Dignition.image.version}.
   *
   * <p>testcontainers-ignition requires a concrete {@code major.minor.patch} tag; {@code latest} and
   * release-line tags such as {@code 8.3} are rejected.
   */
  static final String IGNITION_IMAGE =
      "inductiveautomation/ignition:" + System.getProperty("ignition.image.version", "8.3.8");

  /** Gateway backup restored into the container. */
  static final Path GATEWAY_BACKUP = Path.of("./src/test/resources/ignition.gwbk");

  /** Port the Modbus server device binds to inside the container. */
  static final int MODBUS_PORT = 502;

  /** Unsigned module archive produced by {@code msd-build}. */
  private static final Path MODULE_ARCHIVE =
      Path.of(
          System.getProperty(
              "msd.module.path", "../msd-build/target/Modbus-Server-Driver-Module-unsigned.modl"));

  private IgnitionTestSupport() {}

  /**
   * Returns the module archive, failing with an actionable message when it has not been built.
   *
   * @return path to the unsigned module archive
   */
  static Path requireModuleArchive() {
    if (!Files.isRegularFile(MODULE_ARCHIVE)) {
      throw new IllegalStateException(
          "Module archive not found at %s. Run 'mvn package' first."
              .formatted(MODULE_ARCHIVE.toAbsolutePath()));
    }
    return MODULE_ARCHIVE;
  }
}
