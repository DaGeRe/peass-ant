package de.stro18.peass_ant.buildeditor;

import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.execution.utils.ProjectModules;
import de.dagere.peass.folders.PeassFolders;
import de.dagere.peass.testtransformation.JUnitTestTransformer;
import de.stro18.peass_ant.buildeditor.tomcat.TomcatBuildEditor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

public class TestTomcatBuildEditor
{
    private static final File TEST_FOLDER = new File("src" + File.separator + "test");
    private static final File tomcatExampleDirectory = new File(TEST_FOLDER, "resources" + File.separator + "tomcat-example");
    
    private static final File tomcatDirectory = new File(tomcatExampleDirectory, "tomcat");
    private static final File jdbcDirectory = new File(tomcatDirectory, "modules" + File.separator + "jdbc-pool");
    private static final File tomcatPeassDirectory = new File(tomcatExampleDirectory, "tomcat_peass");

    private static final Path buildXmlPath = Paths.get(tomcatDirectory.getPath(), "build.xml");
    private static final Path buildCopyXmlPath = Paths.get(tomcatDirectory.getPath(), "build-copy.xml");

    private static final Path buildXml2Path = Paths.get(jdbcDirectory.getPath(), "build.xml");
    private static final Path buildCopyXml2Path = Paths.get(jdbcDirectory.getPath(), "build-copy.xml");

    @BeforeAll
    static void prepareFiles() {
        saveFileStates();
        createTomcatPeassDirectory();
        executePrepareBuildfile();
    }

