package com.adfmig.parser;

import com.adfmig.core.estate.AdfEstate;
import com.adfmig.core.estate.CredentialFinding;
import com.adfmig.core.estate.DatabaseConnection;
import com.adfmig.core.estate.DiscoveredApplication;
import com.adfmig.core.estate.ProjectDependency;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the survey that runs before any deep parsing: which applications exist, how each is
 * consumed, what database each reaches, and which depend on one another.
 */
class EstateParserTest {

    @TempDir
    Path root;

    // --- Discovery ---------------------------------------------------------------------

    @Test
    void findsEachApplicationByItsWorkspaceFile() throws IOException {
        workspace("Payments");
        workspace("Billing");

        assertThat(parse().applications())
                .extracting(DiscoveredApplication::path)
                .containsExactlyInAnyOrder("Payments", "Billing");
    }

    @Test
    void treatsATreeWithNoWorkspaceFileAsOneApplication() throws IOException {
        write("Model/src/com/example/Employees.xml",
                "<Entity xmlns=\"http://xmlns.oracle.com/bc4j\" Name=\"Employees\" DBObjectName=\"EMPLOYEES\"/>");

        // Source is routinely exported without its .jws. Reporting zero applications would be
        // useless; the tree itself is the application.
        assertThat(parse().applications())
                .singleElement()
                .satisfies(a -> assertThat(a.entityObjects()).isEqualTo(1));
    }

    @Test
    void classifiesApplicationsByHowTheyAreConsumed() throws IOException {
        workspace("RestApp");
        write("RestApp/Model/adfmsrc/com/example/rest/v1/Res.xml",
                "<pageDefinition xmlns=\"http://xmlns.oracle.com/adfm/uimodel\" id=\"R\" usageMode=\"RESTClient\"/>");

        workspace("FacesApp");
        write("FacesApp/View/src/com/example/HomePageDef.xml",
                "<pageDefinition xmlns=\"http://xmlns.oracle.com/adfm/uimodel\" id=\"Home\"/>");

        workspace("SharedLib");
        write("SharedLib/Model/src/com/example/Employees.xml",
                "<Entity xmlns=\"http://xmlns.oracle.com/bc4j\" Name=\"Employees\" DBObjectName=\"EMPLOYEES\"/>");

        Map<String, DiscoveredApplication.Profile> profiles = parse().applications().stream()
                .collect(java.util.stream.Collectors.toMap(
                        DiscoveredApplication::path, DiscoveredApplication::profile));

        assertThat(profiles).containsEntry("RestApp", DiscoveredApplication.Profile.REST_CONTRACT);
        assertThat(profiles).containsEntry("FacesApp", DiscoveredApplication.Profile.ADF_FACES_UI);
        // No consumer inside the application: almost always a library other applications import.
        assertThat(profiles).containsEntry("SharedLib", DiscoveredApplication.Profile.MODEL_ONLY);
    }

    // --- Database ----------------------------------------------------------------------

    @Test
    void readsTheDeployedDatasourceFormWithItsNamespacedAttribute() throws IOException {
        workspace("Billing");
        write("Billing/Model/src/com/example/common/bc4j.xcfg", """
                <?xml version="1.0" encoding="UTF-8"?>
                <BC4JConfig version="11.1" xmlns="http://xmlns.oracle.com/bc4j/configuration">
                  <AppModuleConfigBag ApplicationName="com.example.BillingAM">
                    <AppModuleConfig name="BillingAMLocal" ApplicationName="com.example.BillingAM">
                      <Custom ns0:JDBCDataSource="java:comp/env/jdbc/HrDS"
                              xmlns:ns0="http://xmlns.oracle.com/bc4j/configuration"/>
                    </AppModuleConfig>
                  </AppModuleConfigBag>
                </BC4JConfig>
                """);

        assertThat(parse().connectionsByApplication().get("Billing"))
                .singleElement()
                .satisfies(c -> {
                    assertThat(c.kind()).isEqualTo(DatabaseConnection.Kind.DATA_SOURCE);
                    // java:comp/env/jdbc/HrDS and jdbc/HrDS name the same datasource.
                    assertThat(c.dataSource()).isEqualTo("jdbc/HrDS");
                    assertThat(c.applicationModule()).isEqualTo("com.example.BillingAM");
                });
    }

