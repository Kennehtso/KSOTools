package org.generator;

import org.apache.commons.compress.utils.Sets;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STTblWidth;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;

/**
 * The `SANDGenerator` class is responsible for generating a Word document based
 * on the configuration files and extracting table data from XML files.
 * It loads the configuration from a properties file, finds the configuration
 * files in the template folder, and generates a Word document for each
 * configuration file.
 * The extracted table data is stored in a tabbed data structure.
 */
public class SANDGenerator {

    private static String mode = "";
    private static String templateFolder = "";
    private static String componentFolder = "";
    private static String outputFolder = "";
    private static final String RGB_BLUE = "156, 194, 229";
    private static List<List<String>> tableData = new ArrayList<>(); // Existing table data
    private static Map<String, List<List<String>>> tabbedData = new HashMap<>(); // Tabbed data
    static final String TIMESTAMP_FORMAT = "yyyyMMddHHmmss";
    static final Set<String> availableElementTags = Sets.newHashSet("container", "item");
    static final Set<String> unAvailableXpaths = Sets.newHashSet("isReplicate");

    public static void main(String[] args) {
        loadConfiguration();

        List<File> cfgFiles = findFilesByExtension(new File(templateFolder), new ArrayList<>(), "cfg");
        List<File> componentFiles = findFilesByExtension(new File(componentFolder), new ArrayList<>(), "xml");
        String outputFileName = outputFolder + MessageFormat.format("datacapture_{0}.docx",
                new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date()));

