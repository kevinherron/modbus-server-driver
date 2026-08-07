package com.kevinherron.ignition.modbus.security;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

/**
 * An immutable, thread-safe IPv4 source-address policy for incoming Modbus TCP connections.
 *
 * <p>Use {@link #parse(String)} to compile a device's comma-separated configuration, then query it
 * with {@link #isAllowed(InetAddress)} or pass it to {@link AllowedIpAddressHandler}. Parsing and
 * matching never perform hostname resolution.
 */
public final class AllowedIpAddressFilter {

  private static final int MAX_ENTRIES = 256;
  private static final int MAX_LENGTH = 4096;

  private static final byte[] MIN_ADDRESS = new byte[] {0, 0, 0, 0};
  private static final byte[] MAX_ADDRESS =
      new byte[] {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0xff};
  private static final AllowedIpAddressFilter ALLOW_ALL =
      new AllowedIpAddressFilter(true, List.of());

  private final boolean allowsAll;
  private final List<AddressRange> ranges;

  private AllowedIpAddressFilter(boolean allowsAll, List<AddressRange> ranges) {
    this.allowsAll = allowsAll;
    this.ranges = List.copyOf(ranges);
  }

  /**
   * Compiles a comma-separated IPv4 source-address policy.
   *
   * <p>Entries may be {@code *}, strict dotted-decimal IPv4 literals, CIDR networks whose host bits
   * are zero, trailing whole-octet wildcards, last-octet short ranges, or full-address ranges.
   * Whitespace around each comma-delimited entry is ignored. DNS names, IPv6 addresses, embedded
   * whitespace, leading-zero octets, empty entries, and reversed ranges are rejected.
   *
   * <p>A null or blank value produces an unrestricted policy. Every entry is validated even when
   * {@code *} is present; a valid {@code *} makes the resulting policy unrestricted.
   *
   * @param csv the comma-separated entries, or {@code null} for unrestricted access.
   * @return an immutable policy compiled from the supplied entries.
   * @throws IllegalArgumentException if a nonblank value exceeds 4,096 characters or 256 entries,
   *     or an entry is empty, malformed, or unsupported.
   */
  public static AllowedIpAddressFilter parse(String csv) {
    if (csv == null || csv.isBlank()) {
      return ALLOW_ALL;
    }
    if (csv.length() > MAX_LENGTH) {
      throw new IllegalArgumentException(
          "allowed IP addresses must not exceed " + MAX_LENGTH + " characters");
    }

    String[] entries = csv.split(",", -1);
    if (entries.length > MAX_ENTRIES) {
      throw new IllegalArgumentException(
          "allowed IP addresses must not contain more than " + MAX_ENTRIES + " entries");
    }

    boolean matchAll = false;
    var ranges = new ArrayList<AddressRange>(entries.length);

    for (String unstrippedEntry : entries) {
      String entry = unstrippedEntry.strip();
      validateCharacters(entry);

      if (entry.equals("*")) {
        matchAll = true;
      } else {
        ranges.add(parseEntry(entry));
      }
    }

    return matchAll ? ALLOW_ALL : new AllowedIpAddressFilter(false, ranges);
  }

  /**
   * Indicates whether this policy disables source-address filtering entirely.
   *
   * <p>This is {@code true} for a null or blank source value or a valid list containing {@code *}.
   * An IPv4-wide rule such as {@code 0.0.0.0/0} remains limited to IPv4 peers.
   *
   * @return {@code true} when callers can omit connection filtering.
   */
  public boolean allowsAll() {
    return allowsAll;
  }

  /**
   * Tests an already-resolved peer address against this policy.
   *
   * <p>An unrestricted policy accepts any value. A restrictive policy rejects {@code null} and
   * non-IPv4 addresses.
   *
   * @param address the resolved peer address, or {@code null} when unavailable.
   * @return {@code true} when the address is unrestricted or matches at least one entry.
   */
  public boolean isAllowed(InetAddress address) {
    if (allowsAll) {
      return true;
    }
    if (address == null) {
      return false;
    }

    byte[] bytes = address.getAddress();
    if (bytes.length != 4) {
      return false;
    }

    long value = toUnsignedLong(bytes);
    for (AddressRange range : ranges) {
      if (range.contains(value)) {
        return true;
      }
    }
    return false;
  }

