package util;

import lombok.extern.slf4j.Slf4j;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Slf4j
public class XmlTextExtractor {

    private static final Set<String> SKIP_TAGS = Set.of(
            "ref", "url", "id", "timestamp", "username",
            "comment", "model", "format", "sha1"
    );

    public static String extractText(String xmlContent) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();

            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);

            SAXParser parser = factory.newSAXParser();

            parser.setProperty("jdk.xml.maxGeneralEntitySizeLimit", "0");
            parser.setProperty("jdk.xml.totalEntitySizeLimit", "0");

            TextExtractorHandler handler = new TextExtractorHandler();

            InputStream is = new ByteArrayInputStream(
                    xmlContent.getBytes(StandardCharsets.UTF_8)
            );
            parser.parse(is, handler);

            return handler.getText();

        } catch (Exception e) {
            log.warn("Failed to parse XML, falling back to tag stripping: {}", e.getMessage());
            return stripXmlTags(xmlContent);
        }
    }

    private static String stripXmlTags(String content) {
        return content
                .replaceAll("<[^>]+>", " ")
                .replaceAll("&[a-z]+;", " ")
                .replaceAll("&#[0-9]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static class TextExtractorHandler extends DefaultHandler {
        private final StringBuilder text = new StringBuilder();
        private boolean skipContent = false;

        @Override
        public void startElement(String uri, String localName,
                                 String qName, Attributes attributes) {
            skipContent = SKIP_TAGS.contains(qName.toLowerCase());
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            skipContent = false;
            text.append(" ");
        }

        @Override
        public void characters(char[] ch, int start, int length) {
            if (!skipContent) {
                text.append(ch, start, length);
            }
        }

        public String getText() {
            return text.toString();
        }
    }
}
