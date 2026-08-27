package com.subhub.app;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/** Keeps visible English UI copy out of accidental caps-lock styling. */
public final class StringCapitalizationTest {
    @Test
    public void englishStringsDoNotUseAllCapsCopy() throws Exception {
        File strings = new File("src/main/res/values/strings.xml");
        if (!strings.isFile()) strings = new File("app/src/main/res/values/strings.xml");
        assertTrue("Could not locate English strings.xml", strings.isFile());

        Document document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(strings);
        NodeList entries = document.getElementsByTagName("string");
        List<String> offenders = new ArrayList<>();
        for (int index = 0; index < entries.getLength(); index++) {
            Element entry = (Element) entries.item(index);
            if ("phrase_ntr".equals(entry.getAttribute("name"))) continue;
            String visible = entry.getTextContent().replaceAll("%(?:\\d+\\$)?[^A-Za-z]*[A-Za-z%]", "");
            String letters = visible.replaceAll("[^A-Za-z]", "");
            if (!letters.isEmpty() && letters.equals(letters.toUpperCase())) {
                offenders.add(entry.getAttribute("name") + "=" + entry.getTextContent());
            }
        }
        assertTrue("All-caps UI strings: " + offenders, offenders.isEmpty());
    }
}
