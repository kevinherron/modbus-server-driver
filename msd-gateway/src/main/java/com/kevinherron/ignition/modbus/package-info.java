/**
 * Integrates a Modbus TCP server with Ignition's OPC UA device subsystem around a per-device
 * process image.
 *
 * <h2>Lifecycle</h2>
 *
 * <p>{@link ModbusServerDeviceExtensionPoint} validates configuration and creates an unstarted
 * {@link ModbusServerDevice}. Device startup validates connection admission before binding the
 * Modbus listener, then starts {@link BrowsableAddressSpace} for configured browse nodes and {@link
 * ModbusAddressSpace} for dynamic value access. Shutdown stops the address spaces and listener;
 * each device owns these resources for its lifetime.
 *
 * <h2>Data flow</h2>
 *
 * <p>Modbus requests and OPC UA reads and writes share the device's {@link
 * com.digitalpetri.modbus.server.ProcessImage}. {@link ModbusValueAccess} converts between Modbus
 * storage and OPC UA values. When persistence is enabled, address-space startup restores the image
 * and records later modifications in the device's storage.
 *
 * <h2>Validation and runtime boundaries</h2>
 *
 * <p>{@link ModbusServerDeviceConfig} defines the serialized settings and form schema.
 * Extension-point validation catches invalid port, browse-range, and allow-list settings before
 * they are saved. {@link ModbusServerDevice#startup()} parses the connection allow list again
 * before binding, making runtime admission the final authority.
 *
 * <p>The {@code security} subpackage owns the remote-address grammar and Modbus TCP connection
 * enforcement. The {@code address} subpackage owns dynamic OPC UA address syntax and its Modbus
 * type and shape representation. Connection filtering applies only to Modbus TCP; Ignition remains
 * responsible for OPC UA access control.
 */
package com.kevinherron.ignition.modbus;
