package ru.icc.cells.tabbypdf;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import ru.icc.cells.tabbypdf.detection.TableDetector;
import ru.icc.cells.tabbypdf.detection.TableDetectorConfiguration;
import ru.icc.cells.tabbypdf.entities.Page;
import ru.icc.cells.tabbypdf.entities.TableBox;
import ru.icc.cells.tabbypdf.entities.TextBlock;
import ru.icc.cells.tabbypdf.entities.table.Table;
import ru.icc.cells.tabbypdf.exceptions.EmptyArgumentException;
import ru.icc.cells.tabbypdf.extraction.PdfDataExtractor;
import ru.icc.cells.tabbypdf.recognition.SimpleTableRecognizer;
import ru.icc.cells.tabbypdf.recognition.TableOptimizer;
import ru.icc.cells.tabbypdf.utils.processing.TextChunkProcessor;
import ru.icc.cells.tabbypdf.utils.processing.TextChunkProcessorConfiguration;
import ru.icc.cells.tabbypdf.utils.processing.filter.Heuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.EqualFontAttributesBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.EqualFontFamilyBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.EqualFontSizeBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.HeightBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.HorizontalPositionBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.SpaceWidthBiFilter;
import ru.icc.cells.tabbypdf.utils.processing.filter.bi.VerticalPositionBiHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.tri.CutInAfterTriHeuristic;
import ru.icc.cells.tabbypdf.utils.processing.filter.tri.CutInBeforeTriHeuristic;
import ru.icc.cells.tabbypdf.writers.TableToExcelWriter;
import ru.icc.cells.tabbypdf.writers.TableToHtmlWriter;
import ru.icc.cells.tabbypdf.writers.TableToXmlWriter;
import ru.icc.cells.tabbypdf.writers.TableTextBlockToXmlWriter;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.NoSuchElementException;

/**
 * @author aaltaev
 */
public class App {
    @Option(name = "-f", usage = "Folder name")
    private String inputFolder;
    @Option(name = "-xml", usage = "Resulting xmlFile file")
    private String xmlFolder;

    public static void main(String[] args) {
        new App().run(args);
    }

    public void run(String[] args) {
        CmdLineParser parser = new CmdLineParser(this);

        try {
            parser.parseArgument(args);
            checkArgThrowIfEmpty(inputFolder);
            checkArgThrowIfEmpty(xmlFolder);

            extractAndWrite();

        } catch (CmdLineException | EmptyArgumentException e) {
            parser.printUsage(System.err);
        }

    }

    private void checkArgsThrowIfAllEmpty(String... args) {
        if (!Stream.of(args).anyMatch(this::checkArgIsNotEmpty)) {
            throw new EmptyArgumentException("At least one of these options must be specified: -xml, -excel, -html.");
        }
    }

    private void checkArgThrowIfEmpty(String arg) {
        if (!checkArgIsNotEmpty(arg))
            throw new EmptyArgumentException("Required option was not specified.");
    }

    private boolean checkArgIsNotEmpty(String arg) {
        return arg != null && !arg.isEmpty();
    }

    public void extractAndWrite() {

        File folder = new File(inputFolder);
        for (File file : folder.listFiles(File::isFile)) {
            if (file.getName().lastIndexOf(".pdf") == file.getName().length() - 4) {
                List<Page> pages = new PdfDataExtractor.Factory().getPdfBoxTextExtractor(file).getPageContent();
                try {
                    for (int pageNumber = 0; pageNumber < pages.size(); pageNumber++) {
                        Page page = pages.get(pageNumber);
                        TextChunkProcessorConfiguration configuration = getRecognizingConfiguration();
                        List<TextBlock> textBlocks = new TextChunkProcessor(page, configuration).process();

                        // write data to xml
                        String fileName = file.getName();
                        TableTextBlockToXmlWriter writer = new TableTextBlockToXmlWriter(fileName);
                        try {
                            FileWriter fileWriter = new FileWriter(
                                    xmlFolder + fileName.substring(0, fileName.lastIndexOf('.')) + "-blk-output.xml");
                            fileWriter.write(writer.write(textBlocks));
                            fileWriter.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                } catch (NoSuchElementException e) {
                }
            }
        }

    }

    public static TextChunkProcessorConfiguration getDetectionConfiguration() {
        return new TextChunkProcessorConfiguration()
                /* VERTICAL FILTERS */.addFilter(new HorizontalPositionBiHeuristic())
                .addFilter(new SpaceWidthBiFilter(2, true))
                /* HORIZONTAL FILTERS */
                .addFilter(new VerticalPositionBiHeuristic()).addFilter(new HeightBiHeuristic())
                .addFilter(new CutInAfterTriHeuristic()).addFilter(new CutInBeforeTriHeuristic())
                /* COMMON FILTERS */
                .addFilter(new EqualFontFamilyBiHeuristic(Heuristic.Orientation.VERTICAL))
                .addFilter(new EqualFontAttributesBiHeuristic(Heuristic.Orientation.VERTICAL))
                .addFilter(new EqualFontSizeBiHeuristic(Heuristic.Orientation.VERTICAL))
                /* REPLACE STRINGS */
                .addStringsToReplace(new String[] { "•", "", " ", "_", "\u0002"/*  */ }).setRemoveColons(true);
        // .setUseCharacterChunks(true);
    }

    public static TextChunkProcessorConfiguration getRecognizingConfiguration() {
        return new TextChunkProcessorConfiguration()
                /* VERTICAL FILTERS */.addFilter(new HorizontalPositionBiHeuristic())
                .addFilter(new SpaceWidthBiFilter(2, true).enableListCheck(false))
                /* HORIZONTAL FILTERS */
                .addFilter(new VerticalPositionBiHeuristic()).addFilter(new HeightBiHeuristic())
                .addFilter(new CutInAfterTriHeuristic()).addFilter(new CutInBeforeTriHeuristic())
                /* COMMON FILTERS */
                .addFilter(new EqualFontFamilyBiHeuristic(Heuristic.Orientation.VERTICAL))
                .addFilter(new EqualFontAttributesBiHeuristic(Heuristic.Orientation.VERTICAL))
                .addFilter(new EqualFontSizeBiHeuristic(Heuristic.Orientation.VERTICAL))
                /* REPLACE STRINGS */
                .addStringsToReplace(new String[] { "•", "", " ", "_", "\u0002"/*  */ }).setRemoveColons(false);
    }
}
