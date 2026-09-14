package com.adfmig.parser;

import com.adfmig.core.model.ApplicationModule;
import com.adfmig.core.model.Association;
import com.adfmig.core.model.EntityObject;
import com.adfmig.core.model.ViewLink;
import com.adfmig.core.model.ViewObject;
import org.w3c.dom.Element;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Parses the three ADF business component types that carry business meaning: entity objects,
 * view objects and application modules.
 */
final class BusinessComponentParser {

    private final XmlDocuments documents = new XmlDocuments();

    // --- Entity objects ---------------------------------------------------------------

    Optional<EntityObject> parseEntity(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> new EntityObject(
                fqn,
                Xml.attr(root, "DBObjectName"),
                Xml.attr(root, "DBObjectType", "table"),
                Xml.attr(root, "RowClass"),
                entityAttributes(root),
                accessors(root),
                constraints(root),
                validators(root),
                relativePath,
                Xml.attr(root, "Extends")));
    }

    private List<EntityObject.Attribute> entityAttributes(Element root) {
        List<EntityObject.Attribute> out = new ArrayList<>();
        for (Element a : Xml.children(root, "Attribute")) {
            out.add(new EntityObject.Attribute(
                    Xml.attr(a, "Name"),
                    storedColumn(a),
                    Xml.attr(a, "Type"),
                    Xml.attr(a, "SQLType"),
                    Xml.attr(a, "ColumnType"),
                    Xml.intAttr(a, "Precision"),
                    Xml.intAttr(a, "Scale"),
                    Xml.boolAttr(a, "IsNotNull"),
                    Xml.boolAttr(a, "IsUnique"),
                    Xml.boolAttr(a, "PrimaryKey"),
                    Xml.boolAttr(a, "RetrievedOnUpdate"),
                    !Xml.children(a, "TransientExpression").isEmpty(),
                    Xml.boolAttr(a, "DiscrColumn"),
                    Xml.attr(a, "DefaultValue")));
        }
        return out;
    }

    /**
     * The column an attribute is stored in, or null when it is not stored at all.
     *
     * <p>ADF marks a calculated attribute {@code IsPersistent="false"} and then still writes a
     * ColumnName for it — usually the placeholder {@code $none$}, sometimes a real-looking name.
     * Taken at face value that becomes a mapped column, which compiles and then fails at startup
     * with "missing column", because the column was never in the database. Returning null here
     * puts it through the same path as an attribute that declares no column: not mapped, with a
     * TODO and a diagnostic saying so.
     *
     * <p>{@code $none$} is treated the same way even where IsPersistent is absent, because it is
     * ADF's own way of writing "no column" and means nothing to a database.
     */
    private static String storedColumn(Element a) {
        String column = Xml.attr(a, "ColumnName");
        if (column == null || column.isBlank() || "$none$".equalsIgnoreCase(column.trim())) {
            return null;
        }
        String persistent = Xml.attr(a, "IsPersistent");
        if ("false".equalsIgnoreCase(persistent)) return null;

        // ROWID is Oracle's address for a row, not a column in the table: it cannot be created,
        // selected into a mapping or used as a key by JPA. ADF maps it where a table has nothing
        // else identifying a row. Carried across as an ordinary column it refuses to start,
        // because no schema has it — so it is treated as what it is, storage that is not there.
        String columnType = Xml.attr(a, "ColumnType");
        if ("ROWID".equalsIgnoreCase(columnType == null ? "" : columnType.trim())) return null;

        return column;
    }

    private List<EntityObject.Accessor> accessors(Element root) {
        List<EntityObject.Accessor> out = new ArrayList<>();
        for (Element a : Xml.children(root, "AccessorAttribute")) {
            // ADF encodes cardinality in the accessor's declared type: a RowIterator walks many
            // rows, an EntityImpl points at one.
            String type = Xml.attr(a, "Type", "");
            EntityObject.Cardinality cardinality = type.contains("RowIterator")
                    ? EntityObject.Cardinality.TO_MANY
                    : EntityObject.Cardinality.TO_ONE;
            out.add(new EntityObject.Accessor(
                    Xml.attr(a, "Name"),
                    Xml.attr(a, "Association"),
                    cardinality,
                    Xml.boolAttr(a, "IsUpdateable")));
        }
        return out;
    }

    /**
     * Reads database constraints out of the {@code Key} elements. ADF keeps the interesting facts
     * — whether a key is foreign, unique or a check, and the condition — in the design-time block
     * rather than as attributes.
     */
    private List<EntityObject.Constraint> constraints(Element root) {
        List<EntityObject.Constraint> out = new ArrayList<>();
        for (Element key : Xml.children(root, "Key")) {
            String referencedKey = Xml.designTime(key, "_referencedKey");
            String checkCondition = Xml.designTime(key, "_checkCondition");

            EntityObject.Constraint.Kind kind;
            if (Xml.boolAttr(key, "PrimaryKey")) {
                kind = EntityObject.Constraint.Kind.PRIMARY_KEY;
            } else if ("true".equals(Xml.designTime(key, "_isForeign"))) {
                kind = EntityObject.Constraint.Kind.FOREIGN_KEY;
            } else if ("true".equals(Xml.designTime(key, "_isCheck"))) {
                kind = EntityObject.Constraint.Kind.CHECK;
            } else if ("true".equals(Xml.designTime(key, "_isUnique"))) {
                kind = EntityObject.Constraint.Kind.UNIQUE;
            } else {
                kind = EntityObject.Constraint.Kind.OTHER;
            }

            out.add(new EntityObject.Constraint(
                    Xml.attr(key, "Name"),
                    kind,
                    Xml.designTime(key, "_DBObjectName"),
                    Xml.attrArray(key, "Attributes").stream().map(BusinessComponentParser::lastSegment).toList(),
                    referencedKey,
                    checkCondition));
        }
        return out;
    }

    /**
     * Collects declarative validation rules. ADF names them by bean type — CompareValidationBean,
     * ExpressionValidationBean, LengthValidationBean and so on — in the validation namespace, and
     * they appear both on attributes and at entity level.
     */
    private List<EntityObject.Validator> validators(Element root) {
        List<EntityObject.Validator> out = new ArrayList<>();
        collectValidators(root, null, out);
        for (Element attribute : Xml.children(root, "Attribute")) {
            collectValidators(attribute, Xml.attr(attribute, "Name"), out);
        }
        return out;
    }

    private void collectValidators(Element parent, String onAttribute, List<EntityObject.Validator> out) {
        for (Element child : Xml.children(parent)) {
            String local = Xml.localName(child);
            if (!local.endsWith("ValidationBean")) continue;

            // A rule delegating to a Groovy expression carries a TransientExpression child; those
            // need translating from the entity's .bcs file rather than mapping to an annotation.
            boolean expression = !Xml.descendants(child, "TransientExpression").isEmpty()
                    || "EXPR".equals(Xml.attr(child, "OperandType"));

            out.add(new EntityObject.Validator(
                    Xml.attr(child, "Name"),
                    local,
                    Xml.attr(child, "OnAttribute", onAttribute),
                    Xml.attr(child, "CompareType"),
                    expression));
        }
    }

    // --- Associations -----------------------------------------------------------------

    /**
     * Parses an association: the entities at each end, their cardinality and the attributes joined
     * on. Without this an entity's accessor names a relationship that cannot be mapped, because
     * neither the target entity nor the join columns are stated on the accessor itself.
     */
    Optional<Association> parseAssociation(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> {
            List<Association.End> ends = new ArrayList<>();
            for (Element end : Xml.children(root, "AssociationEnd")) {
                ends.add(new Association.End(
                        Xml.attr(end, "Name"),
                        Xml.attr(end, "Cardinality"),
                        Xml.attr(end, "Owner"),
                        Xml.boolAttr(end, "Source"),
                        Xml.attrArray(end, "Attributes").stream()
                                .map(BusinessComponentParser::lastSegment).toList(),
                        Xml.designTime(end, "_foreignKey")));
            }
            return new Association(fqn, List.copyOf(ends), relativePath);
        });
    }

    /**
     * Parses a view link: the master-detail relationship between two queries. Shaped like an
     * association but joining view objects rather than entity objects, and it is what ADF exposes
     * as a nested collection.
     */
    Optional<ViewLink> parseViewLink(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> {
            List<ViewLink.End> ends = new ArrayList<>();
            for (Element end : Xml.children(root, "ViewLinkDefEnd")) {
                ends.add(new ViewLink.End(
                        Xml.attr(end, "Name"),
                        Xml.attr(end, "Cardinality"),
                        Xml.attr(end, "Owner"),
                        Xml.boolAttr(end, "Source"),
                        Xml.attrArray(end, "Attributes").stream()
                                .map(BusinessComponentParser::lastSegment).toList()));
            }
            return new ViewLink(fqn, Xml.attr(root, "EntityAssociation"), List.copyOf(ends), relativePath);
        });
    }

    // --- View objects -----------------------------------------------------------------

    Optional<ViewObject> parseViewObject(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> new ViewObject(
                fqn,
                normalise(Xml.attr(root, "SelectList")),
                normalise(Xml.attr(root, "FromList")),
                // An expert-mode view object leaves SelectList and FromList empty and puts the
                // whole statement in a SQLQuery block, often joining tables the entity model
                // never mentions. Reading only the attributes would report an empty query.
                Xml.child(root, "SQLQuery").map(Xml::text).map(BusinessComponentParser::normalise).orElse(null),
                Xml.boolAttr(root, "CustomQuery"),
                Xml.attr(root, "ComponentClass"),
                entityUsages(root),
                viewAttributes(root),
                variables(root),
                criteria(root),
                clientMethods(root),
                relativePath,
                normalise(Xml.attr(root, "Where"))));
    }

    private List<ViewObject.EntityUsage> entityUsages(Element root) {
        return Xml.children(root, "EntityUsage").stream()
                .map(e -> new ViewObject.EntityUsage(Xml.attr(e, "Name"), Xml.attr(e, "Entity")))
                .toList();
    }

    private List<ViewObject.Attribute> viewAttributes(Element root) {
        return Xml.children(root, "ViewAttribute").stream()
                .map(a -> new ViewObject.Attribute(
                        Xml.attr(a, "Name"),
                        Xml.attr(a, "EntityAttrName"),
                        Xml.attr(a, "EntityUsage"),
                        Xml.attr(a, "AliasName"),
                        Xml.attr(a, "Type"),
                        Xml.boolAttr(a, "IsNotNull"),
                        Xml.boolAttr(a, "IsUnique")))
                .toList();
    }

    private List<ViewObject.Variable> variables(Element root) {
        return Xml.children(root, "Variable").stream()
                .map(v -> new ViewObject.Variable(
                        Xml.attr(v, "Name"), Xml.attr(v, "Type"), Xml.attr(v, "Kind")))
                .toList();
    }

    private List<ViewObject.Criteria> criteria(Element root) {
        List<ViewObject.Criteria> out = new ArrayList<>();
        for (Element c : Xml.children(root, "ViewCriteria")) {
            List<ViewObject.CriteriaItem> items = new ArrayList<>();
            for (Element item : Xml.descendants(c, "ViewCriteriaItem")) {
                items.add(new ViewObject.CriteriaItem(
                        Xml.attr(item, "ViewAttribute"),
                        Xml.attr(item, "Operator"),
                        Xml.attr(item, "Value"),
                        Xml.attr(item, "Conjunction"),
                        Xml.boolAttr(item, "IsBindVarValue"),
                        Xml.attr(item, "Required")));
            }
            out.add(new ViewObject.Criteria(Xml.attr(c, "Name"), Xml.attr(c, "Conjunction"), items));
        }
        return out;
    }

    /**
     * Reads the client interface: the methods a remote caller may invoke. Everything else on a
     * custom {@code ViewObjectImpl} is internal, so this is the true public surface.
     */
    private List<ViewObject.ClientMethod> clientMethods(Element root) {
        List<ViewObject.ClientMethod> out = new ArrayList<>();
        for (Element ci : Xml.children(root, "ClientInterface")) {
            for (Element method : Xml.children(ci, "Method")) {
                List<ViewObject.Param> params = Xml.children(method, "Parameter").stream()
                        .map(p -> new ViewObject.Param(Xml.attr(p, "Name"), Xml.attr(p, "Type")))
                        .toList();
                String returnType = Xml.child(method, "Return")
                        .map(r -> Xml.attr(r, "Type"))
                        .orElse(null);
                out.add(new ViewObject.ClientMethod(
                        Xml.attr(method, "Name", Xml.attr(method, "MethodName")), returnType, params));
            }
        }
        return out;
    }

    // --- Application modules ----------------------------------------------------------

    Optional<ApplicationModule> parseApplicationModule(Path file, String relativePath, String fqn) {
        return documents.loadRoot(file).map(root -> new ApplicationModule(
                fqn,
                Xml.attr(root, "ComponentClass"),
                Xml.children(root, "ViewUsage").stream()
                        .map(v -> new ApplicationModule.ViewUsage(
                                Xml.attr(v, "Name"), Xml.attr(v, "ViewObjectName")))
                        .toList(),
                Xml.children(root, "AppModuleUsage").stream()
                        .map(a -> Xml.attr(a, "FullName"))
                        .filter(java.util.Objects::nonNull)
                        .toList(),
                Xml.children(root, "ViewLinkUsage").stream()
                        .map(v -> new ApplicationModule.ViewLinkUsage(
                                Xml.attr(v, "Name"),
                                Xml.attr(v, "ViewLinkObjectName"),
                                lastSegment(Xml.attr(v, "SrcViewUsageName", "")),
                                lastSegment(Xml.attr(v, "DstViewUsageName", ""))))
                        .toList(),
                relativePath));
    }

    // --- helpers ----------------------------------------------------------------------

    /** ADF stores SELECT and FROM clauses with the developer's line breaks and indentation. */
    private static String normalise(String sql) {
        return sql == null ? null : sql.replaceAll("\\s+", " ").trim();
    }

    /** {@code com.example.entities.Employees.Email} to {@code Email}. */
    private static String lastSegment(String qualified) {
        int i = qualified.lastIndexOf('.');
        return i < 0 ? qualified : qualified.substring(i + 1);
    }
}
