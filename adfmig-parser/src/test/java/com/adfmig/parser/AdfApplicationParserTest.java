package com.adfmig.parser;

import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.ViewObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the full resolution chain — published URL to application module to view object to
 * entity object to table, plus security grants — against a miniature application shaped like the
 * ADF BC REST samples.
 */
class AdfApplicationParserTest {

    @TempDir
    Path root;

    private AdfApplication app;

    @BeforeEach
    void buildMiniatureApplication() throws IOException {
        write("Model/src/com/example/entities/Employees.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE Entity SYSTEM "jbo_03_01.dtd">
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees"
                        DBObjectType="table" DBObjectName="EMPLOYEES"
                        xmlns:validation="http://xmlns.oracle.com/adfm/validation"
                        RowClass="com.example.entities.EmployeesImpl">
                  <Attribute Name="EmployeeId" IsNotNull="true" Precision="6" Scale="0"
                             ColumnName="EMPLOYEE_ID" SQLType="NUMERIC" Type="java.lang.Integer"
                             ColumnType="NUMBER" PrimaryKey="true"/>
                  <Attribute Name="HireDate" IsNotNull="true" ColumnName="HIRE_DATE" SQLType="DATE"
                             Type="oracle.jbo.domain.Date" ColumnType="DATE">
                    <validation:CompareValidationBean Name="HireDateRule0" OnAttribute="HireDate"
                                                      OperandType="EXPR" CompareType="LESSTHANEQUALTO">
                      <validation:TransientExpression Name="ValidationRuleScript"/>
                    </validation:CompareValidationBean>
                  </Attribute>
                  <AccessorAttribute Name="Reports" Association="com.example.assocs.EmpManagerFkAssoc"
                                     Type="oracle.jbo.RowIterator" IsUpdateable="false"/>
                  <AccessorAttribute Name="Manager" Association="com.example.assocs.EmpManagerFkAssoc"
                                     Type="oracle.jbo.server.EntityImpl" IsUpdateable="true"/>
                  <Key Name="EmpEmpIdPk" PrimaryKey="true">
                    <DesignTime><Attr Name="_DBObjectName" Value="EMP_EMP_ID_PK"/></DesignTime>
                    <AttrArray Name="Attributes"><Item Value="com.example.entities.Employees.EmployeeId"/></AttrArray>
                  </Key>
                  <Key Name="EmpDeptFk">
                    <DesignTime>
                      <Attr Name="_referencedKey" Value="DEPT_ID_PK"/>
                      <Attr Name="_isForeign" Value="true"/>
                      <Attr Name="_DBObjectName" Value="EMP_DEPT_FK"/>
                    </DesignTime>
                    <AttrArray Name="Attributes"><Item Value="com.example.entities.Employees.DepartmentId"/></AttrArray>
                  </Key>
                  <Key Name="EmpSalaryMin">
                    <DesignTime>
                      <Attr Name="_checkCondition" Value="salary &gt; 0"/>
                      <Attr Name="_isCheck" Value="true"/>
                    </DesignTime>
                    <AttrArray Name="Attributes"/>
                  </Key>
                </Entity>
                """);

        write("Model/src/com/example/views/EmployeesView.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE ViewObject SYSTEM "jbo_03_01.dtd">
                <ViewObject xmlns="http://xmlns.oracle.com/bc4j" Name="EmployeesView"
                            CustomQuery="false"
                            ComponentClass="com.example.views.EmployeesViewImpl"
                            SelectList="Employees.EMPLOYEE_ID,
                                 Employees.HIRE_DATE"
                            FromList="EMPLOYEES Employees">
                  <Variable Name="firstNameVar" Kind="viewcriteria" Type="java.lang.String"/>
                  <EntityUsage Name="Employees" Entity="com.example.entities.Employees"/>
                  <ViewAttribute Name="EmployeeId" EntityAttrName="EmployeeId" EntityUsage="Employees"
                                 AliasName="EMPLOYEE_ID" IsNotNull="true"/>
                  <ViewAttribute Name="HireDate" EntityAttrName="HireDate" EntityUsage="Employees"
                                 AliasName="HIRE_DATE"/>
                  <ViewCriteria Name="EmployeesViewCriteria" Conjunction="AND">
                    <ViewCriteriaRow Name="row0">
                      <ViewCriteriaItem Name="FirstName" ViewAttribute="FirstName" Operator="STARTSWITH"
                                        Conjunction="AND" Value=":firstNameVar" IsBindVarValue="true"
                                        Required="Optional"/>
                    </ViewCriteriaRow>
                  </ViewCriteria>
                  <ClientInterface Name="__clientInterface">
                    <Method Name="calculateEmployees" MethodName="calculateEmployees">
                      <Return Name="_return_type_" Type="java.lang.String"/>
                      <Parameter Name="firstName" Type="java.lang.String"/>
                    </Method>
                  </ClientInterface>
                </ViewObject>
                """);

        write("Model/src/com/example/views/DepartmentsView.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE ViewObject SYSTEM "jbo_03_01.dtd">
                <ViewObject xmlns="http://xmlns.oracle.com/bc4j" Name="DepartmentsView" CustomQuery="true">
                  <DesignTime><Attr Name="_isExpertMode" Value="true"/></DesignTime>
                  <SQLQuery><![CDATA[SELECT d.DEPARTMENT_ID,
                       l.STREET_ADDRESS
                FROM DEPARTMENTS d, LOCATIONS l WHERE d.LOCATION_ID = l.LOCATION_ID]]></SQLQuery>
                  <ViewAttribute Name="DepartmentId" AliasName="DEPARTMENT_ID" Type="java.lang.Integer"/>
                </ViewObject>
                """);

        write("Model/src/com/example/services/HrModule.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <!DOCTYPE AppModule SYSTEM "jbo_03_01.dtd">
                <AppModule xmlns="http://xmlns.oracle.com/bc4j" Name="HrModule">
                  <ViewUsage Name="EmployeesView1" ViewObjectName="com.example.views.EmployeesView"/>
                  <ViewUsage Name="DepartmentsView1" ViewObjectName="com.example.views.DepartmentsView"/>
                </AppModule>
                """);

        write("Model/adfmsrc/com/example/rest/v1/HrModule_EmployeesViewResources.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <pageDefinition xmlns="http://xmlns.oracle.com/adfm/uimodel"
                                id="HrModule_EmployeesViewResources" usageMode="RESTClient">
                  <executables>
                    <variableIterator id="variables"/>
                    <iterator Binds="EmployeesView1" DataControl="HrModuleDataControl" id="It"/>
                  </executables>
                  <bindings>
                    <tree IterBinding="It" id="Employees"/>
                    <tree IterBinding="It" id="EmployeesJobs"/>
                    <methodAction id="calculateEmployees" MethodName="calculateEmployees"
                                  IsViewObjectMethod="true" DataControl="HrModuleDataControl">
                      <NamedData NDName="firstName" NDType="java.lang.String"/>
                    </methodAction>
                  </bindings>
                </pageDefinition>
                """);

        write("Model/adfmsrc/com/example/rest/v1/HrModule_DepartmentsViewResources.xml", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <pageDefinition xmlns="http://xmlns.oracle.com/adfm/uimodel"
                                id="HrModule_DepartmentsViewResources" usageMode="RESTClient">
                  <executables>
                    <iterator Binds="DepartmentsView1" DataControl="HrModuleDataControl" id="It2"/>
                  </executables>
                  <bindings><tree IterBinding="It2" id="Departments"/></bindings>
                </pageDefinition>
                """);

        write("Model/adfmsrc/com/example/ResourceRegistry.rpx", """
                <?xml version="1.0" encoding="UTF-8" ?>
                <Application xmlns="http://xmlns.oracle.com/adfm/application" id="ResourceRegistry"
                             Package="com.example">
                  <pageMap>
                    <page path="v1_Employees" usageId="v1_HrModule_EmployeesViewResources"/>
                    <page path="v1_EmployeesJobs" usageId="v1_HrModule_EmployeesViewResources"/>
                    <page path="v1_Departments" usageId="v1_HrModule_DepartmentsViewResources"/>
                  </pageMap>
                  <dataControlUsages>
                    <BC4JDataControl id="HrModuleDataControl" Package="com.example.services"
                                     Configuration="HrModuleLocal"
                                     xmlns="http://xmlns.oracle.com/adfm/datacontrol"/>
                  </dataControlUsages>
                  <pageDefinitionUsages>
                    <page id="v1_HrModule_EmployeesViewResources"
                          path="com.example.rest.v1.HrModule_EmployeesViewResources"/>
                    <page id="v1_HrModule_DepartmentsViewResources"
                          path="com.example.rest.v1.HrModule_DepartmentsViewResources"/>
                  </pageDefinitionUsages>
                </Application>
                """);

        write("src/META-INF/jazn-data.xml", """
                <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                <jazn-data>
                  <jazn-realm default="jazn.com">
                    <realm><name>jazn.com</name>
                      <users><user><name>redsam</name><credentials>{903}xx</credentials></user></users>
                    </realm>
                  </jazn-realm>
                  <policy-store><applications><application>
                    <name>MiniApp</name>
                    <jazn-policy><grant>
                      <grantee><principals><principal>
                        <class>oracle.security.jps.internal.core.principals.JpsXmlUserImpl</class>
                        <name>redsam</name>
                      </principal></principals></grantee>
                      <permissions>
                        <permission>
                          <class>oracle.adf.share.security.authorization.RestServicePermission</class>
                          <name>Employees</name>
                          <actions>get, create, update, delete</actions>
                        </permission>
                        <permission>
                          <class>oracle.adf.share.security.authorization.RestServicePermission</class>
                          <name>EmployeesJobs</name>
                          <actions>get</actions>
                        </permission>
                      </permissions>
                    </grant></jazn-policy>
                  </application></applications></policy-store>
                </jazn-data>
                """);

        app = new AdfApplicationParser().parse(root);
    }