    @Test
    void readsTheDevelopmentConnectionFormWithoutCapturingThePassword() throws IOException {
        workspace("Legacy");
        write("Legacy/Model/src/com/example/common/bc4j.xcfg", """
                <?xml version="1.0" encoding="UTF-8"?>
                <BC4JConfig>
                  <AppModuleConfigBag>
                    <AppModuleConfig name="HrModuleLocal">
                      <ApplicationName>com.example.HrModule</ApplicationName>
                    </AppModuleConfig>
                  </AppModuleConfigBag>
                  <ConnectionDefinition name="scott">
                    <ENTRY name="HOSTNAME" value="dbhost"/>
                    <ENTRY name="JDBC_PORT" value="1521"/>
                    <ENTRY name="SID" value="ORCL"/>
                    <ENTRY name="user" value="SCOTT"/>
                    <ENTRY name="password"><![CDATA[{904}05F0911C68SECRET]]></ENTRY>
                  </ConnectionDefinition>
                </BC4JConfig>
                """);

        DatabaseConnection connection = parse().connectionsByApplication().get("Legacy").get(0);

        assertThat(connection.kind()).isEqualTo(DatabaseConnection.Kind.JDBC_URL);
        assertThat(connection.schemaIdentity()).isEqualTo("scott@dbhost:1521/orcl");
        // A migration tool has no business copying customer credentials into a report.
        assertThat(connection.toString()).doesNotContain("SECRET");
    }

