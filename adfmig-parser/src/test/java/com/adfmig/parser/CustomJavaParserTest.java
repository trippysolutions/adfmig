package com.adfmig.parser;

import com.adfmig.core.model.CustomJavaClass;
import com.adfmig.core.model.CustomMethod;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Knowing a custom ADF class exists is enough to warn about; it is not enough to price. An override
 * applying a view criteria and counting rows is an afternoon; one walking a row set through the ADF
 * transaction is a week. These cases pin the distinction.
 */
class CustomJavaParserTest {

    @TempDir
    Path root;

    private final CustomJavaParser parser = new CustomJavaParser();

    @Test
    void recognisesWhichAdfComponentTheClassBelongsTo() throws IOException {
        CustomJavaClass parsed = parse("EmployeesImpl.java", """
                package com.example.entities;
                import oracle.jbo.server.EntityImpl;
                public class EmployeesImpl extends EntityImpl {}
                """);

        assertThat(parsed.kind()).isEqualTo(CustomJavaClass.Kind.ENTITY);
        assertThat(parsed.fqn()).isEqualTo("com.example.entities.EmployeesImpl");
    }

    @Test
    void treatsAWritePathOverrideAsARewrite() throws IOException {
        // doDML runs inside ADF's own write path. Nothing outside ADF calls it, so the behaviour
        // has to be re-hosted rather than moved.
        CustomMethod method = parse("EmployeesImpl.java", """
                package com.example;
                import oracle.jbo.server.EntityImpl;
                public class EmployeesImpl extends EntityImpl {
                    protected void doDML(int operation, TransactionEvent e) {
                        if (getAttribute("Version") != null) { setAttribute("Version", 1); }
                        super.doDML(operation, e);
                    }
                }
                """).methods().get(0);

        assertThat(method.lifecycleOverride()).isEqualTo("doDML");
        assertThat(method.effort()).isEqualTo(CustomMethod.Effort.MANUAL);
    }

    @Test
    void namesAShapeThatHasADirectEquivalent() throws IOException {
        CustomMethod method = parse("EmployeesViewImpl.java", """
                package com.example;
                import oracle.jbo.server.ViewObjectImpl;
                public class EmployeesViewImpl extends ViewObjectImpl {
                    public String calculateEmployees(String first, String last) {
                        var vc = this.getViewCriteria("EmployeesViewCriteria");
                        ensureVariableManager().setVariableValue("firstNameVar", first);
                        this.applyViewCriteria(vc);
                        this.executeQuery();
                        return Long.toString(this.getEstimatedRowCount());
                    }
                }
                """).methods().get(0);

        assertThat(method.recognisedPattern()).contains("row count");
        assertThat(method.effort()).isEqualTo(CustomMethod.Effort.MECHANICAL);
    }

    @Test
    void treatsAnOverrideThatOnlyCallsSuperAsCustomisingNothing() throws IOException {
        // JDeveloper writes these when a developer asks to override a hook and then changes
        // nothing. Reporting them buries the methods that matter.
        CustomMethod method = parse("EmployeesImpl.java", """
                package com.example;
                import oracle.jbo.server.EntityImpl;
                public class EmployeesImpl extends EntityImpl {
                    public void lock() { super.lock(); }
                }
                """).methods().get(0);

        assertThat(method.recognisedPattern()).startsWith("an override that only calls super");
        assertThat(method.lifecycleOverride()).isNull();
    }

    @Test
    void separatesAMethodThatMapsOntoSpringFromOneThatDoesNot() throws IOException {
        CustomJavaClass parsed = parse("MixedImpl.java", """
                package com.example;
                import oracle.jbo.server.ViewObjectImpl;
                public class MixedImpl extends ViewObjectImpl {
                    public void filter(String name) {
                        setWhereClause("NAME = :n");
                        executeQuery();
                    }
                    public void rebuild() {
                        var iterator = createRowSetIterator("x");
                        getDBTransaction().commit();
                    }
                }
                """);

        assertThat(parsed.methods().get(0).effort()).isEqualTo(CustomMethod.Effort.ASSISTED);
        // Reaching for the transaction means the method is doing ADF's own work.
        assertThat(parsed.methods().get(1).effort()).isEqualTo(CustomMethod.Effort.MANUAL);
        assertThat(parsed.needingRewrite()).hasSize(1);
    }

    @Test
    void reportsAClassThatTouchesNothingAdfSpecificAsCarryingNothing() throws IOException {
        assertThat(parse("PlainImpl.java", """
                package com.example;
                import oracle.jbo.server.EntityImpl;
                public class PlainImpl extends EntityImpl {
                    public String getName() { return name; }
                    public void setName(String name) { this.name = name; }
                }
                """).isTrivial()).isTrue();
    }

    @Test
    void survivesJavaItCannotParse() throws IOException {
        Path file = root.resolve("Broken.java");
        Files.writeString(file, "public class Broken { this is not java");

        // Unparseable source is a finding for the report, not a reason to abandon the scan.
        assertThat(parser.parse(file, "Broken.java")).isEmpty();
    }

    private CustomJavaClass parse(String fileName, String source) throws IOException {
        Path file = root.resolve(fileName);
        Files.writeString(file, source);
        return parser.parse(file, fileName).orElseThrow();
    }
}
