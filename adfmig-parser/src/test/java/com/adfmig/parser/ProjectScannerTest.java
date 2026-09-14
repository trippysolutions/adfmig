package com.adfmig.parser;

import com.adfmig.core.AdfArtifact;
import com.adfmig.core.AdfArtifactType;
import com.adfmig.core.ScanResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each case here is a defect that real ADF applications actually caused. They are kept as
 * fixtures so the parser cannot regress on them.
 */
class ProjectScannerTest {

    @TempDir
    Path root;

    // --- Classification ---------------------------------------------------------------

    @Test
    void classifiesSecurityPolicyByRootElementNotFilename() throws IOException {
        // Across a large sample of real applications, `jazn-data` is the root element of 96
        // files but only 8 of them are named jazn-data.xml. A scanner driven by filename
        // misses 92% of the security configuration.
        write("META-INF/system-jazn-data.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <jazn-data><jazn-realm/></jazn-data>
                """);

        assertThat(typeOf(scan(), "META-INF/system-jazn-data.xml"))
                .isEqualTo(AdfArtifactType.JAZN_DATA);
    }

    @Test
    void distinguishesRestResourceFromPageDefinitionByUsageMode() throws IOException {
        // ADF reuses the binding-layer pageDefinition format for REST resources rather than
        // defining a dedicated schema. usageMode is the only thing that separates them, and
        // getting it wrong misses the entire existing HTTP contract.
        write("rest/v1/HrModule_EmployeesViewResources.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <pageDefinition xmlns="http://xmlns.oracle.com/adfm/uimodel" id="Employees"
                                usageMode="RESTClient"><bindings/></pageDefinition>
                """);
        write("pages/CustomersPageDef.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <pageDefinition xmlns="http://xmlns.oracle.com/adfm/uimodel" id="CustomersPageDef">
                  <bindings/></pageDefinition>
                """);

        ScanResult r = scan();
        assertThat(typeOf(r, "rest/v1/HrModule_EmployeesViewResources.xml"))
                .isEqualTo(AdfArtifactType.REST_RESOURCE);
        assertThat(typeOf(r, "pages/CustomersPageDef.xml"))
                .isEqualTo(AdfArtifactType.PAGE_DEFINITION);
        assertThat(r.hasExistingRestContract()).isTrue();
    }

    @Test
    void distinguishesResourceRegistryFromDataBindingsSharingTheApplicationRoot() throws IOException {
        write("model/ResourceRegistry.rpx", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Application xmlns="http://xmlns.oracle.com/adfm/application" id="ResourceRegistry">
                  <pageMap><page path="v1_Employees" usageId="v1_Employees"/></pageMap></Application>
                """);
        write("view/DataBindings.cpx", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Application xmlns="http://xmlns.oracle.com/adfm/application" id="DataBindings">
                  <pageMap/></Application>
                """);

        ScanResult r = scan();
        assertThat(typeOf(r, "model/ResourceRegistry.rpx")).isEqualTo(AdfArtifactType.REST_RESOURCE_REGISTRY);
        assertThat(typeOf(r, "view/DataBindings.cpx")).isEqualTo(AdfArtifactType.DATA_BINDINGS);
    }

    @Test
    void classifiesGroovyExpressionCompanionFiles() throws IOException {
        // .bcs holds an entity's Groovy expressions. It is annotated Java-ish source, not XML;
        // parsing it as XML previously reported the richest artifact in the app as unreadable.
        write("model/entities/Employees.bcs", """
                //Groovy Scripts for com.example.Employees
                @TransientValueExpression(attributeName="EmployeeId")
                def EmployeeId_ExpressionScript_Expression() {
                  (new oracle.jbo.server.SequenceImpl("EMPLOYEES_SEQ", adf.object.getDBTransaction()))
                      .getSequenceNumber()
                }
                """);

        ScanResult r = scan();
        assertThat(typeOf(r, "model/entities/Employees.bcs")).isEqualTo(AdfArtifactType.GROOVY_SCRIPT);
        assertThat(r.unparseable()).isEmpty();
    }

    // --- Robustness against real-world files ------------------------------------------

    @Test
    void parsesBusinessComponentsDeclaringAnUnresolvableExternalDtd() throws IOException {
        // Every ADF BC file references a DTD that is not shipped with the application. The parser
        // must not try to fetch it: offline that fails, and inside an air-gapped customer network
        // it stalls the scan.
        write("model/HrModule.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE AppModule SYSTEM "jbo_03_01.dtd">
                <AppModule xmlns="http://xmlns.oracle.com/bc4j" Name="HrModule">
                  <ViewUsage Name="EmployeesView1" ViewObjectName="com.example.EmployeesView"/>
                </AppModule>
                """);

        ScanResult r = scan();
        assertThat(r.unparseable()).isEmpty();
        assertThat(typeOf(r, "model/HrModule.xml")).isEqualTo(AdfArtifactType.APPLICATION_MODULE);
        assertThat(artifact(r, "model/HrModule.xml").name()).isEqualTo("HrModule");
    }

