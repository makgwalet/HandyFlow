package za.co.handyflow.platform.projects.domain.enums;

/**
 * The kinds of project record a document revision can be linked to as evidence.
 *
 * scopeKeyword() is the URL keyword ProjectScopeResolver / ProjectRoute already use for that kind
 * of record, so "which project does this record belong to" is answered by the one existing,
 * tenant-scoped lookup rather than a second copy of it. Keep in sync with the CHECK constraint
 * project_document_links_type_check in migration V576.
 */
public enum DocumentLinkType {
    RFI("rfis"),
    CHANGE_ORDER("change-orders"),
    TASK("tasks"),
    SNAG("snags"),
    RISK("risks"),
    SITE_DIARY("site-diaries");

    private final String scopeKeyword;

    DocumentLinkType(String scopeKeyword) { this.scopeKeyword = scopeKeyword; }

    public String scopeKeyword() { return scopeKeyword; }
}