    @Test
    void resolvesEachPublishedPathFromUrlThroughToTheDatabaseTable() {
        Endpoint employees = endpoint("/v1/Employees");

        assertThat(employees.isFullyResolved()).isTrue();
        assertThat(employees.module().simpleName()).isEqualTo("HrModule");
        assertThat(employees.viewObject().fqn()).isEqualTo("com.example.views.EmployeesView");
        assertThat(employees.entityObject().fqn()).isEqualTo("com.example.entities.Employees");
        assertThat(employees.table()).isEqualTo("EMPLOYEES");
    }

    @Test
    void selectsTheCollectionMatchingThePathWhenPathsShareAResourceDefinition() {
        // /v1/Employees and /v1/EmployeesJobs are two endpoints defined by one file; each selects
        // its own collection binding.
        assertThat(endpoint("/v1/Employees").collection()).isEqualTo("Employees");
        assertThat(endpoint("/v1/EmployeesJobs").collection()).isEqualTo("EmployeesJobs");
    }

    @Test
    void readsRestOperationSignaturesWithParameterTypes() {
        assertThat(endpoint("/v1/Employees").operations())
                .singleElement()
                .satisfies(op -> {
                    assertThat(op.name()).isEqualTo("calculateEmployees");
                    assertThat(op.viewObjectMethod()).isTrue();
                    assertThat(op.signature()).isEqualTo("calculateEmployees(firstName: String)");
                });
    }

