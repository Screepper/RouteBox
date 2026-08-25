package com.routebox.util;

import com.routebox.entity.index.BgpLog;
import com.routebox.entity.index.auxiliary.BgpNeighborRouteMap;
import com.routebox.entity.index.auxiliary.BgpNetworkDeclaration;
import com.routebox.entity.index.auxiliary.BgpRouteEntry;
import com.routebox.entity.index.auxiliary.BgpRouteMap;

import java.math.BigInteger;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @Description BGP Info Parse Tool
 * @Date 2025/7/19
**/
public class IpBgpUtil {

    public enum IPOptions {
        IPv4, // Default
        IPv6,
        IPv4AndIPv6
    }

    private static final Random random = new Random();
    // IPv4 Host
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(0|(1\\d{0,2}|2[0-4]\\d|25[0-5]|[3-9]\\d?))\\.(0|(1\\d{0,2}|2[0-4]\\d|25[0-5]|[3-9]\\d?))\\.(0|(1\\d{0,2}|2[0-4]\\d|25[0-5]|[3-9]\\d?))\\.(0|(1\\d{0,2}|2[0-4]\\d|25[0-5]|[3-9]\\d?))$");
    // IPv6 Host
    private static final Pattern IPV6_PATTERN = Pattern.compile(
            "^(?:(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,7}:|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +  //
                    "(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|" +
                    "[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}|" +
                    ":(?::[0-9a-fA-F]{1,4}){1,7}|::)$"
    );
    // IPv4 CIDR
    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile("^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)/([0-9]|[1-2][0-9]|3[0-2])$");
    // IPv6 CIDR
    private static final Pattern IPV6_CIDR_PATTERN = Pattern.compile(
            "^(?:(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,7}:|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|" +
                    "(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|" +
                    "[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}|" +
                    ":(?::[0-9a-fA-F]{1,4}){1,7}|::)/([0-9]|[1-9][0-9]|1[0-1][0-9]|12[0-8])$"
    );
    // Route Map
    private static final Pattern ROUTE_MAP_PATTERN = Pattern.compile("^route-map\\s+([\\w-]+)\\s+(permit|deny)\\s+(\\d+)$");
    // Inbound-Outbound Route Map
    private static final Pattern IN_OUT_ROUTE_MAP_PATTERN = Pattern.compile("neighbor\\s+(\\S+)\\s+route-map\\s+(\\S+)\\s+(in|out)");
    // Router Advertisement
    private static final Pattern NETWORK_PATTERN = Pattern.compile("network\\s+(\\S+)(?:\\s+mask\\s+(\\S+))?(?:\\s+route-map\\s+(\\S+))?");

    // ==================== Judgement Methods ====================

    /**
     * @Description Check if ip in cidr
     * @Date 2025/7/20
     **/
    public static boolean checkHostIpInCidr(String hostIp, String cidr) {
        if (checkIPv4Host(hostIp) && checkIpv4Cidr(cidr)) return checkIPv4HostIpInCidr(hostIp, cidr);
        else if (checkIPv6Host(hostIp) && checkIpv6Cidr(cidr)) return checkIPv6HostIpInCidr(hostIp, cidr);

        return false;
    }

    /**
     * @Description Check if ip is ipv4 host
     * @Date 2025/7/20
     **/
    public static boolean checkIPv4Host(String ip) {
        return ip != null && !ip.isEmpty() && IPV4_PATTERN.matcher(ip).matches();
    }

    /**
     * @Description Check if cidr is ipv4 cidr
     * @Date 2025/7/20
     **/
    public static boolean checkIpv4Cidr(String cidr) {
        return cidr != null && !cidr.isEmpty() && IPV4_CIDR_PATTERN.matcher(cidr).matches();
    }

    /**
     * @Description Check if ip is ipv6 host
     * @Date 2025/7/20
     **/
    public static boolean checkIPv6Host(String ip) {
        return ip != null && !ip.isEmpty() && IPV6_PATTERN.matcher(ip).matches();
    }

    /**
     * @Description Check if cidr is ipv6 cidr
     * @Date 2025/7/20
     **/
    public static boolean checkIpv6Cidr(String cidr) {
        return cidr != null && !cidr.isEmpty() && IPV6_CIDR_PATTERN.matcher(cidr).matches();
    }

    /**
     * @Description Check if ipv4 host in cidr
     * @Date 2025/7/20
     **/
    private static boolean checkIPv4HostIpInCidr(String ip, String cidr) {
        String[] cidrParts = cidr.split("/");
        String networkAddress = cidrParts[0];
        // Get CIDR prefix length
        int prefixLength = Integer.parseInt(cidrParts[1]);
        // Calculate mask
        long mask = prefixLength > 0 ? (0xFFFFFFFFL << (32 - prefixLength)) : 0;

        // Determine whether the 2 IPs(Long) after applying the mask are completely identical
        return (ipv4ToLong(ip) & mask) == (ipv4ToLong(networkAddress) & mask);
    }


