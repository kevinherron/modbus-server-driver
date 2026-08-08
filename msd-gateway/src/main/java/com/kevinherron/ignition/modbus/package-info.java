/**
 * Implements the Ignition gateway device that exposes Modbus process-image data through Modbus TCP
 * and OPC UA.
 *
 * <h2>Data flow</h2>
 *
 * <p>{@link ModbusServerDeviceExtensionPoint} validates and decodes device settings, then creates a
 * {@link ModbusServerDevice}. The device owns the Modbus TCP server and two OPC UA address spaces:
 * {@link BrowsableAddressSpace} supplies the configured browse hierarchy, while {@link
 * ModbusAddressSpace} resolves variable addresses and reads or writes process-image values.
 *
 * <p>Both protocol paths select images through {@link ProcessImageManager}. Unified mode maps every
 * unit ID to one image. Separate mode maps each valid unit ID to an independent image, with
 * unqualified OPC UA addresses selecting unit 0. Browse configuration controls discovery only; it
 * does not restrict which unit IDs either protocol can address.
 *
 * <h2>Lifecycle and persistence</h2>
 *
 * <p>The device creates its process-image manager and validates connection admission before
 * accepting Modbus requests. An image is made available only after {@link ProcessImagePersistence}
 * has synchronously restored its configured dataset and attached a modification listener. Shutdown
 * unregisters the OPC UA address spaces, stops the Modbus server, removes persistence listeners,
 * and waits for accepted persistence writes to finish.
 *
 * <p>Persistence failures are logged without making an otherwise valid unit unavailable. Unified
 * and separate modes use independent storage layouts, so changing modes neither copies nor deletes
 * process-image data.
 *
 * <h2>Validation and extension boundaries</h2>
 *
 * <p>Device-form validation belongs in the extension point. The {@code security} subpackage owns
 * the remote-address grammar and Modbus TCP connection filtering; this admission policy does not
 * govern OPC UA access. OPC UA address syntax belongs in the {@code address} subpackage, and value
 * conversion belongs in {@link ModbusValueAccess}. Process-image selection, storage ownership, and
 * shutdown coordination remain in this package so the Modbus and OPC UA paths share the same
 * lifecycle and routing rules.
 */
package com.kevinherron.ignition.modbus;
