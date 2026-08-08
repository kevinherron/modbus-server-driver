package com.kevinherron.ignition.modbus.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.stream.Stream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class AllowedIpAddressFilterTest {

  @Nested
  class Parsing {

    // Every supported entry form must parse successfully.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "*",
          "0.0.0.0",
          "255.255.255.255",
          "192.168.1.50",
          "0.0.0.0/0",
          "192.168.1.0/24",
          "192.168.1.50/32",
          "192.168.1.*",
          "192.168.*",
          "192.168.*.*",
          "10.*",
          "192.168.1.10-20",
          "192.168.1.10-10",
          "192.168.1.10-192.168.2.5",
          "192.168.1.10-192.168.1.10"
        })
    void parsesEveryAcceptedEntryForm(String entry) {
      assertDoesNotThrow(() -> AllowedIpAddressFilter.parse(entry));
    }

    // Whitespace around CSV entries is ignored; whitespace inside an entry remains invalid.
    @Test
    void stripsWhitespaceAroundEntries() throws Exception {
      var filter = AllowedIpAddressFilter.parse(" 192.168.1.0/24,\t10.0.5.7 \n");

      assertTrue(filter.isAllowed(address("192.168.1.255")));
      assertTrue(filter.isAllowed(address("10.0.5.7")));
      assertFalse(filter.isAllowed(address("10.0.5.8")));
    }

    // Malformed or ambiguous entries are rejected instead of becoming broader rules.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "localhost",
          "example.com",
          "!192.168.1.50",
          "192\\.168\\.1\\..*",
          "192.168.1.?",
          "192.168. 1.1",
          "192.168.\t1.1",
          "192.168.1.\u00a01",
          "192.168.1.\u200b1",
          "192.168.*.5",
          "192.*.1.*",
          "*.168.1.1",
          "192.168.1.1*",
          "*.*.*.*",
          "192.168.1",
          "3232235777",
          "1.2.3.4.5",
          "192.168.001.1",
          "00.0.0.0",
          "256.0.0.1",
          "192.168.1.5/24",
          "1.2.3.4/0",
          "192.168.1.0/-1",
          "192.168.1.0/33",
          "192.168.1.0/24/1",
          "::1",
          "2001:db8::/32",
          "::ffff:192.168.1.50",
          "192.168.1.20-10",
          "192.168.2.5-192.168.1.10",
          "192.168.1.10-020",
          "192.168.001.10-192.168.1.20",
          "192.168.1.10-256",
          "192.168.1.10-192.168.1",
          "192.168.1.10-20-30",
          ",192.168.1.1",
          "192.168.1.1,,10.0.0.1",
          "192.168.1.1,"
        })
    void rejectsUnsupportedOrAmbiguousEntries(String entry) {
      assertThrows(IllegalArgumentException.class, () -> AllowedIpAddressFilter.parse(entry));
    }

    // An error must identify the bad token in a long list so the saved value can be repaired.
    @Test
    void invalidEntryMessageNamesTheOffendingToken() {
      var exception =
          assertThrows(
              IllegalArgumentException.class,
              () -> AllowedIpAddressFilter.parse("10.0.0.1, bad-host,192.168.1.0/24"));

      assertTrue(exception.getMessage().contains("bad-host"));
      assertTrue(exception.getMessage().contains("unsupported character"));
    }

    // An allow-all token must not hide a malformed token later in the same saved value.
    @Test
    void validatesEveryEntryEvenWhenListContainsAsterisk() {
      assertThrows(
          IllegalArgumentException.class, () -> AllowedIpAddressFilter.parse("*,localhost"));
    }

    // Duplicate and overlapping entries are harmless for an allow-only list and remain valid.
    @Test
    void acceptsDuplicateAndOverlappingEntries() throws Exception {
      var filter =
          AllowedIpAddressFilter.parse("192.168.1.0/24,192.168.1.42,192.168.1.0/25,192.168.1.42");

      assertTrue(filter.isAllowed(address("192.168.1.42")));
      assertTrue(filter.isAllowed(address("192.168.1.200")));
      assertFalse(filter.isAllowed(address("192.168.2.1")));
    }
  }

  @Nested
  class UnrestrictedBehavior {

    // Null and blank configuration values remain unrestricted for backward compatibility.
    @ParameterizedTest(name = "{0}")
    @MethodSource("unrestrictedValues")
    void nullBlankAndAsteriskAllowEveryAddress(String description, String csv) throws Exception {
      var filter = AllowedIpAddressFilter.parse(csv);

      assertTrue(filter.allowsAll());
      assertTrue(filter.isAllowed(address("203.0.113.9")));
      assertTrue(filter.isAllowed(ipv6Address()));
    }

    static Stream<Arguments> unrestrictedValues() {
      return Stream.of(
          Arguments.of("null", (String) null),
          Arguments.of("empty", ""),
          Arguments.of("spaces", "   "),
          Arguments.of("blank controls", "\t\r\n"),
          Arguments.of("asterisk", "*"),
          Arguments.of("padded asterisk", "  *  "));
    }

    // An asterisk in any list position dominates the other rules and preserves IPv6 access too.
    @Test
    void asteriskInMixedListAllowsAll() throws Exception {
      var filter = AllowedIpAddressFilter.parse("192.168.1.50, *,10.0.0.0/8");

      assertTrue(filter.allowsAll());
      assertTrue(filter.isAllowed(address("198.51.100.7")));
      assertTrue(filter.isAllowed(ipv6Address()));
    }

    // A CIDR /0 allows every IPv4 address but still rejects IPv6; only "*" disables filtering.
    @Test
    void ipv4DefaultRouteDoesNotAllowIpv6() throws Exception {
      var filter = AllowedIpAddressFilter.parse("0.0.0.0/0");

      assertFalse(filter.allowsAll());
      assertTrue(filter.isAllowed(address("0.0.0.0")));
      assertTrue(filter.isAllowed(address("255.255.255.255")));
      assertFalse(filter.isAllowed(ipv6Address()));
    }
  }

  @Nested
  class Matching {

    // Boundary cases ensure a CIDR includes exactly its declared network.
    @ParameterizedTest(name = "{0}")
    @MethodSource("cidrMatchCases")
    void matchesCidrBoundaries(String description, String remoteAddress, boolean expected)
        throws Exception {
      var filter = AllowedIpAddressFilter.parse("192.168.1.0/24");

      assertEquals(expected, filter.isAllowed(address(remoteAddress)));
    }

    static Stream<Arguments> cidrMatchCases() {
      return Stream.of(
          Arguments.of("CIDR just below", "192.168.0.255", false),
          Arguments.of("CIDR lower boundary", "192.168.1.0", true),
          Arguments.of("CIDR interior", "192.168.1.128", true),
          Arguments.of("CIDR upper boundary", "192.168.1.255", true),
          Arguments.of("CIDR just above", "192.168.2.0", false));
    }

    // Wildcard spellings must match exactly the octet-aligned block they express.
    @ParameterizedTest(name = "{0}")
    @MethodSource("wildcardMatchCases")
    void matchesWildcardBoundaries(
        String description, String rule, String remoteAddress, boolean expected) throws Exception {
      var filter = AllowedIpAddressFilter.parse(rule);

      assertEquals(expected, filter.isAllowed(address(remoteAddress)));
    }

    static Stream<Arguments> wildcardMatchCases() {
      return Stream.of(
          Arguments.of("one wildcard just below", "192.168.1.*", "192.168.0.255", false),
          Arguments.of("one wildcard lower boundary", "192.168.1.*", "192.168.1.0", true),
          Arguments.of("one wildcard upper boundary", "192.168.1.*", "192.168.1.255", true),
          Arguments.of("one wildcard just above", "192.168.1.*", "192.168.2.0", false),
          Arguments.of("short /16 lower boundary", "192.168.*", "192.168.0.0", true),
          Arguments.of("short /16 upper boundary", "192.168.*", "192.168.255.255", true),
          Arguments.of("expanded /16 lower boundary", "192.168.*.*", "192.168.0.0", true),
          Arguments.of("expanded /16 upper boundary", "192.168.*.*", "192.168.255.255", true),
          Arguments.of("/16 just above", "192.168.*", "192.169.0.0", false),
          Arguments.of("/8 just below", "10.*", "9.255.255.255", false),
          Arguments.of("/8 lower boundary", "10.*", "10.0.0.0", true),
          Arguments.of("/8 upper boundary", "10.*", "10.255.255.255", true),
          Arguments.of("/8 just above", "10.*", "11.0.0.0", false));
    }

    // A short range changes only the final octet and includes both endpoints.
    @ParameterizedTest(name = "{0}")
    @MethodSource("shortRangeMatchCases")
    void matchesShortRangeBoundaries(String description, String remoteAddress, boolean expected)
        throws Exception {
      var filter = AllowedIpAddressFilter.parse("192.168.1.10-20");

      assertEquals(expected, filter.isAllowed(address(remoteAddress)));
    }

    static Stream<Arguments> shortRangeMatchCases() {
      return Stream.of(
          Arguments.of("short range just below", "192.168.1.9", false),
          Arguments.of("short range lower boundary", "192.168.1.10", true),
          Arguments.of("short range interior", "192.168.1.15", true),
          Arguments.of("short range upper boundary", "192.168.1.20", true),
          Arguments.of("short range just above", "192.168.1.21", false));
    }

    // A full range compares all four octets and can cross a subnet boundary.
    @ParameterizedTest(name = "{0}")
    @MethodSource("fullRangeMatchCases")
    void matchesFullRangeBoundaries(String description, String remoteAddress, boolean expected)
        throws Exception {
      var filter = AllowedIpAddressFilter.parse("192.168.1.10-192.168.2.5");

      assertEquals(expected, filter.isAllowed(address(remoteAddress)));
    }

    static Stream<Arguments> fullRangeMatchCases() {
      return Stream.of(
          Arguments.of("full range just below", "192.168.1.9", false),
          Arguments.of("full range lower boundary", "192.168.1.10", true),
          Arguments.of("full range interior", "192.168.2.0", true),
          Arguments.of("full range upper boundary", "192.168.2.5", true),
          Arguments.of("full range just above", "192.168.2.6", false));
    }

    // A comma-separated list is an order-independent OR across dissimilar entry forms.
    @ParameterizedTest(name = "{0}")
    @MethodSource("mixedListMatchCases")
    void matchesAnyEntryInMixedList(String description, String remoteAddress, boolean expected)
        throws Exception {
      String rules = "203.0.113.7,192.168.1.0/24,10.*,172.16.0.10-20,198.51.100.10-198.51.101.5";
      String reversedRules =
          "198.51.100.10-198.51.101.5,172.16.0.10-20,10.*,192.168.1.0/24,203.0.113.7";

      assertEquals(expected, AllowedIpAddressFilter.parse(rules).isAllowed(address(remoteAddress)));
      assertEquals(
          expected, AllowedIpAddressFilter.parse(reversedRules).isAllowed(address(remoteAddress)));
    }

    static Stream<Arguments> mixedListMatchCases() {
      return Stream.of(
          Arguments.of("mixed literal", "203.0.113.7", true),
          Arguments.of("mixed CIDR", "192.168.1.200", true),
          Arguments.of("mixed wildcard", "10.99.88.77", true),
          Arguments.of("mixed short range", "172.16.0.15", true),
          Arguments.of("mixed full range", "198.51.101.1", true),
          Arguments.of("mixed miss", "203.0.113.8", false));
    }

    // Restrictive IPv4 allow lists reject IPv6 peers.
    @Test
    void explicitIpv4ListRejectsIpv6Remote() throws Exception {
      var filter = AllowedIpAddressFilter.parse("127.0.0.1,192.168.1.0/24");

      assertFalse(filter.allowsAll());
      assertFalse(filter.isAllowed(ipv6Address()));
    }

    // An IPv4-mapped IPv6 address remains outside an IPv4-only allow list.
    @Test
    void explicitIpv4ListRejectsSixteenByteIpv4MappedRemote() throws Exception {
      byte[] mappedAddress =
          new byte[] {
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, (byte) 0xff, (byte) 0xff, (byte) 192, (byte) 168, 1, 50
          };
      InetAddress remote = Inet6Address.getByAddress(null, mappedAddress, 0);

      assertEquals(16, remote.getAddress().length);
      assertFalse(AllowedIpAddressFilter.parse("192.168.1.0/24").isAllowed(remote));
    }
  }

  @Nested
  class Limits {

    // The documented 256-entry maximum remains usable at its boundary.
    @Test
    void acceptsExactly256Entries() {
      String csv = repeatedEntries(256, "0.0.0.0");

      assertDoesNotThrow(() -> AllowedIpAddressFilter.parse(csv));
    }

    // One more otherwise-valid entry exceeds the configuration limit.
    @Test
    void rejects257Entries() {
      String csv = repeatedEntries(257, "0.0.0.0");

      assertThrows(IllegalArgumentException.class, () -> AllowedIpAddressFilter.parse(csv));
    }

    // A maximum-length configuration remains valid at exactly 4,096 characters.
    @Test
    void acceptsExactly4096Characters() {
      String csv = maxLengthEntries("192.168.100.0/24");

      assertEquals(4096, csv.length());
      assertDoesNotThrow(() -> AllowedIpAddressFilter.parse(csv));
    }

    // Runtime parsing enforces the same 4,096-character limit as configuration validation.
    @Test
    void rejects4097Characters() {
      String csv = maxLengthEntries("10.0.0.1-10.0.0.1");

      assertEquals(4097, csv.length());
      assertThrows(IllegalArgumentException.class, () -> AllowedIpAddressFilter.parse(csv));
    }
  }

  @Nested
  class ReadmeExamples {

    // These cases duplicate README examples so parser changes that invalidate published syntax fail
    // the test.
    @ParameterizedTest(name = "{0}")
    @ValueSource(
        strings = {
          "*",
          "192.168.1.50",
          "192.168.1.0/24",
          "192.168.1.*",
          "192.168.*",
          "192.168.*.*",
          "10.*",
          "192.168.1.10-20",
          "192.168.1.10-192.168.2.5"
        })
    void parsesEveryReadmeAllowListExample(String entry) {
      assertDoesNotThrow(() -> AllowedIpAddressFilter.parse(entry));
    }
  }

  private static String repeatedEntries(int count, String entry) {
    return String.join(",", Collections.nCopies(count, entry));
  }

  private static String maxLengthEntries(String firstEntry) {
    return firstEntry + "," + repeatedEntries(255, "255.255.255.255");
  }

  private static InetAddress address(String value) throws UnknownHostException {
    // Test inputs are numeric literals; production address parsing never performs name resolution.
    return InetAddress.getByName(value);
  }

  private static InetAddress ipv6Address() throws UnknownHostException {
    return InetAddress.getByAddress(new byte[16]);
  }
}