    /**
     * @Description Check if ipv6 host in cidr
     * @Date 2025/7/20
     **/
    private static boolean checkIPv6HostIpInCidr(String ip, String cidr) {
        // Resolve CIDR to obtain network address and mask length
        String[] cidrParts = cidr.split("/");
        String networkAddress = cidrParts[0];

        int prefixLength = Integer.parseInt(cidrParts[1]);
        try {
            // Calculate the mask, with high prefix Length bits set to 1 and low 128 prefix Length bits set to 0
            BigInteger mask = prefixLength == 0 ? BigInteger.ZERO :
                    (prefixLength == 128 ? new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF", 16) :
                            BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE).shiftLeft(128 - prefixLength));

            // Convert the hostIp and network address to a 128 bit BigInteger and compare the network addresses
            return ipv6ToBigInt(ip).and(mask).equals(ipv6ToBigInt(networkAddress).and(mask));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * @Description Check if ipv4 host is private
     * @Date 2025/7/20
     **/
    private static boolean isIPv4PrivateAddress(int p1, int p2) {
        if (p1 == 10) return true;
        if (p1 == 172 && p2 >= 16 && p2 <= 31) return true;

        return p1 == 192 && p2 == 168;
    }

    /**
     * @Description Check if ipv6 host is private
     * @Date 2025/7/20
     **/
    private static boolean isIPv6PrivateAddress(byte[] bytes) {
        // Unique local address (ULA) fc00::/7 (The first 7 digits are 1111110)
        if ((bytes[0] & 0xFE) == 0xFC) return true;

        // Link local address (LLA) fe80::/10 (The first 10 are 1111111010)
        return (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xC0) == 0x80;
    }

    // ==================== Generation Methods ====================

    /**
     * @Description Generate random IP prefixes
     * @Date 2025/7/20
     **/
    public static List<String> generateRandomIpPrefixes(int num, IPOptions... ipOptions) {
        IPOptions ipOption = ipOptions.length == 0 ? IPOptions.IPv4 : ipOptions[0];
        List<String> prefixes = new ArrayList<>();

        for (int i = 0; i < num; i++) {
            String ipPrefix = generateRandomIpPrefix(ipOption);

            while (ipPrefix == null || prefixes.contains(ipPrefix)) {
                ipPrefix = generateRandomIpPrefix(ipOption);
            }

            prefixes.add(ipPrefix);
        }

        return prefixes;
    }

    /**
     * @Description Generate hostIp corresponding to IP prefix
     * @Date 2025/7/20
     **/
    public static String generateHostIp(String ip) {
        if (checkIpv4Cidr(ip)) return generateIPv4HostIp(ip);
        else if (checkIpv6Cidr(ip)) return generateIPv6HostIp(ip);

        return ip;
    }

    /**
     * @Description Generate hostIp corresponding to IP prefix batch
     * @Date 2025/7/20
     **/
    public static List<String> generateHostIps(List<String> ips) {
        if (ips == null || ips.isEmpty()) return null;

        return ips.stream().map(IpBgpUtil::generateHostIp).toList();
    }

    /**
     * @Description Generate a random IP prefix of the specified type
     * @Date 2025/7/20
     **/
    private static String generateRandomIpPrefix(IPOptions ipOption) {
        if (ipOption == IPOptions.IPv4) return generateRandomIPv4Prefix();
        else if (ipOption == IPOptions.IPv6) return generateRandomIPv6Prefix();

        return random.nextBoolean() ? generateRandomIPv4Prefix() : generateRandomIPv6Prefix();
    }

    /**
     * @Description Generate random ipv4 prefix
     * @Date 2025/7/20
     **/
    private static String generateRandomIPv4Prefix() {
        int part1 = random.nextInt(256);
        int part2 = random.nextInt(256);
        int part3 = random.nextInt(256);
        int part4 = random.nextInt(256);

        // Randomly generate subnet masks (12-30)
        int subnetMask = random.nextInt(19) + 12;
        // Calculate the IP length integer and convert 4 segments into 32-bit integers
        long ipLong = ((long) part1 << 24) | ((long) part2 << 16) | ((long) part3 << 8) | part4;
        // Apply subnet mask, with high subnetMask bits set to 1 and low 32 subnetMask bits set to 0
        long mask = (0xFFFFFFFFL << (32 - subnetMask)) & 0xFFFFFFFFL;
        // Ensure the host bit is reset to zero
        long networkLong = ipLong & mask;
        // Convert to IP address and verify private address
        String prefix = longToIPv4(networkLong) + "/" + subnetMask;

        return isIPv4PrivateAddress(part1, part2) ? null : prefix;
    }

    /**
     * @Description Generate random ipv6 prefix (2000::/3)
     * @Date 2025/7/20
     **/
    private static String generateRandomIPv6Prefix() {
        // Generate a 16 byte array (corresponding to 128 bits for ipv6)
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        // The first three digits of the ipv6 global unicast address (2000::/3) are 001
        bytes[0] = (byte) ((bytes[0] & 0x1F) | 0x20);
        // Mask length (32-96)
        int prefixLen = random.nextInt(65) + 32;
        // Apply mask
        int fullBytes = prefixLen / 8;
        int remainingBits = prefixLen % 8;

        // Clear the host section
        for (int i = fullBytes + 1; i < 16; i++) {
            bytes[i] = 0;
        }

        // If the mask is not a multiple of 8, apply a mask to the last partially covered byte to ensure that the host bit is reset to zero
        if (remainingBits > 0) {
            bytes[fullBytes] = (byte) (bytes[fullBytes] & (0xFF << (8 - remainingBits)));
        }
        String ipPrefix = bytes2CompressIPv6(bytes) + "/" + prefixLen;

        return isIPv6PrivateAddress(bytes) ? null : ipPrefix;
    }

    /**
     * @Description Generate ipv4 hostIp
     * @Date 2025/7/20
     **/
    private static String generateIPv4HostIp(String ip) {
        String[] parts = ip.split("/")[0].split("\\.");

        return parts[0] + "." + parts[1] + "." + parts[2] + ".1";
    }

    /**
     * @Description Generate ipv6 hostIp
     * @Date 2025/7/20
     **/
    private static String generateIPv6HostIp(String inIp) {
        String ip = inIp.split("/")[0];

        // Processing compression formats
        if (ip.contains("::")) {
            String[] sections = ip.split("::", -1);
            String left = sections[0];
            String right = sections.length > 1 ? sections[1] : "";
            int leftCount = left.isEmpty() ? 0 : left.split(":").length;
            int rightCount = right.isEmpty() ? 0 : right.split(":").length;
            int zeroCount = 8 - (leftCount + rightCount);

            StringBuilder fullIp = new StringBuilder();
            if (!left.isEmpty()) {
                fullIp.append(left).append(":");
            }

            fullIp.append("0:".repeat(Math.max(0, zeroCount)));
            if (!right.isEmpty()) {
                fullIp.append(right);
            }

            ip = fullIp.toString();
            if (ip.endsWith(":")) {
                ip = ip.substring(0, ip.length() - 1);
            }
        }

        // Ensure 8 segments
        String[] ipParts = ip.split(":");

        if (ipParts.length < 8) {
            List<String> partsList = new ArrayList<>(Arrays.asList(ipParts));

            while (partsList.size() < 8) {
                partsList.add("0");
            }

            ip = String.join(":", partsList);
        }

        // Set the last paragraph to 1
        ipParts = ip.split(":");
        ipParts[ipParts.length - 1] = "1";

        return String.join(":", ipParts);
    }

    // ==================== Update Methods ====================

    /**
     * @Description Randomly shorten the length of the IP prefix mask
     * @Date 2025/7/20
     **/
    public static String shortenIpPrefix(String ipPrefix) {
        if (checkIpv4Cidr(ipPrefix)) return shortenIPv4Prefix(ipPrefix);
        else if (checkIpv6Cidr(ipPrefix)) return shortenIPv6Prefix(ipPrefix);

        return ipPrefix;
    }

    /**
     * @Description Randomly increase the length of the IP prefix mask
     * @Date 2025/7/20
     **/
    public static String extendIpPrefix(String ipPrefix) {
        if (checkIpv4Cidr(ipPrefix)) return extendIPv4Prefix(ipPrefix);
        else if (checkIpv6Cidr(ipPrefix)) return extendIPv6Prefix(ipPrefix);

        return ipPrefix;
    }

    /**
     * @Description Shorten the length of the ipv4 mask
     * @Date 2025/7/20
     **/
    private static String shortenIPv4Prefix(String ipPrefix) {
        String[] parts = ipPrefix.split("/");
        String ip = parts[0];
        int prefixLen = Integer.parseInt(parts[1]);
        // Calculate the shortened mask length
        int newPrefixLen = prefixLen - random.nextInt(16) + 1;
        if (newPrefixLen < 12) {
            newPrefixLen = 12;
        }
        // Generate new mask
        long newMask = (0xFFFFFFFFL << (32 - newPrefixLen)) & 0xFFFFFFFFL;
        // Convert the original IP to a long integer and apply a new mask
        long adjustedIpLong = ipv4ToLong(ip) & newMask;

        // Convert to IP address
        return longToIPv4(adjustedIpLong) + "/" + newPrefixLen;
    }

    /**
     * @Description Increase the length of the ipv4 mask
     * @Date 2025/7/20
     **/
    private static String extendIPv4Prefix(String ipPrefix) {
        String[] parts = ipPrefix.split("/");
        int prefixLen = Integer.parseInt(parts[1]);
        int newPrefixLen = prefixLen + random.nextInt(8) + 1;

        if (newPrefixLen > 31) {
            newPrefixLen = 31;
        }

        return parts[0] + "/" + newPrefixLen;
    }

    /**
     * @Description Shorten the length of the ipv6 mask
     * @Date 2025/7/20
     **/
    private static String shortenIPv6Prefix(String ipPrefix) {
        String[] parts = ipPrefix.split("/");
        String ip = parts[0];
        int prefixLen = Integer.parseInt(parts[1]);

        // Calculate the shortened mask length
        int newPrefixLen = prefixLen - random.nextInt(32) + 1;
        if (newPrefixLen < 16) {
            newPrefixLen = 16;
        }
        // Expand the compressed IPv6 address into a complete format of 8 segments, and then convert it to a 128 bit BigInteger
        BigInteger ipBigInt = ipv6ToBigInt(expandIPv6(ip));
        // Generate a new mask with a high newInitialLen bit of 1
        BigInteger newMask = BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
                .shiftLeft(128 - newPrefixLen)
                .and(BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE));
        BigInteger adjustedIpBigInt = ipBigInt.and(newMask);

        // Convert to ipv6 address
        return bigIntToIPv6(adjustedIpBigInt) + "/" + newPrefixLen;
    }

