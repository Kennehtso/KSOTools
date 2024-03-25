package org.example;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class SANDGenerator {
    private static final String TEMPLATE_FOLDER = "C:\\Users\\Admin\\IdeaProjects\\KSOTOOLS\\resources\\sample\\";
    // private static final String TEMPLATE_FOLDER = "C:\\Users\\Admin\\IdeaProjects\\KSOTOOLS\\resources\\template\\";
    private static final String OUTPUT_FOLDER = "C:\\Users\\Admin\\IdeaProjects\\KSOTOOLS\\output\\";
    private static final String RGB_BLUE = "156, 194, 229";

    public static void main(String[] args) {
        List<File> cfgFiles = findCfgFiles(new File(TEMPLATE_FOLDER), new ArrayList<>());
        String outputFileName = OUTPUT_FOLDER + "result.docx";

        for (File file : cfgFiles) {
            List<List<String>> tableData = extractTableData(file);

            String parentFolderName = file.getParentFile().getName();
            String tableName = parentFolderName.substring(3).toUpperCase(); // Assumes prefix "pn_"

            // generateWordDocument(tableData, outputFileName, tableName);
        }
    }

    private static List<File> findCfgFiles(File directory, List<File> cfgFiles) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findCfgFiles(file, cfgFiles);
                } else if (file.getName().endsWith(".cfg")) {
                    cfgFiles.add(file);
                }
            }
        }
        return cfgFiles;
    }

    private static List<List<String>> extractTableData(File file) {
        List<List<String>> tableData = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            Element rootContainer = (Element) document.getElementsByTagName("root-container").item(0);
            processElement(rootContainer, tableData, 1, RGB_BLUE);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        return tableData;
    }

    private static void processElement(Element element, List<List<String>> tableData, int level, String rgbColor) {
        NodeList childNodes = element.getChildNodes();
        boolean isRepeating = isRepeatingElement(element);
        String rgbColorLighter = getLighterColor(rgbColor);

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();

                if (tagName.equals("container") || tagName.equals("item")) {
                    List<String> rowData = new ArrayList<>();
                    rowData.add(formatLevel(level));
                    rowData.add(isRepeating ? "Y" : "N");
                    rowData.add(childElement.getAttribute("pathid"));
                    rowData.add(getLabel(childElement));
                    rowData.add(getDataType(childElement));
                    rowData.add(isMandatory(childElement) ? "Y" : "N");
                    rowData.add(""); // Description & Logic (empty for now)
                    tableData.add(rowData);

                    if (tagName.equals("container")) {
                        processElement(childElement, tableData, level + 1, rgbColorLighter);
                    }
                }
            }
        }
    }

    private static String formatLevel(int level) {
        StringBuilder sb = new StringBuilder();
        int currentLevel = level;
        while (currentLevel > 0) {
            int remainder = currentLevel % 100;
            sb.insert(0, remainder != 0 ? "." + remainder : "");
            currentLevel = currentLevel / 100;
        }
        return sb.toString();
    }

    private static boolean isRepeatingElement(Element element) {
        return element.hasAttribute("min") || element.hasAttribute("max");
    }

    private static String getLabel(Element element) {
        NodeList labelNodes = element.getElementsByTagName("label");
        if (labelNodes.getLength() > 0) {
            return labelNodes.item(0).getTextContent();
        }
        return "";
    }

    private static String getDataType(Element element) {
        String tagName = element.getTagName();
        if (tagName.equals("container")) {
            return isRepeatingElement(element) ? "Container (Min = " + element.getAttribute("min") + ", Max = " + element.getAttribute("max") + ")" : "Container";
        } else {
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    String childTagName = node.getNodeName();
                    if (childTagName.equals("text") || childTagName.equals("checkbox") || childTagName.equals("textarea") || childTagName.equals("browser")) {
                        return "Text";
                    }
                }
            }
        }
        return "";
    }

    private static boolean isMandatory(Element element) {
        if (element.getTagName().equals("item")) {
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element childElement = (Element) node;
                    if (childElement.getAttribute("required").equals("t")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static String getLighterColor(String rgbColor) {
        String[] components = rgbColor.split(", ");
        int r = Integer.parseInt(components[0]);
        int g = Integer.parseInt(components[1]);
        int b = Integer.parseInt(components[2]);

        r = Math.max(r - 20, 0);
        g = Math.max(g - 20, 0);
        b = Math.max(b - 20, 0);

        return r + ", " + g + ", " + b;
    }

    //TODO - generate xml to word table
    private static void generateWordDocument(List<List<String>> tableData, String outputFileName, String tableName) {

    }
}
