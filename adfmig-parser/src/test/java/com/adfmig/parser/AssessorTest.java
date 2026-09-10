package com.adfmig.parser;

import com.adfmig.core.analysis.ApplicationAssessment;
import com.adfmig.core.analysis.ArtifactAssessment;
import com.adfmig.core.analysis.Assessor;
import com.adfmig.core.analysis.EffortModel;
import com.adfmig.core.analysis.MigrationClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Covers how components are classified and costed. The classification is what a customer buys —
 * it separates work a generator does from work a person does — so each case here pins a specific
 * rule rather than an overall number.
 */
class AssessorTest {

    @TempDir
    Path root;

    private final Assessor assessor = new Assessor(EffortModel.uncalibrated());

    @Test
    void classifiesAPurelyDeclarativeEntityAsGenerated() throws IOException {
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <Attribute Name="Name" ColumnName="NAME" Type="java.lang.String"/>
                </Entity>
                """);

        assertThat(assess("com.example.Employees").migrationClass()).isEqualTo(MigrationClass.AUTO);
    }

    @Test
    void classifiesAnEntityWithGroovyValidationAsNeedingReview() throws IOException {
        // Groovy has no mechanical equivalent: each rule is read and rewritten by a person.
        write("Model/src/com/example/Salaried.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Salaried" DBObjectName="SALARIED"
                        xmlns:validation="http://xmlns.oracle.com/adfm/validation">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <Attribute Name="Salary" ColumnName="SALARY" Type="java.math.BigDecimal">
                    <validation:ExpressionValidationBean Name="SalaryRule0" OperandType="EXPR">
                      <validation:TransientExpression Name="ValidationRuleScript"/>
                    </validation:ExpressionValidationBean>
                  </Attribute>
                </Entity>
                """);