    @Test
    void mapsRestServicePermissionGrantsOntoEndpoints() {
        assertThat(endpoint("/v1/Employees").grantedActions())
                .containsExactly("get", "create", "update", "delete");
        assertThat(endpoint("/v1/EmployeesJobs").grantedActions()).containsExactly("get");
        assertThat(endpoint("/v1/Employees").principals()).containsExactly("redsam");
    }

    @Test
    void flagsResourcesWithNoGrantSoTheyAreNotPublishedOpen() {
        // Departments has no permission entry. In ADF that makes it unreachable, so a generator
        // must deny it rather than emit permitAll.
        Endpoint departments = endpoint("/v1/Departments");
        assertThat(departments.isUngranted()).isTrue();
        assertThat(departments.isFullyResolved()).isTrue();
    }

    @Test
    void readsHandWrittenSqlFromExpertModeViewObjects() {
        // Expert-mode views leave SelectList and FromList empty and put the statement in a
        // SQLQuery block — often joining tables the entity model never mentions.
        ViewObject departments = app.viewObjects().get("com.example.views.DepartmentsView");

        assertThat(departments.customQuery()).isTrue();
        assertThat(departments.isReadOnly()).isTrue();
        assertThat(departments.selectList()).isNull();
        assertThat(departments.sql())
                .contains("FROM DEPARTMENTS d, LOCATIONS l")
                .contains("l.STREET_ADDRESS");
    }

