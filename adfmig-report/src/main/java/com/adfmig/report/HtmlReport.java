package com.adfmig.report;

import com.adfmig.core.analysis.ApplicationAssessment;
import com.adfmig.core.analysis.ArtifactAssessment;
import com.adfmig.core.analysis.EffortModel;
import com.adfmig.core.analysis.MigrationClass;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.SecurityPolicy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static com.adfmig.report.Html.days;
import static com.adfmig.report.Html.escape;

/**
 * Renders a migration assessment as one self-contained HTML file.
 *
 * <p>This is the deliverable a customer receives, so it is written to be argued with: every
 * number shows the formula and the findings behind it, and the limits of the estimate are stated
 * rather than buried.
 */
public final class HtmlReport {

    private static final int TOP_ARTIFACTS = 40;

    public String render(ApplicationAssessment assessment) {
        StringBuilder out = new StringBuilder(64_000);
        out.append("<!doctype html>\n<html lang=\"en\">\n<head>\n")
                .append("<meta charset=\"utf-8\">\n")
                .append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n")
                .append("<title>Migration assessment — ").append(escape(assessment.name())).append("</title>\n")
                .append("<style>\n").append(ReportStyle.CSS).append("</style>\n")
                .append("</head>\n<body>\n<div class=\"page\">\n");

        header(out, assessment);
        headline(out, assessment);
        profile(out, assessment);
        effort(out, assessment);
        composition(out, assessment);
        endpoints(out, assessment);
        security(out, assessment);
        risks(out, assessment);
        artifacts(out, assessment);
        method(out, assessment);

        out.append("</div>\n</body>\n</html>\n");
        return out.toString();
    }

    private void header(StringBuilder out, ApplicationAssessment a) {
        out.append("<header>\n<div class=\"eyebrow\">Oracle ADF to Spring Boot &middot; migration assessment</div>\n")
                .append("<h1>").append(escape(a.name())).append("</h1>\n")
                .append("<div class=\"sub\">").append(escape(a.application().root()))
                .append(" &middot; ").append(LocalDate.now()).append("</div>\n</header>\n");
    }

    private void headline(StringBuilder out, ApplicationAssessment a) {
        out.append("<div class=\"headline\">\n");
        stat(out, days(a.backendDays()), "person-days, backend");
        stat(out, String.valueOf(a.application().endpoints().size()), "published endpoints");
        stat(out, String.valueOf(a.artifacts().size()), "business components");
        stat(out, String.valueOf(a.application().entities().size()), "database tables mapped");
        out.append("</div>\n");

        if (a.pageDefinitions() > 0) {
            out.append("<p class=\"note\">A further <b>").append(days(a.frontEndRebuildDays()))
                    .append("</b> person-days is estimated for rebuilding the ADF Faces front end (")
                    .append(a.pageDefinitions()).append(" page definitions). ")
                    .append("That is quoted separately: it is a different project, with different people, ")
                    .append("and folding it into a backend figure makes both indefensible.</p>\n");
        }
    }

    private void stat(StringBuilder out, String value, String label) {
        out.append("<div class=\"stat\"><div class=\"value\">").append(escape(value))
                .append("</div><div class=\"label\">").append(escape(label)).append("</div></div>\n");
    }

    private void profile(StringBuilder out, ApplicationAssessment a) {
        out.append("<h2>Migration profile</h2>\n");
        boolean rest = a.application().scan().hasExistingRestContract();
        boolean faces = a.application().scan().hasAdfFacesConsumers();

        if (rest) {
            out.append("<div class=\"callout info\"><h3>Already publishes REST</h3>")
                    .append("<p>The application's URL structure, operation signatures and security grants are ")
                    .append("all declared in its own metadata. The replacement can therefore preserve the ")
                    .append("existing contract exactly, and each endpoint can be verified by running the same ")
                    .append("request against the old service and the new one and comparing the responses. ")
                    .append("This is the cheapest and lowest-risk class of ADF migration.</p></div>\n");
        }
        if (faces) {
            out.append("<div class=\"callout\"><h3>Bound to an ADF Faces user interface</h3>")
                    .append("<p>ADF Faces binds to the business model in-process through page definitions, ")
                    .append("not over HTTP. Migrating the model alone leaves the existing screens with ")
                    .append("nothing to bind to, so the front end has to be rebuilt against the new REST ")
                    .append("API. The page definitions do, however, record exactly which operations each ")
                    .append("screen uses, which is the specification for that API.</p></div>\n");
        }
        if (!rest && !faces) {
            out.append("<p>No REST contract and no ADF Faces consumer was found. This is characteristic of ")
                    .append("a shared library imported by other applications; check which applications ")
                    .append("depend on it before migrating.</p>\n");
        }
    }

