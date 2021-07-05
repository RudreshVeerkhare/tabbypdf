package ru.icc.cells.tabbypdf.writers;

import lombok.AllArgsConstructor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import ru.icc.cells.tabbypdf.entities.TableBox;
import ru.icc.cells.tabbypdf.entities.TableRegion;
import ru.icc.cells.tabbypdf.entities.TextLine;
import ru.icc.cells.tabbypdf.entities.TextBlock;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.util.List;

@AllArgsConstructor
public class TableTextBlockToXmlWriter implements Writer<TableBox, String> {
    private String fileName;

    @Override
    public String write(List<TableBox> tableBoxes) {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = null;
        try {
            docBuilder = docFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }

        Document doc = docBuilder.newDocument();
        Element rootElement = doc.createElement("document");
        rootElement.setAttribute("filename", fileName);
        doc.appendChild(rootElement);

        for (TableBox tableBox : tableBoxes) {
            Element tableElement = doc.createElement("table");
            tableElement.setAttribute("id", "dummy");

            for (TableRegion tableRegion : tableBox.getTableRegions()) {

                Element regionElement = doc.createElement("region");
                regionElement.setAttribute("col-increment", "0");
                regionElement.setAttribute("row-increment", "0");
                regionElement.setAttribute("id", "1");
                regionElement.setAttribute("page", "dummy");

                for (TextLine textLine : tableRegion.getTextLines()) {
                    for (TextBlock textBlock : textLine.getTextBlocks()) {

                        Element cellElement = doc.createElement("cell");

                        Element cellBoxElement = doc.createElement("bounding-box");
                        cellBoxElement.setAttribute("x1", String.valueOf((int) textBlock.getLeft()));
                        cellBoxElement.setAttribute("x2", String.valueOf((int) textBlock.getRight()));
                        cellBoxElement.setAttribute("y1", String.valueOf((int) textBlock.getBottom()));
                        cellBoxElement.setAttribute("y2", String.valueOf((int) textBlock.getTop()));
                        cellElement.appendChild(cellBoxElement);

                        Element cellContentElement = doc.createElement("content");
                        cellContentElement.appendChild(doc.createTextNode(textBlock.getText() + "  "));
                        cellElement.appendChild(cellContentElement);
                        regionElement.appendChild(cellElement);
                    }
                }
                tableElement.appendChild(regionElement);
            }
            rootElement.appendChild(tableElement);
        }

        try {
            TransformerFactory transformerFactory = TransformerFactory.newInstance();
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            DOMSource source = new DOMSource(doc);
            StringWriter stringWriter = new StringWriter();
            transformer.transform(source, new StreamResult(stringWriter));
            return stringWriter.getBuffer().toString().replaceAll("[\n\r]", "");
        } catch (TransformerException e) {
            throw new RuntimeException(e);
        }
    }
}