    @Test
    void normalisesGeneratedSqlSpreadAcrossLines() {
        assertThat(app.viewObjects().get("com.example.views.EmployeesView").sql())
                .isEqualTo("SELECT Employees.EMPLOYEE_ID, Employees.HIRE_DATE FROM EMPLOYEES Employees");
    }

    @Test
    void derivesAssociationCardinalityFromTheDeclaredAccessorType() {
        // ADF encodes cardinality in the accessor's type: RowIterator walks many rows, EntityImpl
        // points at one. This decides @OneToMany versus @ManyToOne in the generated entity.
        EntityObject employees = app.entities().get("com.example.entities.Employees");

        assertThat(employees.accessors())
                .extracting(EntityObject.Accessor::name, EntityObject.Accessor::cardinality)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("Reports", EntityObject.Cardinality.TO_MANY),
                        org.assertj.core.api.Assertions.tuple("Manager", EntityObject.Cardinality.TO_ONE));
    }

    @Test
    void readsDatabaseConstraintsOutOfTheDesignTimeBlock() {
        EntityObject employees = app.entities().get("com.example.entities.Employees");

        assertThat(employees.constraints())
                .extracting(EntityObject.Constraint::name, EntityObject.Constraint::kind)
                .containsExactly(
                        org.assertj.core.api.Assertions.tuple("EmpEmpIdPk", EntityObject.Constraint.Kind.PRIMARY_KEY),
                        org.assertj.core.api.Assertions.tuple("EmpDeptFk", EntityObject.Constraint.Kind.FOREIGN_KEY),
                        org.assertj.core.api.Assertions.tuple("EmpSalaryMin", EntityObject.Constraint.Kind.CHECK));

        assertThat(employees.constraints().get(1).referencedKey()).isEqualTo("DEPT_ID_PK");
        assertThat(employees.constraints().get(2).checkCondition()).isEqualTo("salary > 0");
    }

    @Test
    void marksValidationRulesThatDelegateToGroovyExpressions() {
        EntityObject employees = app.entities().get("com.example.entities.Employees");

        assertThat(employees.validators()).singleElement().satisfies(v -> {
            assertThat(v.name()).isEqualTo("HireDateRule0");
            assertThat(v.onAttribute()).isEqualTo("HireDate");
            // Delegates to a Groovy expression, so it needs translation rather than an annotation.
            assertThat(v.expression()).isTrue();
        });
    }

    @Test
    void recordsCustomJavaThatMetadataCannotDescribe() {
        assertThat(app.customImplementations()).containsExactly(
                "com.example.entities.Employees -> com.example.entities.EmployeesImpl",
                "com.example.views.EmployeesView -> com.example.views.EmployeesViewImpl");
    }

    // --- helpers -----------------------------------------------------------------------

    private Endpoint endpoint(String url) {
        List<Endpoint> endpoints = app.endpoints();
        return endpoints.stream()
                .filter(e -> e.url().equals(url))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no endpoint " + url + " in " + endpoints.stream().map(Endpoint::url).toList()));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