    /**
     * @Description Increase the length of the ipv6 mask
     * @Date 2025/7/20
     **/
    private static String extendIPv6Prefix(String ipPrefix) {
        String[] parts = ipPrefix.split("/");
        int prefixLen = Integer.parseInt(parts[1]);
        int newPrefixLen = prefixLen + random.nextInt(32) + 1;
        if (newPrefixLen > 127) {
            newPrefixLen = 127;
        }

        return parts[0] + "/" + newPrefixLen;
    }


    // ==================== Conversion Methods ====================

    /**
     * @Description ipv4 address to integer conversion
     * @Date 2025/7/20
        **/
    private static long ipv4ToLong(String ip) {
        String[] octets = ip.split("\\.");
        long result = 0;

        // Move 4 8-bit segments to the left by 24, 16, 8, and 0 bits respectively, and add them up to obtain a 32-bit integer
        for (int i = 0; i < 4; i++) {
            result <<= 8;
            result |= Long.parseLong(octets[i]);
        }

        return result;
    }

    /**
     * @Description Long integers to ipv4 addresses
     * @Date 2025/7/20
     **/
    private static String longToIPv4(long ipLong) {
        // Extract 4 8-bit segments by right shift and AND operation (&0xFF)
        return ((ipLong >> 24) & 0xFF) + "." +
                ((ipLong >> 16) & 0xFF) + "." +
                ((ipLong >> 8) & 0xFF) + "." +
                (ipLong & 0xFF);
    }


