package com.adfmig.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADF metadata does not record its own package, so a component's fully qualified name is
 * reconstructed from its path. Getting this wrong is silent and expensive: references stop
 * resolving, and every component in the application then looks like dead code.
 */
class FqnTest {

    @Test
    void derivesThePackageFromTheSourceRoot() {
        assertThat(Fqn.fromPath("Model/src/com/example/entities/Employees.xml"))
                .isEqualTo("com.example.entities.Employees");
    }

    @Test
    void readsBindingMetadataFromTheAdfmSourceRoot() {
        assertThat(Fqn.fromPath("Model/adfmsrc/com/example/rest/v1/HrModule_EmployeesViewResources.xml"))
                .isEqualTo("com.example.rest.v1.HrModule_EmployeesViewResources");
    }

    @Test
    void usesTheDeepestSourceRootWhenProjectsAreNestedInsideOne() {
        // Repositories nest whole projects under a source directory. Matching the outer "/src/"
        // yields "Pooling.Model.src.demo.Employees" — a name nothing references.
        assertThat(Fqn.fromPath("src/Pooling/Model/src/demo/Employees.xml"))
                .isEqualTo("demo.Employees");
        assertThat(Fqn.fromPath("src/RowCurrency/Model/adfmsrc/com/example/Registry.rpx"))
                .isEqualTo("com.example.Registry");
    }

    @Test
    void returnsNullWhenTheFileSitsOutsideAnySourceRoot() {
        assertThat(Fqn.fromPath("META-INF/jazn-data.xml")).isNull();
        assertThat(Fqn.fromPath("Employees.xml")).isNull();
    }
}