        for (File file : cfgFiles) {
            extractTableData(file, componentFiles);
            tableData.clear();

            String parentFolderName = file.getParentFile().getName().toUpperCase();
            generateWordDocument(tabbedData, outputFileName, parentFolderName);
            tabbedData.clear();
        }
    }

    /**
     * Load configuration from the properties file.
     */
    private static void loadConfiguration() {
        Properties config = new Properties();
        try (FileInputStream inputStream = new FileInputStream("config/system.properties")) {
            config.load(inputStream);
            if (config.getProperty("mode").equalsIgnoreCase("test"))
                mode = "test.";
            templateFolder = config.getProperty(mode + "template.folder");
            componentFolder = config.getProperty(mode + "template.component.folder");
            outputFolder = config.getProperty("output" + mode + ".folder");

            System.out.println("Current work space: " + System.getProperty("user.dir"));
            System.out.println("Current mode: " + config.getProperty("mode"));
            System.out.println("templateFolder: " + templateFolder);
            System.out.println("componentFolder: " + componentFolder);
            System.out.println("outputFolder: " + outputFolder);
        } catch (IOException e) {
            System.err.println("Error loading configuration file. Using default paths.");
        }
    }

    /**
     * Find all configuration files in the directory.
     * 
     * @param directory
     * @param targetFiles
     * @return
     */
    private static List<File> findFilesByExtension(File directory, List<File> targetFiles, String ext) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findFilesByExtension(file, targetFiles, ext);
                } else if (file.getName().endsWith("." + ext)) {
                    targetFiles.add(file);
                }
            }
        }
        return targetFiles;
    }

    /**
     * Extract table data from the XML file.
     * 
     * @param file
     */
    private static void extractTableData(File file, List<File> componentFiles) {
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

                processElement(tabElement, tableDataForTab, "", "");
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Process the element and extract the table data.
     * 
     * @param element
     * @param tableData
     * @param parentXPath
     * @param parentHierarchy
     */
    private static void processElement(Element element, List<List<String>> tableData, String parentXPath,
            String parentHierarchy) {
        NodeList childNodes = element.getChildNodes();
        boolean isRepeating = isRepeatingElement(element);
        if (!parentHierarchy.isEmpty())
            parentHierarchy += ".";
        if (!parentXPath.isEmpty())
            parentXPath += "/";

        // System.out.println("-----------------------------------------");
        int orderExcludeSkipped = 0;
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);

            // check if the node is valid
            if (!isValidElements(node))
                continue;

            Element childElement = (Element) node;
            String tagName = childElement.getTagName();
            String dataType = getDataType(childElement);
            String xPath = parentXPath + childElement.getAttribute(tagName.equals("item") ? "pathid" : "name");

            System.out.println("proceed \n" +
                    "  name: " + xPath + ", " +
                    "  pathid: " + childElement.getAttribute("pathid") + ", " +
                    "  location: " + childElement.getAttribute("location") + ", ");

            List<String> rowData = new ArrayList<>();

            String childOrder = parentHierarchy + (orderExcludeSkipped);
            String hasBG = isRepeatingElement(element) ? "hasBG" : "noBG";
            rowData.add(childOrder);
            rowData.add((isRepeatingElement(childElement) ? "Y" : "N") + "|" + hasBG);
            rowData.add(xPath);
            rowData.add(getLabel(childElement));
            rowData.add(dataType);
            rowData.add(isMandatory(childElement) ? "Y" : "N");
            rowData.add(""); // Description & Logic (empty for now)
            tableData.add(rowData);

            orderExcludeSkipped++;

            // Recursive call for containers only
            if (tagName.equals("container")) {
                processElement(childElement, tableData, xPath, childOrder);
            }
        }
    }

    /**
     * Check if the node is valid
     * 
     * @param node
     * @return
     */
    private static boolean isValidElements(Node node) {
        return isValidNodeType(node) &&
                isAvailableElementTags(node) &&
                isAvailableXPath(node) &&
                isHidden(node);
    }

    /**
     * Check if the node is a valid element type
     * 
     * @param node
     * @return
     */
    private static boolean isValidNodeType(Node node) {
        boolean isValid = true;
        if (node.getNodeType() != Node.ELEMENT_NODE) {
            System.out.println("skipped - NOT ELEMENT_NODE");
            isValid = false;
        }
        return isValid;
    }

    /**
     * Check if the node is a valid element tag
     * 
     * @param node
     * @return
     */
    private static boolean isAvailableElementTags(Node node) {
        boolean isValid = true;
        try {
            String tagName = ((Element) node).getTagName();
            if (!availableElementTags.contains(tagName)) {
                System.out.println("skipped - NOT container|item");
                isValid = false;
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            isValid = false;
        }
        return isValid;
    }

    /**
     * Check if the xPath is available
     * 
     * @param node
     * @return
     */
    private static boolean isAvailableXPath(Node node) {
        boolean isValid = true;
        try {
            Element childElement = (Element) node;
            String tagName = ((Element) node).getTagName();
            String xPath = childElement.getAttribute(tagName.equals("item") ? "pathid" : "name");

            // Check if any item in unAvailableXpath is present in xPath
            if (unAvailableXpaths.stream().anyMatch(xPath::contains)) {
                isValid = false;
            }

        } catch (ClassCastException e) {
            e.printStackTrace();
            isValid = false;
        }
        return isValid;
    }

    /**
     * Check if the node is hidden
     * 
     * @param node
     * @return
     */
    private static boolean isHidden(Node node) {
        boolean isValid = true;
        try {
            String dateType = getDataType((Element) node);
            if (dateType.equals("hidden")) {
                System.out.println("skipped - node is hidden");
                isValid = false;
            }
        } catch (ClassCastException e) {
            e.printStackTrace();
            isValid = false;
        }
        return isValid;
    }

    /**
     * Format the level
     * 
     * @param level
     * @return
     */
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

    /**
     * Check if the element is repeating
     * 
     * @param element
     * @return
     */
    private static boolean isRepeatingElement(Element element) {
        return element.hasAttribute("min") || element.hasAttribute("max");
    }

    /**
     * Get the label of the element
     * 
     * @param element
     * @return
     */
    private static String getLabel(Element element) {
        NodeList labelNodes = element.getElementsByTagName("label");
        if (labelNodes.getLength() > 0) {
            return labelNodes.item(0).getTextContent();
        }
        return "";
    }

    /**
     * Get the data type of the element
     * 
     * @param element
     * @return
     */
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

    /**
     * Check if the element is mandatory
     * 
     * @param element
     * @return
     */
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

    /**
     * Get the depth of the level
     * 
     * @param level
     * @return
     */
    private static int getLevelDepth(String level) {
        return level.split("\\.").length;
    }

    /**
     * Calculate the level color
     * 
     * @param level
     * @return
     */
    private static String calculateLevelColor(int level) {
        String[] components = RGB_BLUE.split(", ");
        int r = Integer.parseInt(components[0]);
        int g = Integer.parseInt(components[1]);
        int b = Integer.parseInt(components[2]);

        Color color = new Color(r, g, b);
        float[] hsv = Color.RGBtoHSB(r, g, b, null);

        // Adjust value (brightness) based on level
        float valueStep = 0.05f; // Adjust this for desired smoothness
        float newValue = Math.max(hsv[2] - (level * valueStep), 0.0f);

        Color newColor = Color.getHSBColor(hsv[0], hsv[1], newValue);

        // Convert back to RGB and format as hex
        return String.format("%02X%02X%02X", newColor.getRed(), newColor.getGreen(), newColor.getBlue());
    }

    /**
     * Render the header row
     * 
     * @param table
     */
    private static void renderHeaderRow(XWPFTable table) {
        int[] columnWidths = { 2, 3, 28, 28, 12, 3, 24 };
        XWPFTableRow headerRow = table.getRow(0);
        headerRow.getCell(0).setText("Level");
        headerRow.addNewTableCell().setText("Repeating");
        headerRow.addNewTableCell().setText("Path ID");
        headerRow.addNewTableCell().setText("Label");
        headerRow.addNewTableCell().setText("Data Type");
        headerRow.addNewTableCell().setText("Mandatory");
        headerRow.addNewTableCell().setText("Description & Logic");
        int idx = 0;
        for (XWPFTableCell cell : headerRow.getTableCells()) {
            cell.getCTTc().addNewTcPr().addNewShd().setFill("0070C0"); // Set background color
            cell.setWidth(columnWidths[idx] + "%");
            XWPFParagraph paragraph = cell.getParagraphs().get(0);
            assert paragraph != null;
            paragraph.setWordWrapped(true);
            for (XWPFRun run : paragraph.getRuns()) {
                run.setFontSize(8);
                run.setBold(true);
                run.setFontFamily("Calibri");
                if (run.getCTR() != null && run.getCTR().getRPr() != null) {
                    run.getCTR().getRPr().addNewColor().setVal("FFFFFF");
                }
            }
            idx++;
        }
    }

    /**
     * Render the content row
     * 
     * @param table
     * @param tableDataForTab
     */
    private static void renderContentRow(XWPFTable table, List<List<String>> tableDataForTab) {
        for (List<String> rowData : tableDataForTab) {
            XWPFTableRow row = table.createRow();
            int cellIndex = 0;
            for (String cellData : rowData) {
                XWPFTableCell cell = row.getCell(cellIndex);
                int level = getLevelDepth(rowData.get(0));

                setContentCellText(rowData, cellData, cellIndex, cell);

                setContentCellBGColor(rowData, cellData, level, cell, cellIndex);

                setContentCellFont(cell);

                cellIndex++;
            }
        }
    }

    private static void setContentCellFont(XWPFTableCell cell) {
        for (XWPFParagraph paragraph : cell.getParagraphs()) {
            paragraph.setWordWrapped(true); // Add this line
            for (XWPFRun run : paragraph.getRuns()) {
                run.setFontSize(8);
                run.setFontFamily("Calibri");
            }
        }
    }

    private static void setContentCellText(List<String> rowData, String cellData, int cellIndex, XWPFTableCell cell) {
        if (cellIndex == 1) {
            String[] cellDataSplit = cellData.split("\\|");
            if (rowData.get(4).contains("Container")) {
                cell.setText(cellDataSplit[0]);
            } else {
                cell.setText(cellData.contains("hasBG") ? "" : cellDataSplit[0] );
            }
        } else{
            cell.setText(cellData);
        }
    }

    private static void setContentCellBGColor(List<String> rowData, String cellData, int level, XWPFTableCell cell, int cellIndex) {
        String hexColor;
        if (rowData.get(4).contains("Container")) {
            hexColor = calculateLevelColor(level);
            cell.getCTTc().addNewTcPr().addNewShd().setFill(hexColor);
        } else if (cellIndex == 1 && cellData.contains("hasBG")) {
            hexColor = calculateLevelColor(level - 1);
            cell.getCTTc().addNewTcPr().addNewShd().setFill(hexColor);
        }
    }

    /**
     * Generate the Word document
     * 
     * @param tabbedData
     * @param outputFileName
     * @param tableName
     */
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
                table.getCTTbl().getTblPr().addNewTblW().setType(STTblWidth.AUTO); // Set auto-sizing behavior
                renderHeaderRow(table);

                renderContentRow(table, tableDataForTab);

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