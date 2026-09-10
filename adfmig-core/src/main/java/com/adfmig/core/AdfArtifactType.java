package com.adfmig.core;

import java.util.Locale;

/**
 * The kinds of artifact an ADF application is built from, and how each one bears on a migration
 * to Spring Boot.
 */
public enum AdfArtifactType {

    // --- Business components: the data and service model. This is what we migrate. ---
    ENTITY_OBJECT("Entity object", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    VIEW_OBJECT("View object", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    APPLICATION_MODULE("Application module", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    ASSOCIATION("Association", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    VIEW_LINK("View link", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    DOMAIN("Domain", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    /**
     * The Groovy expressions belonging to one business component, which ADF extracts into a
     * companion {@code .bcs} file. Each expression is annotated with its role
     * ({@code @TransientValueExpression}, {@code @ValidatorExpression},
     * {@code @ValidatorConditionExpression}, {@code @MessageParameterExpression}) and the attribute
     * it applies to, so expressions can be classified deterministically and the common idioms
     * translated mechanically.
     */
    GROOVY_SCRIPT("Groovy expressions (.bcs)", Layer.BUSINESS_COMPONENTS, Relevance.PRIMARY),
    BC_PROJECT("Business components project (.jpx)", Layer.BUSINESS_COMPONENTS, Relevance.SUPPORTING),
    BC_PACKAGE("Business components package", Layer.BUSINESS_COMPONENTS, Relevance.SUPPORTING),
    BC4J_CONFIG("Application module configuration (bc4j.xcfg)", Layer.BUSINESS_COMPONENTS, Relevance.SUPPORTING),

    // --- Already an HTTP contract. The migration beachhead. ---
    REST_RESOURCE_REGISTRY("REST resource registry (.rpx)", Layer.SERVICE, Relevance.PRIMARY),
    REST_RESOURCE("REST resource definition", Layer.SERVICE, Relevance.PRIMARY),
    SERVICE_INTERFACE("SOAP/SDO service interface", Layer.SERVICE, Relevance.PRIMARY),

    // --- Binding layer. Cannot be ported: these bind ADF Faces to the model in-process rather
    //     than over HTTP. They are still parsed, because they reveal which application module
    //     methods each page calls — the call graph needed to place endpoint security. ---
    DATA_BINDINGS("Data bindings (.cpx)", Layer.BINDING, Relevance.UI_REWRITE),
    PAGE_DEFINITION("Page definition", Layer.BINDING, Relevance.UI_REWRITE),

    // --- Security: drives Spring Security generation. ---
    JAZN_DATA("Security policy (jazn-data)", Layer.SECURITY, Relevance.PRIMARY),
    JPS_CONFIG("OPSS configuration (jps-config)", Layer.SECURITY, Relevance.SUPPORTING),
    IDS_CONFIG("Identity store configuration", Layer.SECURITY, Relevance.SUPPORTING),
    WSM_ASSEMBLY("Web services manager policy", Layer.SECURITY, Relevance.SUPPORTING),
    WEB_XML("web.xml", Layer.SECURITY, Relevance.PRIMARY),
    WEBLOGIC_WEB_XML("weblogic.xml", Layer.SECURITY, Relevance.PRIMARY),
    WEBLOGIC_APPLICATION("weblogic-application.xml", Layer.SECURITY, Relevance.SUPPORTING),

    // --- Controller and view: not migrated. Their presence means a UI rewrite, not a port. ---
    TASK_FLOW("Task flow / adfc-config", Layer.CONTROLLER, Relevance.UI_REWRITE),
    TASK_FLOW_REGISTRY("Task flow registry", Layer.CONTROLLER, Relevance.UI_REWRITE),
    FACES_CONFIG("faces-config.xml", Layer.CONTROLLER, Relevance.UI_REWRITE),
    STRUTS_CONFIG("struts-config.xml (pre-JSF ADF)", Layer.CONTROLLER, Relevance.UI_REWRITE),
    JSF_PAGE("JSF page (.jspx/.jsp)", Layer.VIEW, Relevance.UI_REWRITE),
    JSF_FRAGMENT("JSF fragment (.jsff)", Layer.VIEW, Relevance.UI_REWRITE),
    PAGE_TEMPLATE("Page template definition", Layer.VIEW, Relevance.UI_REWRITE),
    SKIN("Skin / trinidad-config", Layer.VIEW, Relevance.IGNORED),

    // --- Out of scope. ---
    ADF_MOBILE("ADF Mobile artifact", Layer.OTHER, Relevance.OUT_OF_SCOPE),
    MDS("MDS metadata / customization", Layer.OTHER, Relevance.OUT_OF_SCOPE),

    // --- Project plumbing and everything else. ---
    ADF_CONFIG("adf-config.xml", Layer.CONFIG, Relevance.SUPPORTING),
    ADF_SETTINGS("adf-settings.xml", Layer.CONFIG, Relevance.SUPPORTING),
    CONNECTIONS("connections.xml", Layer.CONFIG, Relevance.SUPPORTING),
    DATA_SOURCES("data-sources.xml", Layer.CONFIG, Relevance.SUPPORTING),
    JDEV_PROJECT("JDeveloper project (.jpr)", Layer.PROJECT, Relevance.SUPPORTING),
    JDEV_WORKSPACE("JDeveloper workspace (.jws)", Layer.PROJECT, Relevance.SUPPORTING),
    JAVA_SOURCE("Java source", Layer.OTHER, Relevance.PRIMARY),
    UNKNOWN("Unrecognised", Layer.OTHER, Relevance.IGNORED);

    /** Which architectural tier the artifact belongs to. */
    public enum Layer {
        BUSINESS_COMPONENTS, SERVICE, BINDING, SECURITY, CONTROLLER, VIEW, CONFIG, PROJECT, OTHER
    }

    /** What the artifact means for a migration. */
    public enum Relevance {
        /** Carries business meaning that must survive into the Spring Boot application. */
        PRIMARY,
        /** Needed to resolve or configure primary artifacts, but not migrated on its own. */
        SUPPORTING,
        /**
         * Bound to the ADF Faces UI. Cannot be ported to REST; its presence signals that the
         * consumer needs rewriting, which is the single biggest driver of migration cost.
         */
        UI_REWRITE,
        /** Recognised but outside the scope of an ADF-to-Spring-Boot migration. */
        OUT_OF_SCOPE,
        /** Recognised and safely ignorable. */
        IGNORED
    }

    private final String label;
    private final Layer layer;
    private final Relevance relevance;

    AdfArtifactType(String label, Layer layer, Relevance relevance) {
        this.label = label;
        this.layer = layer;
        this.relevance = relevance;
    }

    public String label() { return label; }
    public Layer layer() { return layer; }
    public Relevance relevance() { return relevance; }

    /**
     * Classifies an XML document from its root element, root attributes and filename.
     *
     * <p>Order matters: the ambiguous root elements are disambiguated before the plain
     * root-element lookup.
     */
    public static AdfArtifactType classify(XmlHeader header, String fileName) {
        String root = header.rootElement();
        String lower = fileName.toLowerCase(Locale.ROOT);

        // "Application" is shared between the REST resource registry and the binding container.
        if (root.equals("Application")) {
            if (lower.endsWith(".rpx") || "ResourceRegistry".equals(header.attr("id"))) {
                return REST_RESOURCE_REGISTRY;
            }
            return DATA_BINDINGS;
        }

        // ADF reuses the binding-layer pageDefinition format for REST resources rather than
        // defining a dedicated schema. usageMode is the only thing distinguishing them.
        if (root.equals("pageDefinition")) {
            return header.hasAttr("usageMode", "RESTClient") ? REST_RESOURCE : PAGE_DEFINITION;
        }

        return switch (root) {
            case "Entity"                 -> ENTITY_OBJECT;
            case "ViewObject"             -> VIEW_OBJECT;
            case "AppModule"              -> APPLICATION_MODULE;
            case "Association"            -> ASSOCIATION;
            case "ViewLink"               -> VIEW_LINK;
            case "Domain"                 -> DOMAIN;
            case "JboProject"             -> BC_PROJECT;
            case "JboPackage"             -> BC_PACKAGE;
            case "BC4JConfig"             -> BC4J_CONFIG;

            case "jazn-data"              -> JAZN_DATA;
            case "jpsConfig"              -> JPS_CONFIG;
            case "identityStoreConfig",
                 "IdentityDirectoryConfig"-> IDS_CONFIG;
            case "wsm-assembly"           -> WSM_ASSEMBLY;
            case "web-app"                -> WEB_XML;
            case "weblogic-web-app"       -> WEBLOGIC_WEB_XML;
            case "weblogic-application",
                 "orion-application"      -> WEBLOGIC_APPLICATION;

            case "adfc-config",
                 "adfc-mobile-config"     -> TASK_FLOW;
            case "task-flow-registry"     -> TASK_FLOW_REGISTRY;
            case "faces-config"           -> FACES_CONFIG;
            case "struts-config"          -> STRUTS_CONFIG;
            case "pageTemplateDefs",
                 "declarativeCompDefs"    -> PAGE_TEMPLATE;
            case "skins", "trinidad-config",
                 "adf-faces-config"       -> SKIN;

            case "adf-config"             -> ADF_CONFIG;
            case "adf-settings"           -> ADF_SETTINGS;
            case "connections"            -> CONNECTIONS;
            case "data-sources"           -> DATA_SOURCES;

            case "MetadataDirectory",
                 "References",
                 "customization"          -> MDS;

            default -> {
                if (root.startsWith("adfmf")) yield ADF_MOBILE;
                if (lower.endsWith(".jpr")) yield JDEV_PROJECT;
                if (lower.endsWith(".jws")) yield JDEV_WORKSPACE;
                yield UNKNOWN;
            }
        };
    }

    /** Classifies a non-XML file by extension. Returns {@code null} when the file is not of interest. */
    public static AdfArtifactType classifyByExtension(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".java")) return JAVA_SOURCE;
        if (lower.endsWith(".bcs")) return GROOVY_SCRIPT;
        if (lower.endsWith(".jsff")) return JSF_FRAGMENT;
        if (lower.endsWith(".jspx") || lower.endsWith(".jsp")) return JSF_PAGE;
        return null;
    }
}
