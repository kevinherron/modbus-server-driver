package com.kevinherron.ignition.modbus;

import java.nio.file.Files;
import java.nio.file.Path;

/** Runtime settings and artifacts shared by integration tests that start an Ignition Gateway. */
final class IgnitionTestSupport {

  /**
   * Ignition Docker image tag under test, overridable with {@code -Dignition.image.version}.
   *
   * <p>testcontainers-ignition requires a concrete {@code major.minor.patch} tag; {@code latest}
   * and release-line tags such as {@code 8.3} are rejected.
   */
  static final String IGNITION_IMAGE =
      "inductiveautomation/ignition:" + System.getProperty("ignition.image.version", "8.3.8");

  /** Baseline Gateway backup used for migration and unrestricted-access coverage. */
  static final Path GATEWAY_BACKUP = Path.of("./src/test/resources/ignition.gwbk");

  /** Gateway backup whose Modbus device permits loopback IPv4 connections only. */
  static final Path LOOPBACK_WHITELIST_GATEWAY_BACKUP =
      Path.of("./src/test/resources/ignition-loopback-whitelist.gwbk");

  /** Port the Modbus server device binds to inside the container. */
  static final int MODBUS_PORT = 502;

  /** Unsigned module archive produced by {@code msd-build}. */
  private static final Path MODULE_ARCHIVE =
      Path.of(
          System.getProperty(
              "msd.module.path", "../msd-build/target/Modbus-Server-Driver-Module-unsigned.modl"));

  private IgnitionTestSupport() {}

  /**
   * Returns the configured unsigned module archive after verifying that it is a regular file.
   *
   * <p>Override the default path with {@code -Dmsd.module.path=/path/to/module.modl}.
   *
   * @return path to the unsigned module archive.
   * @throws IllegalStateException if the configured path does not identify a regular file.
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