    @Test
    void reportsApplicationsWritingTheSameSchema() throws IOException {
        workspace("Payments");
        datasource("Payments", "java:comp/env/jdbc/HrDS");
        workspace("Billing");
        datasource("Billing", "jdbc/HrDS");
        workspace("Reporting");
        datasource("Reporting", "jdbc/WarehouseDS");

        Map<String, List<String>> conflicts = parse().schemaConflicts();

        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get("jdbc/HrDS")).containsExactlyInAnyOrder("Payments", "Billing");
    }

    @Test
    void carriesTheLockingModeThroughToTheGeneratedEntities() throws IOException {
        workspace("Orders");
        write("Orders/Model/src/com/example/common/bc4j.xcfg", """
                <?xml version="1.0" encoding="UTF-8"?>
                <BC4JConfig version="11.1" xmlns="http://xmlns.oracle.com/bc4j/configuration">
                  <AppModuleConfigBag>
                    <AppModuleConfig name="AMLocal" ApplicationName="com.example.OrdersAM">
                      <Database jbo.locking.mode="optimistic"/>
                      <Custom ns0:JDBCDataSource="jdbc/OrdersDS"
                              xmlns:ns0="http://xmlns.oracle.com/bc4j/configuration"/>
                    </AppModuleConfig>
                  </AppModuleConfigBag>
                </BC4JConfig>
                """);

        AdfEstate estate = parse();

        // An application already relying on optimistic locking needs a version column in the
        // replacement, or concurrent updates silently overwrite one another after migration.
        assertThat(estate.connectionsByApplication().get("Orders").get(0).isOptimisticLocking()).isTrue();
        assertThat(estate.applicationsUsingOptimisticLocking()).containsExactly("Orders");
    }

    // --- Credentials -------------------------------------------------------------------

    @Test
    void locatesCredentialMaterialWithoutReadingItsValue() throws IOException {
        workspace("Secrets");
        write("Secrets/src/META-INF/jazn-data.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <jazn-data><jazn-realm><realm><name>jazn.com</name><users>
                  <user><name>admin</name><credentials>{903}TOPSECRETVALUE</credentials></user>
                </users></realm></jazn-realm></jazn-data>
                """);
        Files.createDirectories(root.resolve("Secrets/src/META-INF"));
        Files.write(root.resolve("Secrets/src/META-INF/cwallet.sso"), new byte[]{1, 2, 3});

        List<CredentialFinding> findings = parse().credentialFindings();

        assertThat(findings)
                .extracting(CredentialFinding::kind)
                .containsExactlyInAnyOrder(
                        CredentialFinding.Kind.CREDENTIAL_STORE,
                        CredentialFinding.Kind.OBFUSCATED_SECRET);

        CredentialFinding obfuscated = findings.stream()
                .filter(f -> f.kind() == CredentialFinding.Kind.OBFUSCATED_SECRET)
                .findFirst().orElseThrow();

        assertThat(obfuscated.path()).isEqualTo("Secrets/src/META-INF/jazn-data.xml");
        assertThat(obfuscated.location()).isEqualTo("credentials");
        // The location is recorded; the value never is. Reversing Oracle's obfuscation would make
        // this a credential harvester rather than a migration tool.
        assertThat(findings.toString()).doesNotContain("TOPSECRETVALUE");
    }

    @Test
    void doesNotReportAWalletLockFileAsCredentialMaterial() throws IOException {
        workspace("Locked");
        Files.createDirectories(root.resolve("Locked/src/META-INF"));
        Files.write(root.resolve("Locked/src/META-INF/cwallet.sso.lck"), new byte[0]);

        assertThat(parse().credentialFindings()).isEmpty();
    }

    // --- Dependencies ------------------------------------------------------------------

    @Test
    void detectsDependenciesReachingAcrossApplicationBoundaries() throws IOException {
        workspace("SharedLib");
        write("SharedLib/Model/Model.jpr", "<jpr:project xmlns:jpr=\"http://x\"/>");
        write("SharedLib/Model/src/com/example/Employees.xml",
                "<Entity xmlns=\"http://xmlns.oracle.com/bc4j\" Name=\"Employees\" DBObjectName=\"EMPLOYEES\"/>");

        workspace("Consumer");
        write("Consumer/ViewController/ViewController.jpr", """
                <?xml version="1.0" encoding="UTF-8"?>
                <jpr:project xmlns:jpr="http://xmlns.oracle.com/ide/project">
                  <hash n="oracle.ide.model.DependencyConfiguration">
                    <list n="dependencyList">
                      <hash>
                        <hash n="dependable">
                          <url n="sourceOwnerURL" path="../../SharedLib/SharedLib.jws"/>
                          <url n="sourceURL" path="../../SharedLib/Model/Model.jpr"/>
                        </hash>
                        <value n="recognizerId" v="oracle.jdeveloper.library"/>
                      </hash>
                    </list>
                  </hash>
                </jpr:project>
                """);

        AdfEstate estate = parse();

        assertThat(estate.crossApplicationDependencies())
                .singleElement()
                .satisfies(d -> {
                    assertThat(d.fromApplication()).isEqualTo("Consumer");
                    assertThat(d.toApplication()).isEqualTo("SharedLib");
                });

        // The shared library must exist as a module before the application importing it.
        assertThat(estate.migrationOrder())
                .extracting(DiscoveredApplication::path)
                .containsExactly("SharedLib", "Consumer");
    }

    @Test
    void doesNotTreatAProjectReferenceWithinOneApplicationAsCrossApplication() throws IOException {
        workspace("Single");
        write("Single/Model/Model.jpr", "<jpr:project xmlns:jpr=\"http://x\"/>");
        write("Single/ViewController/ViewController.jpr", """
                <?xml version="1.0" encoding="UTF-8"?>
                <jpr:project xmlns:jpr="http://xmlns.oracle.com/ide/project">
                  <hash n="oracle.ide.model.DependencyConfiguration">
                    <list n="dependencyList"><hash><hash n="dependable">
                      <url n="sourceOwnerURL" path="../Single.jws"/>
                      <url n="sourceURL" path="../Model/Model.jpr"/>
                    </hash></hash></list>
                  </hash>
                </jpr:project>
                """);

        AdfEstate estate = parse();
        assertThat(estate.dependencies()).isNotEmpty();
        assertThat(estate.crossApplicationDependencies()).isEmpty();
    }

    @Test
    void keepsApplicationsThatDependOnEachOtherRatherThanDroppingThem() throws IOException {
        workspace("Alpha");
        mutualDependency("Alpha", "Beta");
        workspace("Beta");
        mutualDependency("Beta", "Alpha");

        AdfEstate estate = parse();

        // A cycle is a real shape in ADF estates. Report it for a human to break, but never lose
        // an application from the migration plan.
        assertThat(estate.migrationOrder()).hasSize(2);
        assertThat(estate.dependencyCycles())
                .extracting(DiscoveredApplication::path)
                .containsExactlyInAnyOrder("Alpha", "Beta");
    }

    // --- helpers -----------------------------------------------------------------------

    private AdfEstate parse() throws IOException {
        return new EstateParser().parse(root);
    }

    private void workspace(String name) throws IOException {
        write(name + "/" + name + ".jws",
                "<?xml version=\"1.0\"?><jws:workspace xmlns:jws=\"http://xmlns.oracle.com/ide\"/>");
    }

    private void datasource(String application, String jndiName) throws IOException {
        write(application + "/Model/src/com/example/common/bc4j.xcfg", """
                <?xml version="1.0" encoding="UTF-8"?>
                <BC4JConfig version="11.1" xmlns="http://xmlns.oracle.com/bc4j/configuration">
                  <AppModuleConfigBag>
                    <AppModuleConfig name="AMLocal" ApplicationName="com.example.AM">
                      <Custom ns0:JDBCDataSource="%s"
                              xmlns:ns0="http://xmlns.oracle.com/bc4j/configuration"/>
                    </AppModuleConfig>
                  </AppModuleConfigBag>
                </BC4JConfig>
                """.formatted(jndiName));
    }

    private void mutualDependency(String from, String to) throws IOException {
        write(from + "/Model/Model.jpr", """
                <?xml version="1.0" encoding="UTF-8"?>
                <jpr:project xmlns:jpr="http://xmlns.oracle.com/ide/project">
                  <hash n="oracle.ide.model.DependencyConfiguration">
                    <list n="dependencyList"><hash><hash n="dependable">
                      <url n="sourceOwnerURL" path="../../%s/%s.jws"/>
                      <url n="sourceURL" path="../../%s/Model/Model.jpr"/>
                    </hash></hash></list>
                  </hash>
                </jpr:project>
                """.formatted(to, to, to));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
