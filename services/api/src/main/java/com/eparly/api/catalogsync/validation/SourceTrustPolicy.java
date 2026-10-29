package com.eparly.api.catalogsync.validation;

import com.eparly.api.catalogsync.CatalogSyncProperties;
import com.eparly.api.catalogsync.domain.CatalogSyncSource;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.util.Locale;

@Service
public class SourceTrustPolicy {
    private final CatalogSyncProperties properties;
    private final Environment environment;

    public SourceTrustPolicy(CatalogSyncProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    public URI validate(CatalogSyncSource source, String value) {
        try {
            URI uri = URI.create(value).normalize();
            String host = uri.getHost();
            if (host == null || uri.getUserInfo() != null || uri.getFragment() != null
                    || uri.getPort() < -1 || uri.getPort() > 65535) {
                throw new IllegalArgumentException("Invalid source URL");
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            boolean localTest = isLocalTestHost(lowerHost);
            if (uri.getPort() != -1 && uri.getPort() != 443 && !localTest) {
                throw new IllegalArgumentException("Only the standard HTTPS port is allowed");
            }
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !(localTest && isTestProfile())) {
                throw new IllegalArgumentException("Only HTTPS sources are allowed");
            }
            String officialDomain = source.officialDomain().toLowerCase(Locale.ROOT);
            if (!lowerHost.equals(officialDomain) && !lowerHost.endsWith("." + officialDomain)) {
                throw new IllegalArgumentException("URL host is outside the configured official domain");
            }
            if (!localTest) {
                validateResolvedAddresses(host, InetAddress.getAllByName(host));
            } else if (!properties.isAllowLoopbackForTests() || !isTestProfile()) {
                throw new IllegalArgumentException("Loopback sources are disabled");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Could not validate source host", exception);
        }
    }

    public void validateResolvedAddresses(String host, InetAddress[] addresses) {
        if (addresses.length == 0) {
            throw new IllegalArgumentException("Source host did not resolve to an address");
        }
        boolean localTest = isLocalTestHost(host.toLowerCase(Locale.ROOT))
                && properties.isAllowLoopbackForTests() && isTestProfile();
        for (InetAddress address : addresses) {
            if (localTest && address.isLoopbackAddress()) continue;
            if (!isGloballyRoutable(address)) {
                throw new IllegalArgumentException("Source resolves to a non-public network address");
            }
        }
    }

    private boolean isTestProfile() {
        return environment.acceptsProfiles(Profiles.of("test"));
    }

    private static boolean isLocalTestHost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.equals("[::1]");
    }

    private static boolean isGloballyRoutable(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return false;
        byte[] bytes = address.getAddress();
        if (bytes.length == 4) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            if (first == 0 || first == 10 || first == 127 || first >= 224
                    || (first == 100 && second >= 64 && second <= 127)
                    || (first == 172 && second >= 16 && second <= 31)
                    || (first == 192 && (second == 168 || (second == 0 && third <= 2)
                    || (second == 88 && third == 99)))
                    || (first == 198 && (second == 18 || second == 19
                    || (second == 51 && third == 100)))
                    || (first == 203 && second == 0 && third == 113)
                    || (first == 168 && second == 63 && third == 129
                    && Byte.toUnsignedInt(bytes[3]) == 16)) {
                return false;
            }
            return true;
        }
        if (!(address instanceof Inet6Address)) return false;
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        boolean globalUnicast = (first & 0xe0) == 0x20;
        int third = Byte.toUnsignedInt(bytes[2]);
        int fourth = Byte.toUnsignedInt(bytes[3]);
        boolean specialPurpose2001 = first == 0x20 && second == 0x01 && third <= 0x01;
        boolean documentation = first == 0x20 && second == 0x01
                && third == 0x0d && fourth == 0xb8;
        boolean documentationRange = first == 0x3f && second == 0xff && (third & 0xf0) == 0;
        boolean transition = first == 0x20 && second == 0x02;
        return globalUnicast && !specialPurpose2001 && !documentation && !documentationRange && !transition;
    }
}
