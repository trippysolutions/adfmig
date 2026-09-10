package com.adfmig.cli;

import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.io.PrintStream;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Explains the ADF terms the tool reports.
 *
 * <p>The output names entity objects, view links, application modules and expert-mode queries
 * because those are what the source calls them, and renaming them would make the report harder to
 * check against the application. But the person reading it is often a Spring developer who has
 * never opened JDeveloper, and asking them to search Oracle's documentation mid-assessment is a
 * poor use of their afternoon.
 *
 * <p>Each entry says what the thing is, and what it becomes.
 */
@Command(
        name = "glossary",
        mixinStandardHelpOptions = true,
        description = "Explain the ADF terms that appear in the output.")
public final class GlossaryCommand implements Callable<Integer> {

    @Parameters(paramLabel = "TERM", arity = "0..1",
            description = "A term to look up. Omit for all of them.")
    private String term;

    private record Entry(String adf, String becomes, String explanation) {}

    private static final Map<String, Entry> TERMS = terms();

    @Override
    public Integer call() {
        PrintStream out = System.out;

        if (term == null) {
            out.println();
            out.println("  " + Terminal.bold("ADF terms, and what each becomes"));
            out.println();
            TERMS.values().forEach(entry -> print(out, entry, false));
            out.println("  " + Terminal.dim("adfmig glossary <term>   for the longer explanation"));
            out.println();
            return 0;
        }

        Entry found = TERMS.get(term.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", ""));
        if (found == null) {
            out.printf("%n  No entry for '%s'. Run 'adfmig glossary' for all of them.%n%n", term);
            return 1;
        }
        out.println();
        print(out, found, true);
        return 0;
    }

    private void print(PrintStream out, Entry entry, boolean full) {
        out.printf("  %-26s %s%n", Terminal.bold(entry.adf()),
                Terminal.dim("becomes  ") + entry.becomes());
        if (full) {
            out.println();
            for (String line : entry.explanation().split("\n")) {
                out.println("    " + line);
            }
            out.println();
        }
    }

    private static Map<String, Entry> terms() {
        Map<String, Entry> terms = new LinkedHashMap<>();

        terms.put("entityobject", new Entry("Entity object", "a JPA @Entity", """
                One database table, with its columns, constraints and business rules.
                The closest thing ADF has to a JPA entity, and it maps almost directly:
                columns, types, nullability, keys and relationships all carry across.

                What does not carry across is anything written in Java or Groovy on top
                of it. Those are reported rather than guessed at."""));

        terms.put("viewobject", new Entry("View object", "a repository, or a native query", """
                A query. Either built over entity objects, in which case it becomes a
                Spring Data repository and can be written to, or carrying hand-written
                SQL, in which case it is read-only and the SQL is carried over unchanged.

                A view object frequently joins several entities. Only the first becomes
                the entity behind it; attributes from the others are reported, since they
                cannot be read from it."""));

        terms.put("applicationmodule", new Entry("Application module", "a @Service", """
                ADF's service layer. It owns the transaction: the view instances it
                exposes commit or roll back together.

                That is why the generated controllers call a service rather than a
                repository. Without it each repository call would commit on its own, so
                an operation touching two collections could half-succeed where in ADF it
                could not."""));

        terms.put("viewusage", new Entry("View instance", "one endpoint", """
                A named use of a view object inside an application module. A module can
                expose the same view object twice — a full list and a lookup, say — and
                they are two distinct collections.

                REST resources bind to these names, not to view object classes, which is
                what connects a URL to its query."""));

        terms.put("association", new Entry("Association", "@ManyToOne / @OneToMany", """
                A relationship between two entity objects, carrying the columns they join
                on and the cardinality of each end.

                The entity's accessor names the relationship but not the join columns or
                the target, so an association that is missing means the relationship
                cannot be mapped."""));

        terms.put("viewlink", new Entry("View link", "a nested endpoint", """
                A master-detail relationship between two queries. ADF serves the detail
                beneath the parent row, at /{parent}/{id}/child/{name}, which is part of
                the published contract even though no resource definition mentions it."""));

        terms.put("viewcriteria", new Entry("View criteria", "a JPA Specification", """
                A named, declarative filter: an attribute, an operator, a value or bind
                variable, joined by a conjunction. One of the few parts of ADF that maps
                onto Spring without interpretation.

                Optional conditions are skipped when their value is null, which is what
                ADF does with an unfilled search field."""));

        terms.put("bc", new Entry("ADF BC REST", "a preserved contract", """
                A REST API ADF publishes from its business components, declared in
                ResourceRegistry.rpx. Applications that have one are the cheapest and
                safest to migrate: the URLs, the operations, the page sizes and the
                security grants are all declared, so the replacement can keep the
                contract exactly and be tested against the original."""));

        terms.put("pagedefinition", new Entry("Page definition", "the API a screen needs", """
                Looks like part of the user interface and is in fact a record of what a
                screen asked of the model: which view instance it read, at what page
                size, which attributes it displayed.

                For an ADF Faces application, which publishes nothing over HTTP, this is
                the only specification of what an API has to serve."""));

        terms.put("groovy", new Entry("Groovy expression", "a stub and the original", """
                ADF allows arbitrary Groovy in default values and validation rules,
                collected per component in a .bcs file and annotated with its role.

                Only one idiom is translated, reading a database sequence, because it has
                an exact equivalent. Everything else is reproduced verbatim beside an
                empty stub: a guess at business logic that looks finished is worse than
                an obvious gap."""));

        terms.put("expertmode", new Entry("Expert-mode query", "a native query", """
                A view object whose SQL the developer wrote by hand. It is carried over
                exactly as written and never rewritten — expert-mode views routinely join
                tables the entity model never mentions, and changing the query would
                change behaviour nothing describes."""));

        terms.put("changeindicator", new Entry("Change indicator", "@Version, if the type allows", """
                A column ADF maintains on write and compares on the next update, which is
                how it detects that a row changed underneath a user.

                JPA accepts only integral and timestamp types as a version. ADF change
                indicators are commonly NUMBER, which maps to BigDecimal and is not one,
                so those are mapped read-only and the loss of locking is reported."""));

        terms.put("jazndata", new Entry("jazn-data.xml", "Spring Security", """
                The authorization policy. For an ADF BC REST application the mapping is
                direct: a grant names the resource and the HTTP actions allowed on it.

                Endpoints with no grant were unreachable in ADF, so they are generated
                denied. Publishing them open would turn a closed door into an open one."""));

        terms.put("taskflow", new Entry("Task flow", "not migrated", """
                ADF's navigation between screens. It has no equivalent in a REST service
                and is not carried over; its presence is a signal that the front end
                needs rebuilding."""));

        return terms;
    }
}
