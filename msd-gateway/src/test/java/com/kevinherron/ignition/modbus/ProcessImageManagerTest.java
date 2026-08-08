package com.kevinherron.ignition.modbus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.digitalpetri.modbus.pdu.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.pdu.WriteSingleRegisterRequest;
import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ReadWriteModbusServices;
import com.kevinherron.ignition.modbus.address.ModbusAddressParser;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProcessImageManagerTest {

  @TempDir Path temporaryDirectory;

  @Nested
  class ImageSelection {

    // Unified mode is the compatibility default, so every valid Modbus unit must continue to
    // observe the same process image.
    @Test
    void unifiedModeReturnsOneImageForEveryUnit() {
      try (var manager = manager(false)) {
        ProcessImage image = manager.get(0);

        assertSame(image, manager.get(1));
        assertSame(image, manager.get(255));
      }
    }

    @Test
    void separateModeReturnsStableDistinctImages() {
      try (var manager = manager(true)) {
        ProcessImage unit0 = manager.get(0);
        ProcessImage unit1 = manager.get(1);

        assertSame(unit0, manager.get(0));
        assertSame(unit1, manager.get(1));
        assertNotSame(unit0, unit1);
        assertNotSame(unit1, manager.get(255));
      }
    }

    // Unqualified OPC UA addresses intentionally alias unit 0 rather than a separate implicit
    // image, while an explicit prefix selects that unit's independent image.
    @Test
    void unqualifiedAddressSelectsUnitZeroInSeparateMode() throws Exception {
      try (var manager = manager(true)) {
        assertSame(manager.get(0), manager.get(ModbusAddressParser.parse("HR0")));
        assertSame(manager.get(7), manager.get(ModbusAddressParser.parse("7.HR0")));
        assertNotSame(
            manager.get(ModbusAddressParser.parse("HR0")),
            manager.get(ModbusAddressParser.parse("7.HR0")));
      }
    }

    @Test
    void unitIdsOutsideTheModbusRangeAreRejected() {
      try (var manager = manager(true)) {
        assertThrows(IllegalArgumentException.class, () -> manager.get(-1));
        assertThrows(IllegalArgumentException.class, () -> manager.get(256));
      }
    }

    // Concurrent lazy initialization must never expose competing images for the same unit; doing
    // so would split protocol reads and writes depending on which caller won the race.
    @Test
    void concurrentFirstAccessPublishesOneImage() throws Exception {
      try (var manager = manager(true)) {
        int taskCount = 16;
        ExecutorService executor = Executors.newFixedThreadPool(taskCount);
        var ready = new CountDownLatch(taskCount);
        var start = new CountDownLatch(1);
        List<Future<ProcessImage>> futures = new ArrayList<>();

        try {
          for (int i = 0; i < taskCount; i++) {
            futures.add(
                executor.submit(
                    () -> {
                      ready.countDown();
                      start.await();
                      return manager.get(42);
                    }));
          }

          assertTrue(
              ready.await(5, TimeUnit.SECONDS),
              "all workers must reach the start gate before testing concurrent access");
          start.countDown();

          ProcessImage expected = futures.get(0).get(5, TimeUnit.SECONDS);
          for (Future<ProcessImage> future : futures) {
            assertSame(expected, future.get(5, TimeUnit.SECONDS));
          }
        } finally {
          executor.shutdownNow();
        }
      }
    }
  }

  @Nested
  class ProtocolRouting {

    // This exercises the actual Modbus service boundary so a future device wiring change cannot
    // bypass the manager and accidentally partition the default unified image.
    @Test
    void modbusServicesAliasUnitsInUnifiedMode() throws Exception {
      try (var manager = manager(false)) {
        ReadWriteModbusServices services = services(manager);

        services.writeSingleRegister(null, 7, new WriteSingleRegisterRequest(3, 0x1234));

        assertArrayEquals(
            new byte[] {0x12, 0x34},
            services
                .readHoldingRegisters(null, 1, new ReadHoldingRegistersRequest(3, 1))
                .registers());
      }
    }

    // The baseline read from another unit proves isolation rather than merely confirming that the
    // write reached its intended image.
    @Test
    void modbusServicesIsolateUnitsInSeparateMode() throws Exception {
      try (var manager = manager(true)) {
        ReadWriteModbusServices services = services(manager);

        services.writeSingleRegister(null, 7, new WriteSingleRegisterRequest(3, 0x1234));

        assertArrayEquals(
            new byte[] {0x12, 0x34},
            services
                .readHoldingRegisters(null, 7, new ReadHoldingRegistersRequest(3, 1))
                .registers());
        assertArrayEquals(
            new byte[] {0, 0},
            services
                .readHoldingRegisters(null, 1, new ReadHoldingRegistersRequest(3, 1))
                .registers());
      }
    }
  }

  @Nested
  class Lifecycle {

    @Test
    void accessAfterCloseIsRejected() {
      ProcessImageManager manager = manager(false);
      manager.close();

      assertThrows(IllegalStateException.class, () -> manager.get(0));
    }
  }

  private ProcessImageManager manager(boolean separatePerUnitId) {
    return new ProcessImageManager(
        separatePerUnitId, false, temporaryDirectory.resolve("device"), Runnable::run);
  }

  private static ReadWriteModbusServices services(ProcessImageManager manager) {
    return new ReadWriteModbusServices() {
      @Override
      protected Optional<ProcessImage> getProcessImage(int unitId) {
        return Optional.of(manager.get(unitId));
      }
    };
  }
}