    private void effort(StringBuilder out, ApplicationAssessment a) {
        EffortModel model = a.effort();
        out.append("<h2>Estimated effort</h2>\n<div class=\"scroll\"><table>\n")
                .append("<thead><tr><th>Work</th><th>Basis</th><th class=\"num\">Days</th></tr></thead>\n<tbody>\n");

        Map<MigrationClass, Double> byClass = a.daysByClass();
        Map<MigrationClass, Integer> counts = a.countsByClass();
        byClass.forEach((migrationClass, d) -> row(out,
                "Components &mdash; " + escape(migrationClass.description()),
                counts.getOrDefault(migrationClass, 0) + " artifacts, " + escape(model.formula(migrationClass)),
                d));

        row(out, "Security configuration",
                a.principals().size() + " principal(s) &times; " + days(model.perRole())
                        + " + " + days(model.securityBase()) + " base", a.securityDays());
        row(out, "Contract tests",
                a.application().endpoints().size() + " endpoints &times; " + days(model.perEndpointTest()),
                a.contractTestDays());
        row(out, "Project setup", "scaffolding, build, pipeline, configuration", a.setupDays());

        out.append("</tbody>\n<tfoot><tr><td colspan=\"2\"><b>Backend total</b></td><td class=\"num\"><b>")
                .append(days(a.backendDays())).append("</b></td></tr></tfoot>\n</table></div>\n");

        out.append("<p class=\"note\"><b>These weights are not calibrated.</b> They are a defensible ")
                .append("starting point, not a measurement: no migration has yet been completed with this ")
                .append("tool, so nothing here derives from recorded actuals. The formula is shown above so ")
                .append("it can be challenged and adjusted, and it should be replaced with measured effort ")
                .append("after the first project.</p>\n");
    }

    private void row(StringBuilder out, String work, String basis, double value) {
        out.append("<tr><td>").append(work).append("</td><td class=\"sub\">").append(basis)
                .append("</td><td class=\"num\">").append(days(value)).append("</td></tr>\n");
    }

    private void composition(StringBuilder out, ApplicationAssessment a) {
        out.append("<h2>What can be generated</h2>\n");
        Map<MigrationClass, Integer> counts = a.countsByClass();
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        if (total == 0) {
            out.append("<p>No business components found.</p>\n");
            return;
        }

        out.append("<div class=\"bar\">");
        counts.forEach((migrationClass, count) -> out.append("<span class=\"").append(migrationClass)
                .append("\" style=\"width:").append(Html.percent(count, total)).append("%\"></span>"));
        out.append("</div>\n<div class=\"legend\">");
        counts.forEach((migrationClass, count) -> out.append("<div><span class=\"swatch ")
                .append(migrationClass).append("\" style=\"background:currentColor\"></span><b>")
                .append(count).append("</b> ").append(escape(migrationClass.description())).append("</div>"));
        out.append("</div>\n");

        int generated = counts.getOrDefault(MigrationClass.AUTO, 0)
                + counts.getOrDefault(MigrationClass.ASSISTED, 0);
        out.append("<p class=\"note\">").append(Html.percent(generated, total))
                .append("% of components are produced by the generator, in whole or as a starting point. ")
                .append("The remainder is hand-written code against the ADF runtime, which has no ")
                .append("mechanical equivalent and is rewritten by a person against the generated model.</p>\n");
    }

    private void endpoints(StringBuilder out, ApplicationAssessment a) {
        List<Endpoint> endpoints = a.application().endpoints();
        if (endpoints.isEmpty()) return;

        out.append("<h2>Published endpoints</h2>\n<div class=\"scroll\"><table>\n")
                .append("<thead><tr><th>URL</th><th>Backing query</th><th>Table</th>")
                .append("<th>Granted actions</th></tr></thead>\n<tbody>\n");
        for (Endpoint e : endpoints) {
            out.append("<tr><td class=\"mono\">").append(escape(e.url())).append("</td><td class=\"mono sub\">")
                    .append(escape(e.viewObject() == null ? "unresolved" : e.viewObject().simpleName()))
                    .append("</td><td class=\"mono sub\">")
                    .append(escape(e.table() == null ? "read-only view" : e.table()))
                    .append("</td><td class=\"sub\">")
                    .append(e.isUngranted()
                            ? "<span class=\"tag MANUAL\">no grant</span>"
                            : escape(String.join(", ", e.grantedActions())))
                    .append("</td></tr>\n");
        }
        out.append("</tbody></table></div>\n");
    }

    private void security(StringBuilder out, ApplicationAssessment a) {
        out.append("<h2>Security</h2>\n");

        List<Endpoint> ungranted = a.ungrantedEndpoints();
        List<SecurityPolicy.Grant> userGrants = a.userGrants();
        boolean fileStore = a.application().policies().stream().anyMatch(p -> !p.users().isEmpty());

        if (ungranted.isEmpty() && userGrants.isEmpty() && !fileStore) {
            out.append("<p>No issues found in the declared authorization policy.</p>\n");
            return;
        }

        if (!ungranted.isEmpty()) {
            out.append("<div class=\"callout\"><h3>").append(ungranted.size())
                    .append(" endpoint(s) carry no grant</h3><p>In ADF an ungranted resource is ")
                    .append("unreachable. The generated application must deny these explicitly; emitting ")
                    .append("them open would turn a closed door into an open one.</p><p class=\"mono sub\">");
            ungranted.forEach(e -> out.append(escape(e.url())).append("<br>"));
            out.append("</p></div>\n");
        }
        if (!userGrants.isEmpty()) {
            out.append("<div class=\"callout\"><h3>Permissions granted to named users</h3>")
                    .append("<p>Grants are made to individual users rather than to roles. Roles have to be ")
                    .append("introduced before these can become authorities in the migrated application, ")
                    .append("and the mapping is a decision for the customer, not the tool.</p></div>\n");
        }
        if (fileStore) {
            out.append("<div class=\"callout\"><h3>Users defined in the policy file</h3>")
                    .append("<p>The application authenticates against a file rather than a directory. ")
                    .append("The migrated application needs a real identity provider, and the credentials ")
                    .append("in that file should be treated as compromised and rotated.</p></div>\n");
        }
    }

