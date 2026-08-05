package com.kevinherron.ignition.modbus;

import com.digitalpetri.modbus.server.ProcessImage;
import com.kevinherron.ignition.modbus.address.ModbusAddress;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Resolves Modbus unit IDs and OPC UA addresses to the process images owned by one {@link
 * ModbusServerDevice}.
 *
 * <p>Unified mode returns one shared image for every unit ID. Separate mode returns a stable,
 * independent image for each valid unit ID and treats an unqualified OPC UA address as unit 0. An
 * image is not returned until its configured persistent state is ready for use.
 *
 * <p>The owning device must call {@link #close()} after its address spaces and Modbus server have
 * stopped. The manager cannot be used after it is closed.
 */
final class ProcessImageManager implements AutoCloseable {

  private final boolean separatePerUnitId;
  private final ProcessImagePersistence persistence;
  private final ProcessImage unifiedImage;
  private final ConcurrentHashMap<Integer, ProcessImage> unitImages = new ConcurrentHashMap<>();

  // get() holds the read lock while it creates and returns images so that close(), which takes
  // the write lock, cannot detach persistence while an image is being created or handed out.
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private boolean closed;

  /**
   * Creates a manager for a device and prepares the unified image immediately when unified mode is
   * selected.
   *
   * @param separatePerUnitId whether each unit ID has an independent process image.
   * @param persistData whether process-image values are persisted across device lifecycles.
   * @param deviceFolderPath the device data directory used for persistent files.
   * @param executor the executor used for persistence writes.
   */
  ProcessImageManager(
      boolean separatePerUnitId,
      boolean persistData,
      Path deviceFolderPath,
      Executor executor) {

    this(
        separatePerUnitId,
        new ProcessImagePersistence(
            deviceFolderPath, persistData, separatePerUnitId, executor));
  }

  /**
   * Creates a manager using an existing persistence lifecycle.
   *
   * @param separatePerUnitId whether each unit ID has an independent process image.
   * @param persistence the persistence lifecycle owned by this manager.
   */
  ProcessImageManager(
      boolean separatePerUnitId, ProcessImagePersistence persistence) {

    this.separatePerUnitId = separatePerUnitId;
    this.persistence = persistence;
    unifiedImage = separatePerUnitId ? null : createProcessImage(0);
  }

  /**
   * Returns the process image selected by a Modbus unit ID.
   *
   * @param unitId the unit ID from 0 through 255.
   * @return the selected process image.
   * @throws IllegalArgumentException if {@code unitId} is outside the valid Modbus range.
   * @throws IllegalStateException if the manager has been closed.
   */
  ProcessImage get(int unitId) {
    validateUnitId(unitId);
    lock.readLock().lock();
    try {
      if (closed) {
        throw new IllegalStateException("process image manager is closed");
      }

      return separatePerUnitId
          ? unitImages.computeIfAbsent(unitId, this::createProcessImage)
          : unifiedImage;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Returns the process image selected by an OPC UA Modbus address.
   *
   * @param address the parsed address; an absent unit ID selects unit 0.
   * @return the selected process image.
   * @throws NullPointerException if {@code address} is null.
   * @throws IllegalStateException if the manager has been closed.
   */
  ProcessImage get(ModbusAddress address) {
    return get(address.getUnitId().orElse(0));
  }

  private ProcessImage createProcessImage(int unitId) {
    var processImage = new ProcessImage();
    persistence.initialize(unitId, processImage);
    return processImage;
  }

  /**
   * Validates a Modbus unit ID.
   *
   * @param unitId the unit ID to validate.
   * @throws IllegalArgumentException if {@code unitId} is outside 0 through 255.
   */
  static void validateUnitId(int unitId) {
    if (unitId < 0 || unitId > 255) {
      throw new IllegalArgumentException("unit ID must be between 0 and 255: " + unitId);
    }
  }

  /**
   * Stops persistence tracking and waits for previously accepted writes to finish.
   *
   * <p>This method is idempotent. It waits for in-flight {@link #get(int)} calls, and calls made
   * after it returns fail with {@link IllegalStateException}.
   */
  @Override
  public void close() {
    lock.writeLock().lock();
    try {
      if (closed) {
        return;
      }
      closed = true;
    } finally {
      lock.writeLock().unlock();
    }

    persistence.close();
    unitImages.clear();
  }
}
