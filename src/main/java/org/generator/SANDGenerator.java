package org.generator;

import org.apache.commons.compress.utils.Sets;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTFonts;
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
import java.util.*;
import java.util.List;

public class SANDGenerator {

    private static String templateFolder;
    private static String outputFolder;
    private static final String RGB_BLUE = "156, 194, 229";
    private static List<List<String>> tableData = new ArrayList<>(); // Existing table data
    private static Map<String, List<List<String>>> tabbedData = new HashMap<>(); // Tabbed data
    static final String TIMESTAMP_FORMAT = "yyyyMMddHHmmss";
    static final Set<String> availableElementTags = Sets.newHashSet("container", "item");
    public static void main(String[] args) {
        loadConfiguration();

        List<File> cfgFiles = findCfgFiles(new File(templateFolder), new ArrayList<>());
        String outputFileName = outputFolder + MessageFormat.format("datacapture_{0}.docx",
                new SimpleDateFormat(TIMESTAMP_FORMAT).format(new Date()));

        for (File file : cfgFiles) {
            extractTableData(file);
            tableData.clear(); // Clear tableData after processing each file

            String parentFolderName = file.getParentFile().getName().toUpperCase();
            generateWordDocument(tabbedData, outputFileName, parentFolderName);
            tabbedData.clear(); // Clear the tabbed data for the next file
        }
    }
    private static void loadConfiguration() {
        Properties config = new Properties();
        try (FileInputStream inputStream = new FileInputStream("config/system.properties")) {
            config.load(inputStream);
            templateFolder = config.getProperty("template.folder");
            outputFolder = config.getProperty("output.folder");
            System.out.println("Current work space: " + System.getProperty("user.dir") );
            System.out.println("templateFolder: " + templateFolder);
            System.out.println("outputFolder: " + outputFolder);
        } catch (IOException e) {
            System.err.println("Error loading configuration file. Using default paths.");
            // Use your original hardcoded paths as a fallback
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

                processElement(tabElement, tableDataForTab, "","");
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        // return tableData;
    }

    private static void processElement(Element element, List<List<String>> tableData, String parentXPath, String parentHierarchy) {
        NodeList childNodes = element.getChildNodes();
        boolean isRepeating = isRepeatingElement(element);
        if (!parentHierarchy.isEmpty()) parentHierarchy += ".";
        if (!parentXPath.isEmpty()) parentXPath += "/";

        // System.out.println("-----------------------------------------");
        int orderExcludeSkipped = 0;
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            // System.out.println("type: " + node.getNodeType() + "  tagName : " + node.getNodeName());

            if(!isValidElements(node))
                continue;

            Element childElement = (Element) node;
            String tagName = childElement.getTagName();
            String dateType = getDataType(childElement);
            String xPath = parentXPath + childElement.getAttribute("name");

            System.out.println("proceed \n"+
                    "  name: " + xPath + ", " +
                    "  pathid: " + childElement.getAttribute("pathid") + ", " +
                    "  location: " + childElement.getAttribute("location") + ", ");
            // Calculate formatted level with parent information
            if (!childElement.getAttribute("name").equals("dcr_content")) {
                List<String> rowData = new ArrayList<>();

                orderExcludeSkipped ++;
                String childOrder = parentHierarchy + (orderExcludeSkipped);
                rowData.add(childOrder);
                rowData.add(isRepeating ? "Y" : "N");
                rowData.add(xPath);
                rowData.add(getLabel(childElement));
                rowData.add(dateType);
                rowData.add(isMandatory(childElement) ? "Y" : "N");
                rowData.add(""); // Description & Logic (empty for now)
                tableData.add(rowData);


                // Recursive call for containers only
                if (tagName.equals("container")) {
                    processElement(childElement, tableData, xPath, childOrder);
                }
            }
        }
    }
    private static boolean isValidElements(Node node){
        return isValidNodeType(node) && isAvailablElementTags(node) && isHidden(node);
    }
    private static boolean isValidNodeType(Node node){
        boolean isValid = true;
        if (node.getNodeType() != Node.ELEMENT_NODE){
            System.out.println("skipped - NOT ELEMENT_NODE");
            isValid = false;
        }
        return isValid;
    }
    private static boolean isAvailablElementTags(Node node){
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
    private static boolean isHidden(Node node){
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

    // Calculate the depth of the level (e.g., "1.1.2" has depth 3)
    private static int getLevelDepth(String level) {
        return level.split("\\.").length;
    }

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
    private static void renderHeaderRow(XWPFTable table)
    {
        XWPFTableRow headerRow = table.getRow(0);
        headerRow.getCell(0).setText("Level");
        headerRow.addNewTableCell().setText("Repeating");
        headerRow.addNewTableCell().setText("Path ID");
        headerRow.addNewTableCell().setText("Label");
        headerRow.addNewTableCell().setText("Data Type");
        headerRow.addNewTableCell().setText("Mandatory");
        headerRow.addNewTableCell().setText("Description & Logic");
        for (XWPFTableCell cell : headerRow.getTableCells()) {
            cell.getCTTc().addNewTcPr().addNewShd().setFill("0070C0"); // Set background color
            XWPFParagraph paragraph = cell.getParagraphs().get(0);
            if (paragraph != null ) {
                if(paragraph.getCTP() != null && paragraph.getCTP().getPPr()!= null){
                    CTFonts fonts = paragraph.getCTP().getPPr().addNewRPr().addNewRFonts();
                    fonts.setAscii("Calibri Body");
                    fonts.setHAnsi("Calibri Body");
                    fonts.setCs("Calibri Body");
                }
            }
            assert paragraph != null;
            for (XWPFRun run : paragraph.getRuns()) {
                run.setFontSize(8);
                if(run.getCTR() != null && run.getCTR().getRPr()!= null){
                    run.getCTR().getRPr().addNewColor().setVal("FFFFFF");
                    run.setBold(true);
                } // Set font color to white
            }
        }
    }
    private static void renderContentRow(XWPFTable table, List<List<String>> tableDataForTab)
    {
        for (List<String> rowData : tableDataForTab)
        {
            XWPFTableRow row = table.createRow();
            int cellIndex = 0;
            for (String cellData : rowData) {
                XWPFTableCell cell = row.getCell(cellIndex);
                cell.setText(cellData);
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    for (XWPFRun run : paragraph.getRuns()) {
                        run.setFontSize(8);
                    }
                }

                String hexColor = "";
                int level = getLevelDepth(rowData.get(0));
                if (rowData.get(4).contains("Container")) {
                    hexColor = calculateLevelColor(level);
                    cell.getCTTc().addNewTcPr().addNewShd().setFill(hexColor);
                } else if (cellIndex == 1) {
                    hexColor = calculateLevelColor(level-1);
                    cell.getCTTc().addNewTcPr().addNewShd().setFill(hexColor);
                }
                cellIndex++;
            }
        }
    }
    private static void generateWordDocument(Map<String, List<List<String>>> tabbedData, String outputFileName,
                                             String tableName)
    {
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

                // render Header Row
                renderHeaderRow(table);

                // render Content Row
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