    @Test
    void recoversDocumentsDeclaringJavaCharsetNamesRatherThanIanaNames() throws IOException {
        // JDeveloper wrote encoding="Cp1252" / "MacRoman" / "Cp1256" (Arabic locales). Conformant
        // XML parsers reject those names; the JDK resolves them as charset aliases. Dozens of
        // files in a large sample of real applications are unreadable without this fallback.
        Path file = root.resolve("META-INF/jps-config.xml");
        Files.createDirectories(file.getParent());
        Files.write(file, """
                <?xml version="1.0" encoding="Cp1252" standalone="yes"?>
                <jpsConfig xmlns="http://xmlns.oracle.com/oracleas/schema/11/jps-config-11_1.xsd">
                  <serviceProviders/>
                </jpsConfig>
                """.getBytes(Charset.forName("Cp1252")));

        ScanResult r = scan();
        assertThat(r.unparseable()).isEmpty();
        assertThat(typeOf(r, "META-INF/jps-config.xml")).isEqualTo(AdfArtifactType.JPS_CONFIG);
    }

    @Test
    void reportsMalformedXmlInsteadOfFailingTheScan() throws IOException {
        write("model/Broken.xml", "<Entity Name=\"Truncated\"");
        write("model/Good.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Good"/>
                """);

        ScanResult r = scan();
        assertThat(r.unparseable()).containsKey("model/Broken.xml");
        assertThat(typeOf(r, "model/Good.xml")).isEqualTo(AdfArtifactType.ENTITY_OBJECT);
    }

    @Test
    void skipsBuildOutputAndVersionControlDirectories() throws IOException {
        write("target/classes/Employees.xml",
                "<Entity xmlns=\"http://xmlns.oracle.com/bc4j\" Name=\"Copy\"/>");
        write(".git/config.xml", "<Entity Name=\"NotReal\"/>");
        write("model/Employees.xml",
                "<Entity xmlns=\"http://xmlns.oracle.com/bc4j\" Name=\"Employees\"/>");

        assertThat(scan().ofType(AdfArtifactType.ENTITY_OBJECT))
                .extracting(AdfArtifact::path)
                .containsExactly("model/Employees.xml");
    }

    @Test
    void redactsSecretsFromRetainedAttributesSoTheyCannotReachAnExport() throws IOException {
        // Root attributes are captured wholesale for later analysis, and scan results are written
        // to JSON and handed to customers. Nothing here needs a secret's value.
        write("config/connection.xml",
                "<ConnectionDefinition name=\"scott\" password=\"{904}OBFUSCATEDVALUE\" user=\"scott\"/>");

        AdfArtifact artifact = artifact(scan(), "config/connection.xml");

        assertThat(artifact.attributes()).containsEntry("password", AdfArtifact.REDACTED);
        assertThat(artifact.attributes()).containsEntry("user", "scott");
        assertThat(artifact.toString()).doesNotContain("OBFUSCATEDVALUE");
    }

    // --- Migration profile -------------------------------------------------------------

    @Test
    void flagsAdfFacesConsumersThatCannotBePortedBehindAnHttpContract() throws IOException {
        write("view/CustomersPageDef.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <pageDefinition xmlns="http://xmlns.oracle.com/adfm/uimodel" id="CustomersPageDef"/>
                """);
        write("view/customers.jsff", "<jsp:root/>");

        ScanResult r = scan();
        assertThat(r.hasAdfFacesConsumers()).isTrue();
        assertThat(r.hasExistingRestContract()).isFalse();
        assertThat(r.countsByRelevance()).containsEntry(AdfArtifactType.Relevance.UI_REWRITE, 2);
    }

    // --- helpers -----------------------------------------------------------------------

    private ScanResult scan() throws IOException {
        return new ProjectScanner().scan(root);
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static AdfArtifact artifact(ScanResult r, String path) {
        return r.artifacts().stream()
                .filter(a -> a.path().equals(path))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no artifact scanned at " + path));
    }

    private static AdfArtifactType typeOf(ScanResult r, String path) {
        return artifact(r, path).type();
    }
}
