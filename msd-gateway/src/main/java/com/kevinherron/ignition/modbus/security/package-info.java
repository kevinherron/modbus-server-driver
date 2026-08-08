/**
 * Compiles and enforces per-device source-address policies for incoming Modbus TCP connections.
 *
 * <h2>Data flow</h2>
 *
 * <p>The value from {@link
 * com.kevinherron.ignition.modbus.ModbusServerDeviceConfig.Security#allowedIpAddresses()} is
 * compiled by {@link AllowedIpAddressFilter#parse(String)}. {@link
 * com.kevinherron.ignition.modbus.ModbusServerDeviceExtensionPoint} uses the same parser during
 * save validation, and {@link com.kevinherron.ignition.modbus.ModbusServerDevice} parses the value
 * again before binding the transport. A restrictive policy is passed to {@link
 * AllowedIpAddressHandler}, which runs before the Modbus protocol handlers; an unrestricted policy
 * requires no handler.
 *
 * <h2>Runtime boundaries</h2>
 *
 * <p>Explicit rules match resolved IPv4 source addresses and never resolve DNS names. Rejected
 * channels are closed before Modbus request processing. These policies do not restrict OPC UA
 * access to the device's process image and do not replace the transport bind address.
 *
 * <h2>Lifecycle and ownership</h2>
 *
 * <p>An {@link AllowedIpAddressFilter} is immutable and may be shared. An {@link
 * AllowedIpAddressHandler} is sharable across one device's channels but should be owned per device
 * so rejection summaries remain device-scoped.
 *
 * <h2>Failure handling</h2>
 *
 * <p>A null or blank runtime value produces an unrestricted policy, while device save validation
 * rejects blank input. Malformed nonblank input causes {@link AllowedIpAddressFilter#parse(String)}
 * to throw {@link java.lang.IllegalArgumentException}; save validation reports that error against
 * the configuration field, and startup does not bind the Modbus transport.
 *
 * <h2>Extension points</h2>
 *
 * <p>Address grammar, matching, and connection-time admission belong in this package. Device-form
 * validation and transport lifecycle remain in the parent Modbus package.
 */
package com.kevinherron.ignition.modbus.security;