    static void saveFileStates() {
        try {
            Files.copy(buildXmlPath, buildCopyXmlPath, StandardCopyOption.REPLACE_EXISTING);
            Files.copy(buildXml2Path, buildCopyXml2Path, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static void createTomcatPeassDirectory() {
        tomcatPeassDirectory.mkdir();
    }

    static void executePrepareBuildfile() {
        PeassFolders peassFolders = new PeassFolders(tomcatDirectory);
        
        List<File> modules = new ArrayList<>();
        modules.add(tomcatDirectory);
        modules.add(jdbcDirectory);
        ProjectModules projectModules = new ProjectModules(modules);

        MeasurementConfig measurementConfig = new MeasurementConfig(1);
        measurementConfig.getKiekerConfig().setUseKieker(true);
        JUnitTestTransformer testTransformer = new JUnitTestTransformer(tomcatDirectory, measurementConfig);
        testTransformer.determineVersionsForPaths(projectModules.getModules());

        TomcatBuildEditor tomcatBuildConfigEditor = new TomcatBuildEditor(testTransformer, projectModules, peassFolders);

        tomcatBuildConfigEditor.prepareBuild();
    }

    @AfterAll
    static void revertFileChanges() {
        try {
            Files.move(buildCopyXmlPath, buildXmlPath, StandardCopyOption.REPLACE_EXISTING);
            Files.move(buildCopyXml2Path, buildXml2Path, StandardCopyOption.REPLACE_EXISTING);

            Files.walk(tomcatPeassDirectory.toPath())
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testClasspaths() {
        Document doc = createDom(buildXmlPath.toFile());

        boolean peassClasspathExists = false;
        boolean tomcatClassesExtendedClasspathConatainsPeassClasspath = false;
        boolean compileClasspathContainsPeassClasspath = false;

        NodeList listOfPaths = doc.getElementsByTagName("path");
        for (int i = 0; i < listOfPaths.getLength(); i++) {
            Node path = listOfPaths.item(i);

            Node idNode = path.getAttributes().getNamedItem("id");
            if (idNode != null && idNode.getTextContent().equals("peass.classpath")) {
                List<Element> listOfPathElements = getChildElements(path);

                for (Element pathElement: listOfPathElements) {
                    if (pathElement.getAttributes().getNamedItem("location").getTextContent().contains("kopeme-core")) {
                        peassClasspathExists = true;
                        break;
                    }
                }
            } else if (idNode != null && idNode.getTextContent().equals("tomcat.classes.extended.classpath")) {
                List<Element> listOfPathElements = getChildElements(path);

                for (Element pathElement: listOfPathElements) {
                    if (pathElement.getAttributes().getNamedItem("refid") != null 
                            && pathElement.getAttributes().getNamedItem("refid").getTextContent().equals("peass.classpath")) {
                        tomcatClassesExtendedClasspathConatainsPeassClasspath = true;
                        break;
                    }
                }
            } else if (idNode != null && idNode.getTextContent().equals("compile.classpath")) {
                List<Element> listOfPathElements = getChildElements(path);

                for (Element pathElement: listOfPathElements) {
                    if (pathElement.getAttributes().getNamedItem("refid") != null 
                            && pathElement.getAttributes().getNamedItem("refid").getTextContent().equals("peass.classpath")) {
                        compileClasspathContainsPeassClasspath = true;
                        break;
                    }
                }
            }
        }

        Assertions.assertTrue(peassClasspathExists);
        Assertions.assertTrue(tomcatClassesExtendedClasspathConatainsPeassClasspath);
        Assertions.assertTrue(compileClasspathContainsPeassClasspath);
    }

    @Test
    public void testArgline() {
        Document doc = createDom(buildXmlPath.toFile());

        boolean extendedArglineExists = false;

        NodeList listOfJUnitTasks = doc.getElementsByTagName("junit");
        for (int i = 0; i < listOfJUnitTasks.getLength(); i++) {
            List<Element> jUnitTaskChildren = getChildElements(listOfJUnitTasks.item(i));

            for (Element child : jUnitTaskChildren) {
                if (child.getTagName().equals("jvmarg") && child.getAttribute("value")
                        .contains("-Dkieker.monitoring.configuration")) {
                    extendedArglineExists = true;
                    break;
                }
            }
        }

        Assertions.assertTrue(extendedArglineExists);
    }
    
    @Test
    public void testProperties() throws XPathExpressionException {
        Document doc = createDom(buildXmlPath.toFile());

        XPath xPath = XPathFactory.newInstance().newXPath();
        
        String nioPropertyAttributeValue = xPath.compile("//property[@name='execute.test.nio2']/@value").evaluateExpression(doc, String.class);
        Assertions.assertEquals("false", nioPropertyAttributeValue);

        String aprPropertyAttributeValue = xPath.compile("//property[@name='execute.test.apr']/@value").evaluateExpression(doc, String.class);
        Assertions.assertEquals("false", aprPropertyAttributeValue);
    }

    @Test
    public void testPeassClasspathAdded() {
        Document doc = createDom(buildXml2Path.toFile());

        boolean peassClasspathExists = false;
        boolean peassClasspathAdded = false;

        NodeList listOfPaths = doc.getElementsByTagName("path");
        for (int i = 0; i < listOfPaths.getLength(); i++) {
            Node path = listOfPaths.item(i);

            Node idNode = path.getAttributes().getNamedItem("id");
            if (idNode != null && idNode.getTextContent().equals("peass.classpath")) {
                List<Element> listOfPathElements = getChildElements(path);

                for (Element pathElement : listOfPathElements) {
                    if (pathElement.getAttributes().getNamedItem("location").getTextContent().contains("kopeme-core")) {
                        peassClasspathExists = true;
                        break;
                    }
                }
            } else if (idNode != null && idNode.getTextContent().equals("tomcat.jdbc.classpath")) {
                List<Element> listOfChildElements = getChildElements(path);

                for (Element childElement : listOfChildElements) {
                    if (childElement.getTagName().equals("path") && childElement.getAttribute("refid").equals("peass.classpath")) {
                        peassClasspathAdded = true;
                        break;
                    }
                }
            }
        }

        Assertions.assertTrue(peassClasspathExists);
        Assertions.assertTrue(peassClasspathAdded);
    }

    private Document createDom(File buildfile) {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setNamespaceAware(true);

        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.parse(buildfile);
        } catch (ParserConfigurationException | IOException | SAXException e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<Element> getChildElements(Node node) {
        NodeList nodeList = node.getChildNodes();
        List<Element> elementChildNodes = new LinkedList<>();

        for (int i = 0; i < nodeList.getLength(); i++) {
            Node child = nodeList.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
                elementChildNodes.add((Element) child);
            }
        }

        return elementChildNodes;
    }

    private Element getFirstChildElement(Node node) {
        NodeList nodeList = node.getChildNodes();

        for (int i = 0; i< nodeList.getLength(); i++) {
            Node child = nodeList.item(i);

            if (child.getNodeType() == Node.ELEMENT_NODE) {
               return (Element) child;
            }
        }

        return null;
    }
}
