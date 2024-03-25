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
import java.util.ArrayList;
import java.util.List;

public class SANDGenerator {
    private static final String TEMPLATE_FOLDER = "C:\\Users\\kenne\\Documents\\Coding\\KSO_JAVA\\KSOTools\\resources\\sample\\";
    // private static final String TEMPLATE_FOLDER = "C:\\Users\\Admin\\IdeaProjects\\KSOTOOLS\\resources\\template\\";
    private static final String OUTPUT_FOLDER = "C:\\Users\\kenne\\Documents\\Coding\\KSO_JAVA\\KSOTools\\output\\";
    private static final String RGB_BLUE = "156, 194, 229";

    public static void main(String[] args) {
        List<File> cfgFiles = findCfgFiles(new File(TEMPLATE_FOLDER), new ArrayList<>());
        String outputFileName = OUTPUT_FOLDER + "datacapture.docx";

        for (File file : cfgFiles) {
            List<List<String>> tableData = extractTableData(file);

            String parentFolderName = file.getParentFile().getName();

            generateWordDocument(tableData, outputFileName, parentFolderName.toUpperCase());
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

        System.out.println("-----------------------------------------");
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            System.out.println("type: "+ node.getNodeType() + "  tagName : " + node.getNodeName());
            if (node.getNodeType() == Node.ELEMENT_NODE){
                Element childElement = (Element) node;
                String tagName = childElement.getTagName();
                if (
                        (tagName.equals("container") || tagName.equals("item") ||
                                (
                                        tagName.equals("tab") &&
                                                !childElement.getAttribute("name").equals("Traditional Chinese") &&
                                                !childElement.getAttribute("name").equals("Simplified Chinese")
                                )
                        )
                ) {
                    System.out.println("proceed \n tagName : "  + tagName +
                            "  name: " + childElement.getAttribute("name") + ", " +
                            "  pathid: " + childElement.getAttribute("pathid") + ", " +
                            "  location: " + childElement.getAttribute("location") + ", " );
                    if(!childElement.getAttribute("name").equals("dcr_content")){
                        List<String> rowData = new ArrayList<>();
                        rowData.add(formatLevel(level));
                        rowData.add(isRepeating ? "Y" : "N");
                        rowData.add(childElement.getAttribute("pathid"));
                        rowData.add(getLabel(childElement));
                        rowData.add(getDataType(childElement));
                        rowData.add(isMandatory(childElement) ? "Y" : "N");
                        rowData.add(""); // Description & Logic (empty for now)
                        tableData.add(rowData);
                    }
                    if(tagName.equals("container") ||
                            (
                                    tagName.equals("tab") &&
                                            !childElement.getAttribute("name").equals("Traditional Chinese")    &&
                                            !childElement.getAttribute("name").equals("Simplified Chinese")
                            )
                    ){
                        processElement(childElement, tableData, level + 1, rgbColorLighter);
                    }
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
            return isRepeatingElement(element) ? "Container (Min = " + element.getAttribute("min") + ", Max = " + element.getAttribute("max") + ")" : "Container";
        } else if(tagName.equals("item")){
            NodeList childNodes = element.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node node = childNodes.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE && !node.getNodeName().equals("label")) {
                    return node.getNodeName();
//                    String childTagName = node.getNodeName();
//                    if (childTagName.equals("text") || childTagName.equals("checkbox") || childTagName.equals("textarea") || childTagName.equals("browser")) {
//                        return "Text";
//                    }
                }
            }
        }
        else{
            return element.getTagName();
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

    private static void generateWordDocument(List<List<String>> tableData, String outputFileName, String tableName) {
        try (XWPFDocument document = new XWPFDocument()) {
            if (!new File(outputFileName).exists()) {
                document.createParagraph().createRun().setText(tableName); // Add table name as header
            }

            // Create a table
            XWPFTable table = document.createTable();

            // Header Row
            XWPFTableRow headerRow = table.getRow(0);
            headerRow.getCell(0).setText("Level");
            headerRow.addNewTableCell().setText("Repeating");
            headerRow.addNewTableCell().setText("Path ID");
            headerRow.addNewTableCell().setText("Label");
            headerRow.addNewTableCell().setText("Data Type");
            headerRow.addNewTableCell().setText("Mandatory");
            headerRow.addNewTableCell().setText("Description & Logic");

            // Data Rows
            for (List<String> rowData : tableData) {
                XWPFTableRow row = table.createRow();
                int cellIndex = 0; // Start at the first cell
                for (String cellData : rowData) {
                    row.getCell(cellIndex).setText(cellData);
                    cellIndex++; // Increment the index after setting the cell text
                }
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