package org.generator;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.*;

public class SANDGenerator {
    private static final String TEMPLATE_FOLDER = "C:\\Users\\kenne\\Documents\\Coding\\KSO_JAVA\\KSOTools\\resources\\sample\\";
    // private static final String TEMPLATE_FOLDER =
    // "C:\\Users\\Admin\\IdeaProjects\\KSOTOOLS\\resources\\template\\";
    private static final String OUTPUT_FOLDER = "C:\\Users\\kenne\\Documents\\Coding\\KSO_JAVA\\KSOTools\\output\\";
    private static final String RGB_BLUE = "156, 194, 229";
    private static List<List<String>> tableData = new ArrayList<>(); // Existing table data
    private static Map<String, List<List<String>>> tabbedData = new HashMap<>(); // Tabbed data
    static final String TIMESTAMP_FORMAT = "yyyyMMddHHmmss";

    public static void main(String[] args) {
        List<File> cfgFiles = findCfgFiles(new File(TEMPLATE_FOLDER), new ArrayList<>());
        String outputFileName = OUTPUT_FOLDER + MessageFormat.format("datacapture_{0}.docx",
                new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date()));

        for (File file : cfgFiles) {
            extractTableData(file);
            tableData.clear(); // Clear tableData after processing each file

            String parentFolderName = file.getParentFile().getName().toUpperCase();
            generateWordDocument(tabbedData, outputFileName, parentFolderName);
            tabbedData.clear(); // Clear the tabbed data for the next file
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

    private static void extractTableData(File file) {
        List<List<String>> tableData = new ArrayList<>();

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(file);

            NodeList tabNodes = document.getElementsByTagName("tab");
            for (int i = 0; i < tabNodes.getLength(); i++) {
                Node tabNode = tabNodes.item(i);
                Element tabElement = (Element) tabNode;
                String tabName = tabElement.getAttribute("name");
                // skip Traditional, Simplified Chinese
                if (tabName.equals("Traditional Chinese") || tabName.equals("Simplified Chinese"))
                    continue;
                // Initialize table data for the current tab
                List<List<String>> tableDataForTab = new ArrayList<>();
                tabbedData.put(tabName, tableDataForTab);

                // Process elements within this tab only:
                processElement(tabElement, tableDataForTab, 1, RGB_BLUE);
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        // return tableData;
    }

    private static void processElement(Element element, List<List<String>> tableData, int level, String rgbColor) {
        NodeList childNodes = element.getChildNodes();
        boolean isRepeating = isRepeatingElement(element);
        String rgbColorLighter = getLighterColor(rgbColor);

        System.out.println("-----------------------------------------");
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            System.out.println("type: " + node.getNodeType() + "  tagName : " + node.getNodeName());
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();
                if ((!tagName.equals("container") && !tagName.equals("item"))) {
                    continue;
                }
                System.out.println("proceed \n tagName : " + tagName +
                        "  name: " + childElement.getAttribute("name") + ", " +
                        "  pathid: " + childElement.getAttribute("pathid") + ", " +
                        "  location: " + childElement.getAttribute("location") + ", ");
                if (!childElement.getAttribute("name").equals("dcr_content")) {
                    List<String> rowData = new ArrayList<>();
                    rowData.add(formatLevel(level));
                    rowData.add(isRepeating ? "Y" : "N");
                    rowData.add(childElement.getAttribute("name"));
                    rowData.add(getLabel(childElement));
                    rowData.add(getDataType(childElement));
                    rowData.add(isMandatory(childElement) ? "Y" : "N");
                    rowData.add(""); // Description & Logic (empty for now)
                    tableData.add(rowData);
                }
                if (tagName.equals("container")) {
                    processElement(childElement, tableData, level + 1, rgbColorLighter);
                }
            } else {
                System.out.println("skipped");
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
            return isRepeatingElement(element)
                    ? "Container (Min = " + element.getAttribute("min") + ", Max = " + element.getAttribute("max") + ")"
                    : "Container";
        } else if (tagName.equals("item")) {
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && !node.getNodeName().equals("label")) {
                    return node.getNodeName();
                }
            }
        } else {
            return element.getTagName();
        }
        return "";
    }

    private static boolean isMandatory(Element element) {
        if (!element.getTagName().equals("item"))
            return false;
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

    private static void generateWordDocument(Map<String, List<List<String>>> tabbedData, String outputFileName,
            String tableName) {
        try (XWPFDocument document = new XWPFDocument()) {
            if (!new File(outputFileName).exists()) {
                document.createParagraph().createRun().setText(tableName); // Add table name as header
            }

            // Create tabs with separate tables for each tab name
            for (Map.Entry<String, List<List<String>>> tabEntry : tabbedData.entrySet()) {
                // Create tab header
                document.createParagraph().createRun().setText(tabEntry.getKey());

                // Use a temporary variable name to avoid conflict
                List<List<String>> tableDataForTab = tabEntry.getValue();
                XWPFTable table = document.createTable();

                XWPFTableRow headerRow = table.getRow(0);
                headerRow.getCell(0).setText("Level");
                headerRow.addNewTableCell().setText("Repeating");
                headerRow.addNewTableCell().setText("Path ID");
                headerRow.addNewTableCell().setText("Label");
                headerRow.addNewTableCell().setText("Data Type");
                headerRow.addNewTableCell().setText("Mandatory");
                headerRow.addNewTableCell().setText("Description & Logic");

                // Data Rows (use the temporary variable)
                for (List<String> rowData : tableDataForTab) {
                    XWPFTableRow row = table.createRow();
                    int cellIndex = 0;
                    for (String cellData : rowData) {
                        row.getCell(cellIndex).setText(cellData);
                        cellIndex++;
                    }
                }
                document.createParagraph(); // Add a separating empty line
            }

            // Save the document
            try (FileOutputStream out = new FileOutputStream(outputFileName, true)) {
                document.write(out);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}