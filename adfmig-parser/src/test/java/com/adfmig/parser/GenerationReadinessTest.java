package com.adfmig.parser;

import com.adfmig.core.analysis.GenerationReadiness;
import com.adfmig.core.model.AdfApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Readiness asks a stricter question than complexity: does the parsed model hold enough to emit
 * working code at all? A component can be trivially simple and still impossible to generate.
 */
class GenerationReadinessTest {

    @TempDir
    Path root;

    @Test
    void passesAnEntityThatHasEverythingAMappingNeeds() throws IOException {
        entityWithAssociation();
        assertThat(check("com.example.Employees").blockers()).isEmpty();
    }

    @Test
    void blocksAnEntityWhoseAssociationIsMissing() throws IOException {
        // The accessor states cardinality but not the target entity or the join columns; without
        // the association there is no relationship to generate.
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <AccessorAttribute Name="Jobs" Association="com.example.assocs.MissingAssoc"
                                     Type="oracle.jbo.server.EntityImpl"/>
                </Entity>
                """);

        assertThat(check("com.example.Employees").blockers())
                .containsExactly("association not found for Jobs");
    }

    @Test
    void blocksAnEntityWhoseAssociationIsMissingItsJoinColumns() throws IOException {
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <AccessorAttribute Name="Jobs" Association="com.example.assocs.EmpJobFkAssoc"
                                     Type="oracle.jbo.server.EntityImpl"/>
                </Entity>
                """);
        write("Model/src/com/example/assocs/EmpJobFkAssoc.xml", """
                <Association xmlns="http://xmlns.oracle.com/bc4j" Name="EmpJobFkAssoc">
                  <AssociationEnd Name="Jobs" Cardinality="1" Source="true" Owner="com.example.Jobs"/>
                  <AssociationEnd Name="Employees" Cardinality="-1" Owner="com.example.Employees"/>
                </Association>
                """);

        assertThat(check("com.example.Employees").blockers())
                .containsExactly("incomplete association for Jobs");
    }

    @Test
    void blocksAnEntityWithNoPrimaryKey() throws IOException {
        write("Model/src/com/example/Audit.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Audit" DBObjectName="AUDIT_LOG">
                  <Attribute Name="Message" ColumnName="MSG" Type="java.lang.String"/>
                </Entity>
                """);

        assertThat(check("com.example.Audit").blockers()).contains("no primary key");
    }

    @Test
    void readsBothEndsOfAnAssociationSoARelationshipCanBeMapped() throws IOException {
        entityWithAssociation();
        var association = parse().associations().get("com.example.assocs.EmpJobFkAssoc");

        assertThat(association.isMappable()).isTrue();
        // ADF states cardinality per end: 1 is the single side, -1 the collection side. This is
        // what decides @ManyToOne against @OneToMany.
        assertThat(association.toOneEnd()).hasValueSatisfying(end -> {
            assertThat(end.owner()).isEqualTo("com.example.Jobs");
            assertThat(end.attributes()).containsExactly("JobId");
        });
        assertThat(association.toManyEnd()).hasValueSatisfying(end -> {
            assertThat(end.owner()).isEqualTo("com.example.Employees");
            assertThat(end.attributes()).containsExactly("JobId");
            assertThat(end.foreignKey()).isEqualTo("com.example.Employees.EmpJobFk");
        });
    }

    // --- helpers -----------------------------------------------------------------------

    private void entityWithAssociation() throws IOException {
        write("Model/src/com/example/Employees.xml", """
                <Entity xmlns="http://xmlns.oracle.com/bc4j" Name="Employees" DBObjectName="EMPLOYEES">
                  <Attribute Name="Id" ColumnName="ID" Type="java.lang.Integer" PrimaryKey="true"/>
                  <Attribute Name="JobId" ColumnName="JOB_ID" Type="java.lang.String"/>
                  <AccessorAttribute Name="Jobs" Association="com.example.assocs.EmpJobFkAssoc"
                                     Type="oracle.jbo.server.EntityImpl"/>
                </Entity>
                """);
        write("Model/src/com/example/assocs/EmpJobFkAssoc.xml", """
                <Association xmlns="http://xmlns.oracle.com/bc4j" Name="EmpJobFkAssoc">
                  <AssociationEnd Name="Jobs" Cardinality="1" Source="true" Owner="com.example.Jobs">
                    <AttrArray Name="Attributes"><Item Value="com.example.Jobs.JobId"/></AttrArray>
                  </AssociationEnd>
                  <AssociationEnd Name="Employees" Cardinality="-1" Owner="com.example.Employees">
                    <DesignTime><Attr Name="_foreignKey" Value="com.example.Employees.EmpJobFk"/></DesignTime>
                    <AttrArray Name="Attributes"><Item Value="com.example.Employees.JobId"/></AttrArray>
                  </AssociationEnd>
                </Association>
                """);
    }

    private AdfApplication parse() throws IOException {
        return new AdfApplicationParser().parse(root);
    }

    private GenerationReadiness check(String fqn) throws IOException {
        List<GenerationReadiness> checks = GenerationReadiness.of(parse());
        return checks.stream()
                .filter(c -> c.fqn().equals(fqn))
                .findFirst()
                .orElseThrow(() -> new AssertionError("not checked: " + fqn));
    }

    private void write(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
