package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.server.ProcessImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessImagePersistenceTest {

  private static final int LAST_ADDRESS = 65_535;

  @TempDir Path temporaryDirectory;

  @Nested
  class StorageLayout {

    // Disabled persistence must be inert: creating its lifecycle may not leave empty directories,
    // files, or listeners that begin writing later.
    @Test
    void disabledPersistenceCreatesNoFilesDirectoriesOrListeners() throws Exception {
      Path deviceRoot = temporaryDirectory.resolve("disabled-device");
      var image = new ProcessImage();
      var persistence = new ProcessImagePersistence(deviceRoot, false, true, Runnable::run);

      persistence.initialize(1, image);
      writeAllAreas(image, 4, true, true, new byte[] {0x12, 0x34}, new byte[] {0x56, 0x78});
      persistence.close();

      assertFalse(Files.exists(deviceRoot));

      writeAllAreas(image, 5, true, true, new byte[] {0x11, 0x22}, new byte[] {0x33, 0x44});
      assertFalse(Files.exists(deviceRoot));
    }

    // Address 65,535 guards the upper-bound slot that was previously omitted from persisted
    // files; the size assertions also protect compatibility with the established file layout.
    @Test
    void unifiedFilesRoundTripAllAreasIncludingLastAddress() throws Exception {
      Path deviceRoot = temporaryDirectory.resolve("unified-device");
      var image = new ProcessImage();

      try (var persistence =
          new ProcessImagePersistence(deviceRoot, true, false, Runnable::run)) {
        persistence.initialize(0, image);
        writeAllAreas(image, 10, true, true, new byte[] {0x12, 0x34}, new byte[] {0x56, 0x78});
        writeAllAreas(
            image,
            LAST_ADDRESS,
            true,
            true,
            new byte[] {(byte) 0x9A, (byte) 0xBC},
            new byte[] {(byte) 0xDE, (byte) 0xF0});
      }

      assertEquals(65_536, Files.size(deviceRoot.resolve("coils.bin")));
      assertEquals(65_536, Files.size(deviceRoot.resolve("discreteInputs.bin")));
      assertEquals(131_072, Files.size(deviceRoot.resolve("holdingRegisters.bin")));
      assertEquals(131_072, Files.size(deviceRoot.resolve("inputRegisters.bin")));
      assertFalse(Files.exists(deviceRoot.resolve("units")));

      var restored = new ProcessImage();
      try (var persistence =
          new ProcessImagePersistence(deviceRoot, true, false, Runnable::run)) {
        persistence.initialize(0, restored);
        assertAllAreas(
            restored, 10, true, true, new byte[] {0x12, 0x34}, new byte[] {0x56, 0x78});
        assertAllAreas(
            restored,
            LAST_ADDRESS,
            true,
            true,
            new byte[] {(byte) 0x9A, (byte) 0xBC},
            new byte[] {(byte) 0xDE, (byte) 0xF0});
      }
    }

    // Identical addresses in different unit directories must restore independently; otherwise a
    // restart would silently collapse the separate process images back into one dataset.
    @Test
    void separateFilesRoundTripAndIsolateTheSameOffsets() throws Exception {
      Path deviceRoot = temporaryDirectory.resolve("separate-device");
      var unit1 = new ProcessImage();
      var unit2 = new ProcessImage();

      try (var persistence =
          new ProcessImagePersistence(deviceRoot, true, true, Runnable::run)) {
        persistence.initialize(1, unit1);
        persistence.initialize(2, unit2);
        writeAllAreas(
            unit1, 20, true, false, new byte[] {0x11, 0x11}, new byte[] {0x22, 0x22});
        writeAllAreas(
            unit2, 20, false, true, new byte[] {0x33, 0x33}, new byte[] {0x44, 0x44});
      }

      assertTrue(Files.exists(deviceRoot.resolve("units/1/coils.bin")));
      assertTrue(Files.exists(deviceRoot.resolve("units/2/coils.bin")));
      assertFalse(Files.exists(deviceRoot.resolve("coils.bin")));

      var restoredUnit1 = new ProcessImage();
      var restoredUnit2 = new ProcessImage();
      try (var persistence =
          new ProcessImagePersistence(deviceRoot, true, true, Runnable::run)) {
        persistence.initialize(1, restoredUnit1);
        persistence.initialize(2, restoredUnit2);
        assertAllAreas(
            restoredUnit1,
            20,
            true,
            false,
            new byte[] {0x11, 0x11},
            new byte[] {0x22, 0x22});
        assertAllAreas(
            restoredUnit2,
            20,
            false,
            true,
            new byte[] {0x33, 0x33},
            new byte[] {0x44, 0x44});
      }
    }

    // Switching modes must select an independent storage layout without copying, deleting, or
    // overwriting the last dataset saved by either mode.
    @Test
    void switchingModesPreservesIndependentDatasets() throws Exception {
      Path deviceRoot = temporaryDirectory.resolve("mode-switch-device");

      writeHoldingRegister(deviceRoot, false, 0, 0, new byte[] {0x11, 0x11});
      writeHoldingRegister(deviceRoot, true, 0, 0, new byte[] {0x22, 0x22});

      assertArrayEquals(
          new byte[] {0x11, 0x11}, readHoldingRegister(deviceRoot, false, 0, 0));
      assertArrayEquals(
          new byte[] {0x22, 0x22}, readHoldingRegister(deviceRoot, true, 0, 0));

      writeHoldingRegister(deviceRoot, false, 0, 1, new byte[] {0x33, 0x33});

      assertArrayEquals(
          new byte[] {0x22, 0x22}, readHoldingRegister(deviceRoot, true, 0, 0));
      assertArrayEquals(
          new byte[] {0x11, 0x11}, readHoldingRegister(deviceRoot, false, 0, 0));
      assertArrayEquals(
          new byte[] {0x33, 0x33}, readHoldingRegister(deviceRoot, false, 0, 1));
    }
  }

  @Nested
  class Lifecycle {

    // close() is the durability boundary: it must wait for already accepted writes and detach
    // listeners so later image changes cannot leak into the stopped device's files.
    @Test
    void closeDrainsQueuedWritesAndRemovesListeners() throws Exception {
      Path deviceRoot = temporaryDirectory.resolve("drain-device");
      ExecutorService persistenceExecutor = Executors.newSingleThreadExecutor();
      ExecutorService closeExecutor = Executors.newSingleThreadExecutor();
      var blockerStarted = new CountDownLatch(1);
      var releaseBlocker = new CountDownLatch(1);
      var closeStarted = new CountDownLatch(1);

      persistenceExecutor.execute(
          () -> {
            blockerStarted.countDown();
            await(releaseBlocker);
          });
      assertTrue(
          blockerStarted.await(5, TimeUnit.SECONDS),
          "the persistence executor must be blocked before a write is queued");

      var image = new ProcessImage();
      var persistence =
          new ProcessImagePersistence(deviceRoot, true, false, persistenceExecutor);

      try {
        persistence.initialize(0, image);
        writeHoldingRegister(image, 8, new byte[] {0x12, 0x34});

        Future<?> closeFuture =
            closeExecutor.submit(
                () -> {
                  closeStarted.countDown();
                  persistence.close();
                });
        assertTrue(
            closeStarted.await(5, TimeUnit.SECONDS),
            "the close task must start before checking its drain behavior");
        assertThrows(
            TimeoutException.class,
            () -> closeFuture.get(100, TimeUnit.MILLISECONDS),
            "close must wait while a previously accepted persistence write is blocked");

        releaseBlocker.countDown();
        closeFuture.get(5, TimeUnit.SECONDS);

        writeHoldingRegister(image, 8, new byte[] {0x56, 0x78});

        assertArrayEquals(
            new byte[] {0x12, 0x34}, readHoldingRegister(deviceRoot, false, 0, 8));
      } finally {
        releaseBlocker.countDown();
        persistence.close();
        closeExecutor.shutdownNow();
        persistenceExecutor.shutdownNow();
      }
    }
  }

  private static void writeHoldingRegister(
      Path deviceRoot,
      boolean separatePerUnitId,
      int unitId,
      int address,
      byte[] value) {

    var image = new ProcessImage();
    try (var persistence =
        new ProcessImagePersistence(
            deviceRoot, true, separatePerUnitId, Runnable::run)) {
      persistence.initialize(unitId, image);
      writeHoldingRegister(image, address, value);
    }
  }

  private static void writeHoldingRegister(ProcessImage image, int address, byte[] value) {
    image.with(tx -> tx.writeHoldingRegisters(map -> map.put(address, value)));
  }

  private static byte[] readHoldingRegister(
      Path deviceRoot, boolean separatePerUnitId, int unitId, int address) {

    var image = new ProcessImage();
    try (var persistence =
        new ProcessImagePersistence(
            deviceRoot, true, separatePerUnitId, Runnable::run)) {
      persistence.initialize(unitId, image);
      return holdingRegister(image, address);
    }
  }

  private static void writeAllAreas(
      ProcessImage image,
      int address,
      boolean coil,
      boolean discreteInput,
      byte[] holdingRegister,
      byte[] inputRegister) {

    image.with(
        tx -> {
          tx.writeCoils(map -> map.put(address, coil));
          tx.writeDiscreteInputs(map -> map.put(address, discreteInput));
          tx.writeHoldingRegisters(map -> map.put(address, holdingRegister));
          tx.writeInputRegisters(map -> map.put(address, inputRegister));
        });
  }

  private static void assertAllAreas(
      ProcessImage image,
      int address,
      boolean coil,
      boolean discreteInput,
      byte[] holdingRegister,
      byte[] inputRegister) {

    image.with(
        tx -> {
          assertEquals(coil, tx.readCoils(map -> map.getOrDefault(address, false)));
          assertEquals(
              discreteInput,
              tx.readDiscreteInputs(map -> map.getOrDefault(address, false)));
          assertArrayEquals(
              holdingRegister,
              tx.readHoldingRegisters(map -> map.getOrDefault(address, new byte[2])));
          assertArrayEquals(
              inputRegister,
              tx.readInputRegisters(map -> map.getOrDefault(address, new byte[2])));
        });
  }

  private static byte[] holdingRegister(ProcessImage image, int address) {
    return image.get(
        tx ->
            tx.readHoldingRegisters(
                map -> map.getOrDefault(address, new byte[2]).clone()));
  }

  private static void await(CountDownLatch latch) {
    boolean interrupted = false;
    while (true) {
      try {
        latch.await();
        break;
      } catch (InterruptedException e) {
        interrupted = true;
      }
    }
    if (interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
