package com.kevinherron.ignition.modbus;

import static com.digitalpetri.modbus.server.ProcessImage.Modification.CoilModification;
import static com.digitalpetri.modbus.server.ProcessImage.Modification.DiscreteInputModification;
import static com.digitalpetri.modbus.server.ProcessImage.Modification.HoldingRegisterModification;
import static com.digitalpetri.modbus.server.ProcessImage.Modification.InputRegisterModification;

import com.digitalpetri.modbus.server.ProcessImage;
import com.digitalpetri.modbus.server.ProcessImage.ModificationListener;
import com.digitalpetri.modbus.server.ProcessImage.Transaction;
import com.inductiveautomation.ignition.common.util.ExecutionQueue;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates persistent process-image storage for one device lifecycle.
 *
 * <p>{@link ProcessImageManager} calls {@link #initialize(int, ProcessImage)} before publishing an
 * image. Initialization restores all four Modbus areas synchronously, then begins tracking later
 * modifications. Persistent writes are serialized and complete asynchronously from the process
 * image transaction that produced them.
 *
 * <p>When persistence is disabled, initialization and shutdown have no filesystem side effects.
 * Load and write failures are logged and leave the in-memory image available, but a storage
 * directory that cannot be created fails initialization so a device is never published with
 * silently broken persistence. The owner must call {@link #close()} to detach listeners and wait
 * for accepted writes before releasing the device.
 */
final class ProcessImagePersistence implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ProcessImagePersistence.class);

  private static final int ADDRESS_COUNT = 65_536;
  private static final int BOOLEAN_FILE_SIZE = ADDRESS_COUNT;
  private static final int REGISTER_FILE_SIZE = ADDRESS_COUNT * 2;

  private final Path deviceRoot;
  private final boolean enabled;
  private final boolean separatePerUnitId;
  private final ExecutionQueue modificationQueue;
  private final Object lifecycleLock = new Object();
  private final List<ListenerRegistration> registrations = new ArrayList<>();

  private boolean closed;

  /**
   * Creates a persistence lifecycle for unified or per-unit storage.
   *
   * @param deviceRoot the device data directory that owns persistent files.
   * @param enabled whether persistence is active for this lifecycle.
   * @param separatePerUnitId whether files are stored beneath unit-specific directories.
   * @param executor the executor used for serialized persistence writes.
   */
  ProcessImagePersistence(
      Path deviceRoot, boolean enabled, boolean separatePerUnitId, Executor executor) {

    this.deviceRoot = deviceRoot.toAbsolutePath();
    this.enabled = enabled;
    this.separatePerUnitId = separatePerUnitId;
    modificationQueue = new ExecutionQueue(executor);
  }

  /**
   * Restores an image and starts tracking its subsequent modifications.
   *
   * <p>Call this once for an image before making that image available to protocol requests. When
   * persistence is disabled, this method returns without creating directories, files, or
   * listeners. It must not be called after {@link #close()}.
   *
   * @param unitId the validated unit ID associated with the image.
   * @param processImage the image to restore and observe.
   * @throws IllegalArgumentException if persistence is enabled and {@code unitId} is outside 0
   *     through 255.
   * @throws UncheckedIOException if the persistence directory cannot be created; the image must
   *     not be published without its persistent state.
   */
  void initialize(int unitId, ProcessImage processImage) {
    if (!enabled) {
      return;
    }
    ProcessImageManager.validateUnitId(unitId);

    Path directory = directoryFor(unitId);
    try {
      Files.createDirectories(directory);
    } catch (IOException e) {
      logFailure("create directory", unitId, "all areas", directory, e);
      throw new UncheckedIOException(
          "unable to create persistence directory: " + directory, e);
    }

    processImage.with(
        tx -> {
          loadBooleans(tx, unitId, "coils", directory.resolve("coils.bin"), true);
          loadBooleans(
              tx,
              unitId,
              "discrete inputs",
              directory.resolve("discreteInputs.bin"),
              false);
          loadRegisters(
              tx,
              unitId,
              "holding registers",
              directory.resolve("holdingRegisters.bin"),
              true);
          loadRegisters(
              tx,
              unitId,
              "input registers",
              directory.resolve("inputRegisters.bin"),
              false);
        });

    var listener = new PersistenceListener(unitId, directory);
    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      processImage.addModificationListener(listener);
      registrations.add(new ListenerRegistration(processImage, listener));
    }
  }

  private void loadBooleans(
      Transaction tx, int unitId, String area, Path path, boolean coils) {

    ByteBuffer values = readPersistedFile(unitId, area, path, BOOLEAN_FILE_SIZE);
    if (values == null) {
      return;
    }

    if (coils) {
      tx.writeCoils(map -> putBooleans(map, values));
    } else {
      tx.writeDiscreteInputs(map -> putBooleans(map, values));
    }
  }

  private void loadRegisters(
      Transaction tx, int unitId, String area, Path path, boolean holdingRegisters) {

    ByteBuffer values = readPersistedFile(unitId, area, path, REGISTER_FILE_SIZE);
    if (values == null) {
      return;
    }

    if (holdingRegisters) {
      tx.writeHoldingRegisters(map -> putRegisters(map, values));
    } else {
      tx.writeInputRegisters(map -> putRegisters(map, values));
    }
  }

  /**
   * Reads a persisted area file, or returns {@code null} when nothing was ever persisted.
   *
   * <p>Files are created and sized on first write, not here, so probing unit IDs does not
   * materialize storage for units that never persisted a value.
   */
  private ByteBuffer readPersistedFile(int unitId, String area, Path path, int size) {
    if (Files.notExists(path)) {
      return null;
    }

    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
      ByteBuffer values = ByteBuffer.allocate(size);
      readFully(channel, values);
      return values.flip();
    } catch (IOException e) {
      logFailure("load", unitId, area, path, e);
      return null;
    }
  }

  private static void putBooleans(Map<Integer, Boolean> map, ByteBuffer values) {
    for (int address = 0; address < values.limit(); address++) {
      if (values.get(address) != 0) {
        map.put(address, true);
      }
    }
  }

  private static void putRegisters(Map<Integer, byte[]> map, ByteBuffer values) {
    for (int index = 0; index + 1 < values.limit(); index += 2) {
      byte high = values.get(index);
      byte low = values.get(index + 1);
      if (high != 0 || low != 0) {
        map.put(index / 2, new byte[] {high, low});
      }
    }
  }

  private Path directoryFor(int unitId) {
    return separatePerUnitId
        ? deviceRoot.resolve("units").resolve(Integer.toString(unitId))
        : deviceRoot;
  }

  private static FileChannel openSizedFile(Path path, int size) throws IOException {
    FileChannel channel = openFile(path);
    try {
      long currentSize = channel.size();
      if (currentSize > size) {
        channel.truncate(size);
      } else if (currentSize < size) {
        channel.position(size - 1L);
        writeFully(channel, ByteBuffer.wrap(new byte[] {0}));
      }
      channel.position(0);
      return channel;
    } catch (IOException e) {
      channel.close();
      throw e;
    }
  }

  private static FileChannel openFile(Path path) throws IOException {
    return FileChannel.open(
        path, StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.CREATE);
  }

  private static void readFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (channel.read(buffer) == -1) {
        break;
      }
    }
  }

  private static void writeFully(FileChannel channel, ByteBuffer buffer) throws IOException {
    while (buffer.hasRemaining()) {
      if (channel.write(buffer) == 0) {
        Thread.onSpinWait();
      }
    }
  }

  private void submit(Runnable task) {
    synchronized (lifecycleLock) {
      if (!closed) {
        modificationQueue.submit(task);
      }
    }
  }

  private void logFailure(String operation, int unitId, String area, Path path, IOException e) {
    logger.error(
        "Process image persistence {} failed: mode={}, unitId={}, area={}, path={}",
        operation,
        separatePerUnitId ? "separate" : "unified",
        unitId,
        area,
        path,
        e);
  }

  /**
   * Detaches all persistence listeners and waits for previously accepted writes to finish.
   *
   * <p>This method is idempotent. Images modified after it returns are not persisted by this
   * lifecycle.
   */
  @Override
  public void close() {
    if (!enabled) {
      return;
    }

    synchronized (lifecycleLock) {
      if (closed) {
        return;
      }
      closed = true;
      for (ListenerRegistration registration : registrations) {
        registration.processImage().removeModificationListener(registration.listener());
      }
      registrations.clear();
    }

    try {
      modificationQueue.runOrSubmit(() -> {}).join();
    } catch (CompletionException e) {
      logger.error("Error draining process image persistence queue", e.getCause());
    }
  }

  private final class PersistenceListener implements ModificationListener {

    private final int unitId;
    private final Path directory;

    private PersistenceListener(int unitId, Path directory) {
      this.unitId = unitId;
      this.directory = directory;
    }

    @Override
    public void onCoilsModified(List<CoilModification> modifications) {
      List<BooleanWrite> writes =
          modifications.stream()
              .map(modification -> new BooleanWrite(modification.address(), modification.value()))
              .toList();
      submit(() -> writeBooleans(unitId, "coils", directory.resolve("coils.bin"), writes));
    }

    @Override
    public void onDiscreteInputsModified(List<DiscreteInputModification> modifications) {
      List<BooleanWrite> writes =
          modifications.stream()
              .map(modification -> new BooleanWrite(modification.address(), modification.value()))
              .toList();
      submit(
          () ->
              writeBooleans(
                  unitId,
                  "discrete inputs",
                  directory.resolve("discreteInputs.bin"),
                  writes));
    }

    @Override
    public void onHoldingRegistersModified(List<HoldingRegisterModification> modifications) {
      List<RegisterWrite> writes =
          modifications.stream()
              .map(
                  modification ->
                      new RegisterWrite(modification.address(), modification.value().clone()))
              .toList();
      submit(
          () ->
              writeRegisters(
                  unitId,
                  "holding registers",
                  directory.resolve("holdingRegisters.bin"),
                  writes));
    }

    @Override
    public void onInputRegistersModified(List<InputRegisterModification> modifications) {
      List<RegisterWrite> writes =
          modifications.stream()
              .map(
                  modification ->
                      new RegisterWrite(modification.address(), modification.value().clone()))
              .toList();
      submit(
          () ->
              writeRegisters(
                  unitId,
                  "input registers",
                  directory.resolve("inputRegisters.bin"),
                  writes));
    }
  }

  private void writeBooleans(
      int unitId, String area, Path path, List<BooleanWrite> writes) {

    try (FileChannel channel = openSizedFile(path, BOOLEAN_FILE_SIZE)) {
      for (BooleanWrite write : writes) {
        channel.position(write.address());
        writeFully(channel, ByteBuffer.wrap(new byte[] {(byte) (write.value() ? 1 : 0)}));
      }
    } catch (IOException e) {
      logFailure("write", unitId, area, path, e);
    }
  }

  private void writeRegisters(
      int unitId, String area, Path path, List<RegisterWrite> writes) {

    try (FileChannel channel = openSizedFile(path, REGISTER_FILE_SIZE)) {
      for (RegisterWrite write : writes) {
        channel.position(write.address() * 2L);
        writeFully(channel, ByteBuffer.wrap(write.value()));
      }
    } catch (IOException e) {
      logFailure("write", unitId, area, path, e);
    }
  }

  private record ListenerRegistration(
      ProcessImage processImage, ModificationListener listener) {}

  private record BooleanWrite(int address, boolean value) {}

  private record RegisterWrite(int address, byte[] value) {}
}