  private static void validateCharacters(String entry) {
    if (entry.isEmpty()) {
      throw invalidEntry(entry, "entry is empty");
    }

    for (int i = 0; i < entry.length(); i++) {
      char c = entry.charAt(i);
      if (!((c >= '0' && c <= '9') || c == '.' || c == '/' || c == '*' || c == '-')) {
        throw invalidEntry(entry, "unsupported character U+%04X".formatted((int) c));
      }
    }
  }

  private static AddressRange parseEntry(String entry) {
    try {
      if (entry.indexOf('/') >= 0) {
        return parseCidr(entry);
      }
      if (entry.indexOf('-') >= 0) {
        return parseRange(entry);
      }
      if (entry.indexOf('*') >= 0) {
        return parseWildcard(entry);
      }

      long address = toUnsignedLong(parseIpv4(entry));
      return new AddressRange(address, address);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("invalid allowed IP address entry: " + entry, e);
    }
  }

  private static AddressRange parseCidr(String entry) {
    String[] parts = entry.split("/", -1);
    if (parts.length != 2) {
      throw invalidEntry(entry);
    }

    byte[] address = parseIpv4(parts[0]);
    int prefixLength = parseDecimal(parts[1], 32);
    long addressValue = toUnsignedLong(address);
    long hostMask = prefixLength == 32 ? 0 : (1L << (32 - prefixLength)) - 1;

    if ((addressValue & hostMask) != 0) {
      throw invalidEntry(entry);
    }

    return new AddressRange(addressValue, addressValue | hostMask);
  }

  private static AddressRange parseWildcard(String entry) {
    String[] parts = entry.split("\\.", -1);
    if (parts.length < 2 || parts.length > 4 || parts[0].equals("*")) {
      throw invalidEntry(entry);
    }

    byte[] low = MIN_ADDRESS.clone();
    byte[] high = MAX_ADDRESS.clone();
    boolean wildcardSeen = false;

    for (int i = 0; i < parts.length; i++) {
      if (parts[i].equals("*")) {
        wildcardSeen = true;
      } else {
        if (wildcardSeen) {
          throw invalidEntry(entry);
        }
        int octet = parseDecimal(parts[i], 255);
        low[i] = (byte) octet;
        high[i] = (byte) octet;
      }
    }

    if (!wildcardSeen) {
      throw invalidEntry(entry);
    }

    return new AddressRange(toUnsignedLong(low), toUnsignedLong(high));
  }

  private static AddressRange parseRange(String entry) {
    String[] parts = entry.split("-", -1);
    if (parts.length != 2) {
      throw invalidEntry(entry);
    }

    byte[] low = parseIpv4(parts[0]);
    byte[] high;
    if (parts[1].indexOf('.') >= 0) {
      high = parseIpv4(parts[1]);
    } else {
      high = low.clone();
      high[3] = (byte) parseDecimal(parts[1], 255);
    }

    long lowValue = toUnsignedLong(low);
    long highValue = toUnsignedLong(high);
    if (lowValue > highValue) {
      throw invalidEntry(entry);
    }

    return new AddressRange(lowValue, highValue);
  }

  private static byte[] parseIpv4(String value) {
    String[] octets = value.split("\\.", -1);
    if (octets.length != 4) {
      throw invalidEntry(value);
    }

    byte[] address = new byte[4];
    for (int i = 0; i < octets.length; i++) {
      address[i] = (byte) parseDecimal(octets[i], 255);
    }
    return address;
  }

  private static int parseDecimal(String value, int maximum) {
    if (value.isEmpty() || (value.length() > 1 && value.charAt(0) == '0')) {
      throw invalidEntry(value);
    }

    int result = 0;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c < '0' || c > '9') {
        throw invalidEntry(value);
      }
      int digit = c - '0';
      if (result > (maximum - digit) / 10) {
        throw invalidEntry(value);
      }
      result = result * 10 + digit;
    }
    return result;
  }

  private static long toUnsignedLong(byte[] address) {
    long result = 0;
    for (byte octet : address) {
      result = (result << 8) | (octet & 0xffL);
    }
    return result;
  }

  private static IllegalArgumentException invalidEntry(String entry) {
    return new IllegalArgumentException("invalid allowed IP address entry: " + entry);
  }

  private static IllegalArgumentException invalidEntry(String entry, String reason) {
    return new IllegalArgumentException(
        "invalid allowed IP address entry: " + entry + " (" + reason + ")");
  }

  private record AddressRange(long low, long high) {

    private boolean contains(long address) {
      return low <= address && address <= high;
    }
  }
}
