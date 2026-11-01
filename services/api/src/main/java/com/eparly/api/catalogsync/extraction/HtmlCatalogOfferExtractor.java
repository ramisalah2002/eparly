package com.eparly.api.catalogsync.extraction;

import com.eparly.api.offer.domain.OfferSector;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class HtmlCatalogOfferExtractor {
    private final ObjectMapper objectMapper;

    public HtmlCatalogOfferExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<RawCatalogOffer> extract(String html, String baseUrl, String configJson, OfferSector sector) {
        try {
            JsonNode config = objectMapper.readTree(configJson == null ? "{}" : configJson);
            String offerSelector = config.path("offerSelector").asText("");
            String nameSelector = config.path("nameSelector").asText("");
            String priceSelector = config.path("priceSelector").asText("");
            String urlSelector = config.path("urlSelector").asText("a");
            String period = config.path("billingPeriod").asText(null);
            if (offerSelector.isBlank() || nameSelector.isBlank() || priceSelector.isBlank()) {
                throw new IllegalArgumentException("HTML source requires offerSelector, nameSelector and priceSelector");
            }
            Document document = Jsoup.parse(html, baseUrl);
            List<RawCatalogOffer> offers = new ArrayList<>();
            for (Element item : document.select(offerSelector)) {
                Element name = item.selectFirst(nameSelector);
                Element price = item.selectFirst(priceSelector);
                Element link = item.selectFirst(urlSelector);
                String url = link == null ? baseUrl : link.absUrl("href");
                String offerName = name == null ? null : name.text();
                offers.add(new RawCatalogOffer(url, url, offerName,
                        item.text(), price == null ? null : price.text(), null, period,
                        null, null, Map.of(), List.of(), sector));
            }
            return offers;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid HTML extractor configuration", exception);
        }
    }
}
