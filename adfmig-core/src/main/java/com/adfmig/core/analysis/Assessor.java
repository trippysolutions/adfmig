package com.adfmig.core.analysis;

import com.adfmig.core.model.AdfApplication;
import com.adfmig.core.model.ApplicationModule;
import com.adfmig.core.model.CustomJavaClass;
import com.adfmig.core.model.CustomMethod;
import com.adfmig.core.model.Endpoint;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.Types;
import com.adfmig.core.model.ViewObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores each artifact in an application for migration complexity and cost.
 *
 * <p>Scoring works only from facts already parsed out of the application's own metadata. Every
 * point added is recorded with the finding that caused it, so an estimate can be traced back to
 * the file it came from.
 *
 * <p>Scores are relative within an estate, not absolute. Their purpose is to rank what is cheap
 * against what is expensive and to separate what a generator handles from what a person must.
 */
public final class Assessor {

    private final EffortModel effort;

    public Assessor(EffortModel effort) {
        this.effort = effort;
    }

    public ApplicationAssessment assess(AdfApplication application) {
        List<ArtifactAssessment> artifacts = new ArrayList<>();
        application.entities().values().forEach(e -> artifacts.add(assessEntity(e, application)));
        application.viewObjects().values().forEach(v -> artifacts.add(assessViewObject(v, application)));
        application.modules().values().forEach(m -> artifacts.add(assessModule(m, application)));

        artifacts.sort((a, b) -> Double.compare(b.effortDays(), a.effortDays()));
        return new ApplicationAssessment(application, List.copyOf(artifacts), effort);
    }

    // --- Entity objects ---------------------------------------------------------------

    private ArtifactAssessment assessEntity(EntityObject entity, AdfApplication application) {
        Score score = new Score(5);

        int attributes = entity.attributes().size();
        if (attributes > 10) {
            score.add(Math.min(15, (attributes - 10) / 2), attributes + " attributes");
        }

        boolean realCustomJava = scoreCustomJava(entity.rowClass(), application, score);

        long expressionRules = entity.validators().stream().filter(EntityObject.Validator::expression).count();
        if (expressionRules > 0) {
            // Groovy has no mechanical target: each rule is read and rewritten.
            score.add((int) expressionRules * 6, expressionRules + " Groovy validation rule(s)");
        }

        long declarativeRules = entity.validators().size() - expressionRules;
        if (declarativeRules > 0) {
            score.add((int) declarativeRules * 2, declarativeRules + " declarative validation rule(s)");
        }

        Set<String> domainTypes = new LinkedHashSet<>();
        entity.attributes().stream()
                .map(EntityObject.Attribute::javaType)
                .filter(Types::isAdfDomainType)
                .forEach(domainTypes::add);
        if (!domainTypes.isEmpty()) {
            score.add(domainTypes.size() * 3, domainTypes.size() + " ADF runtime type(s) to map");
        }

        long toMany = entity.accessors().stream()
                .filter(a -> a.cardinality() == EntityObject.Cardinality.TO_MANY).count();
        if (toMany > 0) score.add((int) toMany * 4, toMany + " collection association(s)");

        if (entity.primaryKey().size() > 1) {
            score.add(8, "composite primary key (" + entity.primaryKey().size() + " columns)");
        } else if (entity.primaryKey().isEmpty()) {
            // Without a key, JPA has nothing to map an identity to; someone must choose one.
            score.add(10, "no primary key declared");
        }

        MigrationClass migrationClass = classify(realCustomJava, expressionRules > 0, score.total());

        return build(entity.fqn(), ArtifactAssessment.Kind.ENTITY_OBJECT,
                entity.sourcePath(), score, migrationClass);
    }

    // --- View objects -----------------------------------------------------------------

    private ArtifactAssessment assessViewObject(ViewObject view, AdfApplication application) {
        Score score = new Score(5);

        if (view.customQuery()) {
            // Hand-written SQL cannot be regenerated from the attribute list; it is carried across
            // as a native query and its result mapping written by hand.
            score.add(18, "hand-written SQL" + tableCount(view));
        }
        boolean realCustomJava = scoreCustomJava(view.componentClass(), application, score);
        if (!view.clientMethods().isEmpty()) {
            score.add(view.clientMethods().size() * 5,
                    view.clientMethods().size() + " remotely callable method(s)");
        }
        if (!view.criteria().isEmpty()) {
            score.add(view.criteria().size() * 3, view.criteria().size() + " view criteria");
        }
        if (!view.variables().isEmpty()) {
            score.add(view.variables().size() * 2, view.variables().size() + " bind variable(s)");
        }
        if (view.entityUsages().size() > 1) {
            score.add(view.entityUsages().size() * 4,
                    "writes " + view.entityUsages().size() + " entities");
        }
        long transientAttributes = view.attributes().stream().filter(ViewObject.Attribute::isTransient).count();
        if (transientAttributes > 0) {
            score.add((int) transientAttributes * 2, transientAttributes + " calculated attribute(s)");
        }

        MigrationClass migrationClass = classify(realCustomJava,
                view.customQuery() || !view.clientMethods().isEmpty(), score.total());

        return build(view.fqn(), ArtifactAssessment.Kind.VIEW_OBJECT,
                view.sourcePath(), score, migrationClass);
    }