        ArtifactAssessment assessment = assess("com.example.Salaried");
        assertThat(assessment.migrationClass()).isEqualTo(MigrationClass.ASSISTED);
        assertThat(assessment.drivers()).anyMatch(d -> d.contains("Groovy validation rule"));
    }

    @Test
    void classifiesAViewObjectWithHandWrittenSqlAsNeedingReview() throws IOException {
        write("Model/src/com/example/DeptView.xml", """
                <ViewObject xmlns="http://xmlns.oracle.com/bc4j" Name="DeptView" CustomQuery="true">
                  <SQLQuery><![CDATA[SELECT d.ID, l.CITY FROM DEPARTMENTS d, LOCATIONS l
                    WHERE d.LOC_ID = l.ID]]></SQLQuery>
                  <ViewAttribute Name="Id" AliasName="ID" Type="java.lang.Integer"/>
                </ViewObject>
                """);

        ArtifactAssessment assessment = assess("com.example.DeptView");
        assertThat(assessment.migrationClass()).isEqualTo(MigrationClass.ASSISTED);
        assertThat(assessment.drivers()).anyMatch(d -> d.startsWith("hand-written SQL"));
    }

    @Test
    void classifiesHeavyCustomJavaAsRewrittenByHand() throws IOException {
        write("Model/src/com/example/BigView.xml", """
                <ViewObject xmlns="http://xmlns.oracle.com/bc4j" Name="BigView" CustomQuery="true"
                            ComponentClass="com.example.BigViewImpl">
                  <SQLQuery><![CDATA[SELECT 1 FROM DUAL]]></SQLQuery>
                  <Variable Name="aVar" Kind="viewcriteria" Type="java.lang.String"/>
                  <Variable Name="bVar" Kind="viewcriteria" Type="java.lang.String"/>
                  <EntityUsage Name="A" Entity="com.example.A"/>
                  <EntityUsage Name="B" Entity="com.example.B"/>
                  <ViewCriteria Name="c1" Conjunction="AND"/>
                  <ClientInterface Name="__clientInterface">
                    <Method Name="m1" MethodName="m1"><Return Type="java.lang.String"/></Method>
                    <Method Name="m2" MethodName="m2"><Return Type="java.lang.String"/></Method>
                  </ClientInterface>
                </ViewObject>
                """);

        assertThat(assess("com.example.BigView").migrationClass()).isEqualTo(MigrationClass.MANUAL);
    }

    @Test
    void recordsTheFindingsBehindEveryScore() throws IOException {
        // A number a customer cannot interrogate is a number they will not accept.
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES"
                        RowClass="com.example.EmployeesImpl">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <Attribute Name="Hired" ColumnName="HIRED" Type="oracle.jbo.domain.Date"/>
                  <AccessorAttribute Name="Reports" Association="com.example.Assoc"
                                     Type="oracle.jbo.RowIterator"/>
                </Entity>
                """);

        assertThat(assess("com.example.Employees").drivers())
                // The class is declared but its source is not in this fixture, so the score says so.
                .anyMatch(d -> d.contains("custom class EmployeesImpl, source not found"))
                .anyMatch(d -> d.contains("ADF runtime type"))
                .anyMatch(d -> d.contains("collection association"));
    }

    @Test
    void flagsAnEntityWithNoPrimaryKeyAsSomethingSomeoneMustDecide() throws IOException {
        // JPA has no identity to map without a key; a person chooses one.
        write("Model/src/com/example/Audit.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Audit" DBObjectName="AUDIT_LOG">
                  <Attribute Name="Message" ColumnName="MSG" Type="java.lang.String"/>
                </Entity>
                """);

        assertThat(assess("com.example.Audit").drivers()).contains("no primary key declared");
    }

    @Test
    void appliesTheStatedFormulaSoTheEstimateCanBeChecked() throws IOException {
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                </Entity>
                """);

        EffortModel model = EffortModel.uncalibrated();
        ArtifactAssessment assessment = assess("com.example.Employees");

        assertThat(assessment.effortDays()).isEqualTo(
                model.autoBase() + assessment.complexity() * model.autoRate(), within(1e-9));
    }

    @Test
    void quotesTheFrontEndRebuildSeparatelyFromTheBackendTotal() throws IOException {
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                </Entity>
                """);
        write("View/src/com/example/HomePageDef.xml",
                "<pageDefinition xmlns=\"http://xmlns.oracle.com/adfm/uimodel\" id=\"Home\"/>");

        ApplicationAssessment assessment = assessApplication();
        EffortModel model = EffortModel.uncalibrated();

        // Rebuilding a front end is a different project with different people. Folding it into a
        // backend figure makes both indefensible.
        assertThat(assessment.pageDefinitions()).isEqualTo(1);
        assertThat(assessment.frontEndRebuildDays())
                .isEqualTo(model.perPageDefinitionRewrite(), within(1e-9));
        assertThat(assessment.backendDays())
                .isEqualTo(assessment.componentDays() + assessment.securityDays()
                        + assessment.contractTestDays() + assessment.setupDays(), within(1e-9));
    }

    @Test
    void doesNotChargeForAGeneratedImplClassThatDoesNothing() throws IOException {
        // JDeveloper writes an Impl class whenever code generation is on, and most contain only
        // typed accessors. A real application declared one on 37 entities, nearly all boilerplate;
        // treating their presence as custom behaviour inflated that estimate almost threefold.
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES"
                        RowClass="com.example.EmployeesImpl">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                </Entity>
                """);
        write("Model/src/com/example/EmployeesImpl.java", """
                package com.example;
                import oracle.jbo.server.EntityImpl;
                public class EmployeesImpl extends EntityImpl {
                    public Integer getId() { return (Integer) getAttributeInternal(0); }
                    public void setId(Integer value) { setAttributeInternal(0, value); }
                }
                """);

        assertThat(assess("com.example.Employees").migrationClass()).isEqualTo(MigrationClass.AUTO);
    }

    @Test
    void chargesForAnImplClassThatReachesIntoTheAdfRuntime() throws IOException {
        write("Model/src/com/example/Orders.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Orders" DBObjectName="ORDERS"
                        RowClass="com.example.OrdersImpl">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                </Entity>
                """);
        write("Model/src/com/example/OrdersImpl.java", """
                package com.example;
                import oracle.jbo.server.EntityImpl;
                public class OrdersImpl extends EntityImpl {
                    protected void doDML(int operation, TransactionEvent e) {
                        getDBTransaction().commit();
                        super.doDML(operation, e);
                    }
                }
                """);

        var assessment = assess("com.example.Orders");
        assertThat(assessment.migrationClass()).isEqualTo(MigrationClass.ASSISTED);
        assertThat(assessment.drivers()).anyMatch(d -> d.contains("reaching into the ADF runtime"));
    }

    @Test
    void assumesTheWorstWhenACustomClassIsNotInTheScan() throws IOException {
        // The class may live in another project or only inside a library. Assuming it is empty
        // would be the more expensive mistake.
        write("Model/src/com/example/Invoices.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Invoices" DBObjectName="INVOICES"
                        RowClass="com.elsewhere.InvoicesImpl">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                </Entity>
                """);

        assertThat(assess("com.example.Invoices").drivers())
                .anyMatch(d -> d.contains("source not found"));
    }

    // --- helpers -----------------------------------------------------------------------

    private ApplicationAssessment assessApplication() throws IOException {
        return assessor.assess(new AdfApplicationParser().parse(root));
    }

    private ArtifactAssessment assess(String fqn) throws IOException {
        return assessApplication().artifacts().stream()
                .filter(a -> a.fqn().equals(fqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("not assessed: " + fqn));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