    private void risks(StringBuilder out, ApplicationAssessment a) {
        int dead = a.unreferencedComponents();
        long expertSql = a.application().viewObjects().values().stream()
                .filter(com.adfmig.core.model.ViewObject::customQuery).count();
        int custom = a.application().customImplementations().size();
        if (dead == 0 && expertSql == 0 && custom == 0) return;

        out.append("<h2>Findings</h2>\n<ul>\n");
        if (dead > 0) {
            out.append("<li><b>").append(dead).append(" component(s) are referenced by nothing.</b> ")
                    .append("Confirm before deleting, but unreferenced components are usually dead code, ")
                    .append("and removing them reduces the migration rather than adding to it.</li>\n");
        }
        if (expertSql > 0) {
            out.append("<li><b>").append(expertSql).append(" view object(s) carry hand-written SQL.</b> ")
                    .append("These cannot be regenerated from the entity model and frequently join tables ")
                    .append("the model never mentions. The SQL is carried across as a native query.</li>\n");
        }
        if (custom > 0) {
            out.append("<li><b>").append(custom).append(" component(s) have hand-written Java.</b> ")
                    .append("Code calling the ADF runtime has no mechanical equivalent and is rewritten ")
                    .append("against the generated model.</li>\n");
        }
        out.append("</ul>\n");
    }

    private void artifacts(StringBuilder out, ApplicationAssessment a) {
        List<ArtifactAssessment> artifacts = a.artifacts();
        if (artifacts.isEmpty()) return;

        out.append("<h2>Components by cost</h2>\n<div class=\"scroll\"><table>\n")
                .append("<thead><tr><th>Component</th><th>Kind</th><th>Migration</th>")
                .append("<th class=\"num\">Score</th><th class=\"num\">Days</th><th>Why</th></tr></thead>\n<tbody>\n");

        artifacts.stream().limit(TOP_ARTIFACTS).forEach(artifact -> out
                .append("<tr><td class=\"mono\">").append(escape(artifact.simpleName()))
                .append("</td><td class=\"sub\">").append(escape(label(artifact.kind())))
                .append("</td><td><span class=\"tag ").append(artifact.migrationClass()).append("\">")
                .append(artifact.migrationClass().name().toLowerCase()).append("</span></td>")
                .append("<td class=\"num\">").append(artifact.complexity())
                .append("</td><td class=\"num\">").append(days(artifact.effortDays()))
                .append("</td><td class=\"drivers\">")
                .append(artifact.drivers().isEmpty() ? "declarative only"
                        : escape(String.join(" &middot; ", artifact.drivers())).replace("&amp;middot;", "&middot;"))
                .append("</td></tr>\n"));

        out.append("</tbody></table></div>\n");
        if (artifacts.size() > TOP_ARTIFACTS) {
            out.append("<p class=\"note\">Showing the ").append(TOP_ARTIFACTS)
                    .append(" most expensive of ").append(artifacts.size())
                    .append(" components. The full list is in the JSON export.</p>\n");
        }
    }

    private static String label(ArtifactAssessment.Kind kind) {
        return switch (kind) {
            case ENTITY_OBJECT -> "entity object";
            case VIEW_OBJECT -> "view object";
            case APPLICATION_MODULE -> "application module";
            case REST_ENDPOINT -> "endpoint";
        };
    }

    private void method(StringBuilder out, ApplicationAssessment a) {
        out.append("<h2>Method</h2>\n")
                .append("<p>Every figure in this report is derived from the application's own metadata: ")
                .append("entity and view object definitions, application module configurations, REST ")
                .append("resource declarations and the authorization policy. Nothing is sampled or ")
                .append("extrapolated, and no code was executed.</p>\n")
                .append("<p>Complexity is scored from specific findings — hand-written SQL, custom Java, ")
                .append("Groovy expressions, association cardinality, composite keys — and each component's ")
                .append("score lists the findings that produced it. Scores are relative within this ")
                .append("estate; they rank cost, they do not measure it.</p>\n")
                .append("<p>Database credentials were not read. Where credential material was found in ")
                .append("source, its location is reported and its value is not.</p>\n")
                .append("<footer>Generated by adfmig ").append(escape(version()))
                .append(" &middot; ").append(LocalDate.now())
                .append(" &middot; estimates are uncalibrated pending a completed migration.</footer>\n");
    }

    private static String version() {
        return com.adfmig.core.Version.current();
    }
}