    private static String tableCount(ViewObject view) {
        String sql = view.sql();
        if (sql == null) return "";
        int commas = sql.toUpperCase().contains(" FROM ")
                ? sql.substring(sql.toUpperCase().indexOf(" FROM ")).split(",").length : 1;
        return commas > 1 ? " joining " + commas + " tables" : "";
    }

    // --- Application modules ----------------------------------------------------------

    private ArtifactAssessment assessModule(ApplicationModule module, AdfApplication application) {
        Score score = new Score(3);

        if (!module.viewUsages().isEmpty()) {
            score.add(Math.min(20, module.viewUsages().size() * 2),
                    module.viewUsages().size() + " view instance(s) exposed");
        }
        boolean realCustomJava = scoreCustomJava(module.componentClass(), application, score);
        if (!module.nested().isEmpty()) {
            score.add(module.nested().size() * 6, module.nested().size() + " nested module(s)");
        }

        MigrationClass migrationClass = classify(realCustomJava, false, score.total());

        return build(module.fqn(), ArtifactAssessment.Kind.APPLICATION_MODULE,
                module.sourcePath(), score, migrationClass);
    }

    // --- helpers ----------------------------------------------------------------------

    /**
     * Scores a component's hand-written class by what is actually in it.
     *
     * <p>JDeveloper writes an {@code Impl} class for a component whenever code generation is
     * switched on, and most of them contain nothing but typed accessors and attribute index
     * constants. Treating the presence of a class as evidence of custom behaviour therefore
     * inflates an estimate across a whole application — a real one declared a custom class on 37
     * entities, almost all of them generated boilerplate.
     *
     * @return true when the class contains behaviour a person has to deal with
     */
    private boolean scoreCustomJava(String className, AdfApplication application, Score score) {
        if (className == null || className.isBlank()) return false;

        CustomJavaClass custom = application.customJavaFor(className).orElse(null);
        if (custom == null) {
            // The class was not in the scan, so nothing is known about it. Score it as real:
            // assuming it is empty would be the more expensive mistake.
            score.add(12, "custom class " + simple(className) + ", source not found");
            return true;
        }
        if (custom.isTrivial()) {
            return false;
        }

        int rewrites = 0;
        int reviews = 0;
        for (CustomMethod method : custom.methods()) {
            switch (method.effort()) {
                case MANUAL -> rewrites++;
                case ASSISTED -> reviews++;
                case MECHANICAL -> { }
            }
        }

        if (rewrites > 0) {
            score.add(rewrites * 12, rewrites + " method(s) reaching into the ADF runtime");
        }
        if (reviews > 0) {
            score.add(reviews * 5, reviews + " custom method(s) to review");
        }
        return rewrites + reviews > 0;
    }

    /** A component is only as hard as the work actually left in it. */
    private static MigrationClass classify(boolean realCustomJava, boolean needsReview, int complexity) {
        if (realCustomJava && complexity >= 40) return MigrationClass.MANUAL;
        if (realCustomJava || needsReview) return MigrationClass.ASSISTED;
        return MigrationClass.AUTO;
    }

    private ArtifactAssessment build(String fqn, ArtifactAssessment.Kind kind, String sourcePath,
                                     Score score, MigrationClass migrationClass) {
        int complexity = Math.min(100, score.total());
        return new ArtifactAssessment(fqn, kind, sourcePath, complexity, migrationClass,
                List.copyOf(score.drivers()), effort.days(migrationClass, complexity));
    }

    private static String simple(String className) {
        if (className == null) return "";
        int i = className.lastIndexOf('.');
        return i < 0 ? className : className.substring(i + 1);
    }

    /** Accumulates a score alongside the reason for each addition. */
    private static final class Score {
        private int total;
        private final List<String> drivers = new ArrayList<>();

        Score(int base) { this.total = base; }

        void add(int points, String driver) {
            if (points <= 0) return;
            total += points;
            drivers.add(driver);
        }

        int total() { return total; }

        List<String> drivers() { return drivers; }
    }
}