    /**
     * @Description ipv6 Address to BigInteger Translation
     * @Date 2025/7/20
     **/
    private static BigInteger ipv6ToBigInt(String ip) {
        String fullIP = expandIPv6(ip);

        if (fullIP == null) return BigInteger.ZERO;

        BigInteger result = BigInteger.ZERO;

        // Move the 8 hexadecimal values to the left by 16 bits and accumulate them to obtain a 128 bit integer
        for (String part : fullIP.split(":")) {
            part = String.format("%4s", part).replace(' ', '0');
            result = result.shiftLeft(16).add(new BigInteger(part, 16));
        }

        return result;
    }

    /**
     * @Description BigInteger to ipv6 addresses
     * @Date 2025/7/20
     **/
    private static String bigIntToIPv6(BigInteger bigInt) {
        byte[] bytes = new byte[16];
        // Split BigInteger into a 16 byte array and convert it to compressed format using bytes2PressIPv6
        BigInteger mask = BigInteger.ONE.shiftLeft(8).subtract(BigInteger.ONE);

        for (int i = 15; i >= 0; i--) {
            bytes[i] = bigInt.and(mask).byteValue();
            bigInt = bigInt.shiftRight(8);
        }

        return bytes2CompressIPv6(bytes);
    }

    /**
     * @Description The extended compressed IPv6 address is in 8-segment format, processing :: compression symbol, filling in missing zero segments
     * @Date 2025/7/20
     **/
    private static String expandIPv6(String ip) {
        if (ip == null || ip.isEmpty()) return null;
        if (ip.equals("::")) return "0:0:0:0:0:0:0:0";

        String[] parts = ip.split(":", -1);
        List<String> segments = new ArrayList<>();
        int doubleColonIndex = -1;

        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                if (doubleColonIndex == -1) {
                    doubleColonIndex = i;
                } else {
                    segments.add("0");
                }
            } else {
                segments.add(part);
            }
        }

        // Fill 0
        int zeroCount = 8 - segments.size();
        if (doubleColonIndex != -1 && zeroCount > 0) {
            segments = getExpanded(segments, doubleColonIndex, zeroCount);
        }

        // Ensure 8 segments
        while (segments.size() < 8) {
            segments.add("0");
        }

        while (segments.size() > 8) {
            segments.removeLast();
        }

        return String.join(":", segments);
    }

    /**
     * @Description Expand the ipv6 address, fill in the missing zero segments
     * @Date 2025/11/24
     **/
    private static List<String> getExpanded(List<String> segments, int doubleColonIndex, int zeroCount) {
        List<String> expanded = new ArrayList<>();
        boolean inserted = false;

        for (String seg : segments) {
            expanded.add(seg);

            if (!inserted && expanded.size() == doubleColonIndex) {
                for (int i = 0; i < zeroCount; i++) {
                    expanded.add("0");
                }
                inserted = true;
            }
        }
        if (!inserted) {
            for (int i = 0; i < zeroCount; i++) {
                expanded.add("0");
            }
        }

        return expanded;
    }

    /**
     * @Description Convert byte array to compressed ipv6 address, search for the longest continuous zero segment, replace with :: (only once), shorten the representation
     * @Date 2025/7/20
     **/
    private static String bytes2CompressIPv6(byte[] bytes) {
        // Convert to 8-segment hexadecimal
        List<String> segmentList = new ArrayList<>();

        for (int i = 0; i < 16; i += 2) {
            int segment = ((bytes[i] & 0xFF) << 8) | (bytes[i + 1] & 0xFF);
            segmentList.add(Integer.toHexString(segment));
        }

        String[] segments = segmentList.toArray(new String[0]);

        // Find the longest continuous zero segment
        int maxStart = -1;
        int maxLength = 0;
        int currentStart = -1;
        int currentLength = 0;

        for (int i = 0; i < segments.length; i++) {
            if (segments[i].equals("0")) {
                if (currentStart == -1) {
                    currentStart = i;
                }

                currentLength++;

                if (currentLength > maxLength) {
                    maxLength = currentLength;
                    maxStart = currentStart;
                }
            } else {
                currentStart = -1;
                currentLength = 0;
            }
        }

        // Compress the longest zero segment
        if (maxLength >= 2) {
            StringBuilder result = compress0Segments(segments, maxStart, maxLength);

            return result.toString().replace("::::", "::").replace(":::", "::");
        }

        return String.join(":", segments);
    }

    /**
     * @Description Compress the ipv6 address, replace the longest continuous zero segment with ::, and remove extra ::
     * @Date 2025/11/24
     **/
    private static StringBuilder compress0Segments(String[] segments, int maxStart, int maxLength) {
        List<String> compressed = new ArrayList<>();

        for (int i = 0; i < segments.length; i++) {
            if (i == maxStart) {
                compressed.add("::");
                i += maxLength - 1;
            } else if (!(i > maxStart && i < maxStart + maxLength)) {
                compressed.add(segments[i]);
            }
        }

        // Processing continuous ::
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < compressed.size(); i++) {
            if (i > 0 && !compressed.get(i).startsWith("::") && !compressed.get(i - 1).endsWith("::")) {
                result.append(":");
            }

            result.append(compressed.get(i));
        }
        return result;
    }

    // ==================== Parse Methods ====================

    /**
     * @Description Parse BGP configuration file to obtain all declared IP prefixes
     * @Date 2025/7/20
     **/
    public static List<BgpNetworkDeclaration> parseCompleteNetworkIps(List<String> lines) {
        List<BgpNetworkDeclaration> declarations = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();

            if (!line.startsWith("network ")) continue;

            Matcher matcher = NETWORK_PATTERN.matcher(line);
            if (matcher.matches()) {
                String ipPrefix = matcher.group(1);
                String mask = matcher.group(2);
                String routeMapName = matcher.group(3);
                declarations.add(new BgpNetworkDeclaration(ipPrefix, routeMapName, mask));
            }
        }

        return declarations;
    }

    /**
     * @Description Parse BGP configuration file to obtain all declared IP prefixes
     * @Date 2025/7/20
     **/
    public static List<String> parseNetworkIps(List<String> lines) {
        // return parseCompleteNetworkIps(lines).stream().map(BgpNetworkDeclaration::getIpPrefix).toList();
        // Only ipv4 is obtained
        return parseCompleteNetworkIps(lines).stream().map(BgpNetworkDeclaration::getIpPrefix).filter(IpBgpUtil::checkIpv4Cidr).toList();
    }

    /**
     * @Description Parse BGP configuration to obtain all concerned routing entries. If null, obtain all routing entries
     * @Date 2025/7/20
     **/
    public static List<BgpRouteEntry> parseRouteEntries(List<String> lines, ArrayList<String> focused) {
        final String[] currentIpPrefix = {null};

        return lines.stream()
                .map(String::trim)
                // Filter non routing entry rows (must start with * and end with i)
                .filter(line -> line.startsWith("*") && line.endsWith("i"))
                .map(line -> {
                    BgpRouteEntry entry = new BgpRouteEntry();
                    entry.setBest(line.startsWith("*>"));

                    // Split line content by blank space
                    String[] parts = line.split("\\s+");

                    if (parts[1].contains("/")) {
                        currentIpPrefix[0] = parts[1];
                    }

                    if (currentIpPrefix[0] == null) return null;

                    if (focused != null && !focused.contains(currentIpPrefix[0])) return null;

                    if (!checkIpv4Cidr(currentIpPrefix[0]) && !checkIpv6Cidr(currentIpPrefix[0])) return null;

                    entry.setIpPrefix(currentIpPrefix[0]);
                    entry.setNextHop(parts[1].contains("/") ? parts[2] : parts[1]);

                    int endIndex = line.length() - 1;
                    while (endIndex > 0 && !(line.charAt(endIndex) == ' ' && line.charAt(endIndex - 1) == ' ')) {
                        endIndex--;
                    }

                    String[] splits = line.substring(endIndex).trim().split(" ", 2);
                    try {
                        entry.setWeight(Integer.parseInt(splits[0]));
                        entry.setPath(splits[1]);
                    } catch (Exception e) {
                        return null;
                    }

                    return entry;
                }).filter(Objects::nonNull)
                .toList();
    }

    /**
     * @Description Parse the inbound and outbound routing mapping rules in BGP configuration
     * @Date 2025/7/20
     **/
    public static List<BgpNeighborRouteMap> parseNeighborRouteMap(List<String> lines) {
        List<BgpNeighborRouteMap> neighborRouteMaps = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();

            if (line.isEmpty()) continue;

            Matcher matcher = IN_OUT_ROUTE_MAP_PATTERN.matcher(line);

            if (matcher.matches()) {
                String neighborIp = matcher.group(1);
                String routeMapName = matcher.group(2);
                String direction = matcher.group(3);
                neighborRouteMaps.add(new BgpNeighborRouteMap(neighborIp, routeMapName, direction));
            }
        }

        return neighborRouteMaps;
    }

    /**
     * @Description Parse routing mapping rules
     * @Date 2025/7/25
     **/
    public static List<BgpRouteMap> parseRouteMaps(List<String> lines) {
        List<BgpRouteMap> routeMaps = new ArrayList<>();
        BgpRouteMap currentRouteMap = null;

        for (String line : lines) {
            line = line.trim();

            if (line.startsWith("!") || line.startsWith("exit")) {
                if (currentRouteMap != null) {
                    routeMaps.add(currentRouteMap);
                    currentRouteMap = null;
                }

                continue;
            }

            Matcher matcher = ROUTE_MAP_PATTERN.matcher(line);

            if (matcher.matches()) {
                if (currentRouteMap != null) {
                    routeMaps.add(currentRouteMap);
                }

                String name = matcher.group(1);
                String action = matcher.group(2);
                int sequenceNumber = Integer.parseInt(matcher.group(3));
                currentRouteMap = new BgpRouteMap(name, action, sequenceNumber);

                continue;
            }

            if (currentRouteMap != null) {
                if (line.startsWith("match ")) {
                    currentRouteMap.addMatchStatement(line);
                } else if (line.startsWith("set ")) {
                    currentRouteMap.addSetStatement(line);
                } else {
                    currentRouteMap.addMatchStatement(line);
                }
            }
        }

        if (currentRouteMap != null) {
            routeMaps.add(currentRouteMap);
        }

        return routeMaps;
    }

    /**
     * @Description Parse the running configuration and fill in BgpLog
     * @Date 2025/7/30
     **/
    public static void parseRunningConfig(String[] lines, BgpLog bgpLog) {
        List<BgpNetworkDeclaration> declarations = new ArrayList<>();
        List<BgpNeighborRouteMap> neighborRouteMaps = new ArrayList<>();
        List<BgpRouteMap> routeMaps = new ArrayList<>();
        BgpRouteMap currentRouteMap = null;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            if (line.startsWith("!") || line.startsWith("exit")) {
                if (currentRouteMap != null) {
                    routeMaps.add(currentRouteMap);
                    currentRouteMap = null;
                }

                continue;
            }

            if (line.startsWith("network ")) {
                Matcher matcher = NETWORK_PATTERN.matcher(line);

                if (matcher.matches()) {
                    String ipPrefix = matcher.group(1);
                    String mask = matcher.group(2);
                    String routeMapName = matcher.group(3);
                    declarations.add(new BgpNetworkDeclaration(ipPrefix, routeMapName, mask));
                }
            } else if (line.startsWith("neighbor ")) {
                Matcher matcher = IN_OUT_ROUTE_MAP_PATTERN.matcher(line);

                if (matcher.matches()) {
                    String neighborIp = matcher.group(1);
                    String routeMapName = matcher.group(2);
                    String direction = matcher.group(3);
                    neighborRouteMaps.add(new BgpNeighborRouteMap(neighborIp, routeMapName, direction));
                }
            } else {
                Matcher matcher = ROUTE_MAP_PATTERN.matcher(line);

                if (matcher.matches()) {
                    if (currentRouteMap != null) {
                        routeMaps.add(currentRouteMap);
                    }

                    String name = matcher.group(1);
                    String action = matcher.group(2);
                    int sequenceNumber = Integer.parseInt(matcher.group(3));
                    currentRouteMap = new BgpRouteMap(name, action, sequenceNumber);

                    continue;
                }

                if (currentRouteMap != null) {
                    if (line.startsWith("match ")) {
                        currentRouteMap.addMatchStatement(line);
                    } else if (line.startsWith("set ")) {
                        currentRouteMap.addSetStatement(line);
                    } else {
                        currentRouteMap.addMatchStatement(line);
                    }
                }
            }
        }

        if (currentRouteMap != null) {
            routeMaps.add(currentRouteMap);
        }

        bgpLog.setNetworkIps(declarations);
        bgpLog.setNeighborRouteMaps(neighborRouteMaps);
        bgpLog.setRouteMaps(routeMaps);
    }


    /**
     * @Description Validation and Test
     * @Date 2025/7/30
     **/
    public static void main(String[] args) {
        testIPValidation();
        testPrefixGeneration();
        testPrefixOperations();
        testCIDRCheck();
        testBgpParsing();
        testHostIpGeneration();
    }

    private static void testIPValidation() {
        System.out.println("IPv4 Validation:");
        System.out.println("192.168.1.1 " + checkIPv4Host("192.168.1.1"));
        System.out.println("256.168.1.1 " + checkIPv4Host("256.168.1.1"));
        System.out.println("192.168.1/24 " + checkIpv4Cidr("192.168.1/24"));
        System.out.println("10.0.0.0/24 " + checkIpv4Cidr("10.0.0.0/24"));

        System.out.println("\nIPv6 Validation:");
        System.out.println("2001:db8::1 " + checkIPv6Host("2001:db8::1"));
        System.out.println("2001:db8:::1 " + checkIPv6Host("2001:db8:::1"));
        System.out.println("fe80::1 " + checkIPv6Host("fe80::1"));
        System.out.println("::1 " + checkIPv6Host("::1"));
        System.out.println("2001:0db8:85a3:0000:0000:8a2e:0370:7334 " + checkIPv6Host("2001:0db8:85a3:0000:0000:8a2e:0370:7334"));
        System.out.println("2001:db8::1/64 " + checkIpv6Cidr("2001:db8::1/64"));
        System.out.println("fe80::/10 " + checkIpv6Cidr("fe80::/10"));
        System.out.println("2001:db8::/129 " + checkIpv6Cidr("2001:db8::/129"));
    }

    private static void testPrefixGeneration() {
        System.out.println("IPv4 Prefixes:");
        List<String> ipv4Prefixes = generateRandomIpPrefixes(3, IPOptions.IPv4);
        ipv4Prefixes.forEach(System.out::println);

        System.out.println("\nIPv6 Prefixes:");
        List<String> ipv6Prefixes = generateRandomIpPrefixes(3, IPOptions.IPv6);
        ipv6Prefixes.forEach(System.out::println);

        System.out.println("\nMixed Prefixes:");
        List<String> mixedPrefixes = generateRandomIpPrefixes(3, IPOptions.IPv4AndIPv6);
        mixedPrefixes.forEach(System.out::println);
    }

    private static void testPrefixOperations() {
        String ipv4Prefix = "192.168.1.0/24";
        System.out.println("IPv4 Operations:");
        System.out.println("Original " + ipv4Prefix);
        System.out.println("Shortened " + shortenIpPrefix(ipv4Prefix));
        System.out.println("Extended " + extendIpPrefix(ipv4Prefix));
        System.out.println("HostIp " + generateHostIp(ipv4Prefix));

        String ipv6Prefix = "2001:db8:abcd:12::/64";
        System.out.println("\nIPv6 Operations:");
        System.out.println("Original " + ipv6Prefix);
        System.out.println("Shortened " + shortenIpPrefix(ipv6Prefix));
        System.out.println("Extended " + extendIpPrefix(ipv6Prefix));
        System.out.println("HostIp " + generateHostIp(ipv6Prefix));
    }

    private static void testCIDRCheck() {
        // True Cases
        System.out.println("IPv4 CIDR Check:");
        System.out.println("192.168.1.5 in 192.168.1.0/24 " +
                checkHostIpInCidr("192.168.1.5", "192.168.1.0/24"));
        System.out.println("192.168.2.5 in 192.168.1.0/24 " +
                checkHostIpInCidr("192.168.2.5", "192.168.1.0/24"));
        System.out.println("10.0.0.5 in 10.0.0.0/8 " +
                checkHostIpInCidr("10.0.0.5", "10.0.0.0/8"));

        System.out.println("\nIPv6 CIDR Check:");
        System.out.println("2001:db8::1 in 2001:db8::/64: " +
                checkHostIpInCidr("2001:db8::1", "2001:db8::/64"));
        System.out.println("2001:db8::1:2 in 2001:db8::/64: " +
                checkHostIpInCidr("2001:db8::1:2", "2001:db8::/64"));
        System.out.println("fe80::1 in fe80::/10: " +
                checkHostIpInCidr("fe80::1", "fe80::/10"));
        System.out.println("::1 in ::/0: " +
                checkHostIpInCidr("::1", "::/0"));

        // False Cases
        System.out.println("2001:db9::1 in 2001:db8::/64: " +
                checkHostIpInCidr("2001:db9::1", "2001:db8::/64"));
        System.out.println("2001:db8:1::1 in 2001:db8::/64: " +
                checkHostIpInCidr("2001:db8:1::1", "2001:db8::/64"));
        System.out.println("2002::1 in 2001:db8::/32: " +
                checkHostIpInCidr("2002::1", "2001:db8::/32"));
        System.out.println("fc00::1 in fe80::/10: " +
                checkHostIpInCidr("fc00::1", "fe80::/10"));
    }

    private static void testBgpParsing() {
        List<String> bgpLines = List.of(
                "   Network          Next Hop            Metric LocPrf Weight Path",
                "*> 10.0.1.36/30     0.0.0.0                  0         32768 i",
                "*> 2001:db8:1::/48  2001:db8:ff::1           0             0 200 i",
                "*  2001:db8:2::/48  2001:db8:ff::2                         0 300 400 i",
                "*>                  2001:db8:ff::1                         0 200 400 i",
                "*> 10.0.1.40/30     10.0.0.54                0             0 2 i",
                "*  2001:db8:3::/48  2001:db8:ff::1                         0 200 500 i",
                "*>                  2001:db8:ff::2                         0 300 i"
        );

        ArrayList<String> focusPrefixes = new ArrayList<>(List.of(
                "10.0.1.36/30",
                "10.0.1.40/30",
                "2001:db8:1::/48",
                "2001:db8:2::/48",
                "2001:db8:3::/48"
        ));

        System.out.println("BGP Parse Result:");
        List<BgpRouteEntry> entries = parseRouteEntries(bgpLines, focusPrefixes);
        for (BgpRouteEntry entry : entries) {
            System.out.printf("IP Prefix: %s, Best: %b, Next Hop: %s, Weight: %d, Path: %s%n",
                    entry.getIpPrefix(), entry.isBest(), entry.getNextHop(),
                    entry.getWeight(), entry.getPath());
        }
    }

    private static void testHostIpGeneration() {
        System.out.println("IPv4: ");
        System.out.println("192.168.1.0/24 -> " + generateHostIp("192.168.1.0/24"));
        System.out.println("10.0.0.0/8 -> " + generateHostIp("10.0.0.0/8"));

        System.out.println("\nIPv6: ");
        System.out.println("2001:db8:abcd:12::/64 -> " + generateHostIp("2001:db8:abcd:12::/64"));
        System.out.println("fe80::/64 -> " + generateHostIp("fe80::/64"));
        System.out.println("2001:db8::/32 -> " + generateHostIp("2001:db8::/32"));
        System.out.println("::1/128 -> " + generateHostIp("::1/128"));
    }
}
