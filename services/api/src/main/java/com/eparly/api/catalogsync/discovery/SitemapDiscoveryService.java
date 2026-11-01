package com.eparly.api.catalogsync.discovery;

import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

@Service
public class SitemapDiscoveryService {
    public List<String> discover(String xml, int limit) {
        try {
            XMLInputFactory factory = XMLInputFactory.newFactory();
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            factory.setProperty("javax.xml.stream.isSupportingExternalEntities", false);
            var reader = factory.createXMLStreamReader(new StringReader(xml));
            List<String> urls = new ArrayList<>();
            boolean inLocation = false;
            StringBuilder location = new StringBuilder();
            while (reader.hasNext() && urls.size() < limit) {
                int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT && reader.getLocalName().equalsIgnoreCase("loc")) {
                    inLocation = true;
                    location.setLength(0);
                } else if (event == XMLStreamConstants.CHARACTERS && inLocation) {
                    location.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && reader.getLocalName().equalsIgnoreCase("loc")) {
                    String url = location.toString().trim();
                    if (!url.isBlank()) urls.add(url);
                    inLocation = false;
                }
            }
            reader.close();
            return List.copyOf(urls);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid sitemap XML", exception);
        }
    }
}
