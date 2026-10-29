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
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    && !(localTest && isTestProfile())) {
                throw new IllegalArgumentException("Only HTTPS sources are allowed");
            }
            String officialDomain = source.officialDomain().toLowerCase(Locale.ROOT);
            if (!lowerHost.equals(officialDomain) && !lowerHost.endsWith("." + officialDomain)) {
                throw new IllegalArgumentException("URL host is outside the configured official domain");
            }
            if (!localTest) {
                for (InetAddress address : InetAddress.getAllByName(host)) {
                    if (isNonPublic(address)) {
                        throw new IllegalArgumentException("Source resolves to a non-public network address");
                    }
                }
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

    private boolean isTestProfile() {
        return environment.acceptsProfiles(Profiles.of("test"));
    }

    private static boolean isLocalTestHost(String host) {
        return host.equals("localhost") || host.equals("127.0.0.1") || host.equals("::1")
                || host.equals("[::1]");
    }

    private static boolean isNonPublic(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) return true;
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }
}
