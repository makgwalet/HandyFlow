package za.co.handyflow.platform.contracting.domain.model;

/** What a delegation of authority can allow. Approving and signing are separate on purpose: a person can hold one without the other. */
public enum AuthorityAction { APPROVE, NEGOTIATE, SIGN, RENEW, TERMINATE }
