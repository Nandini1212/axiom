package com.axiom.investigation.model;

/**
 * What kind of problem a {@link CollectionWarning} reports. {@code OPERATIONAL_FAILURE} covers
 * expected, recoverable problems a collector hit while gathering evidence (a timeout, a rate
 * limit, a malformed input file) — collectors must convert these to a warning rather than throw
 * (see {@code 17-investigation-architecture.md} §3's collector failure contract).
 * {@code DUPLICATE_EVIDENCE_ID} is raised by {@code InvestigationRunner} itself when two
 * collectors return evidence sharing one {@code evidenceId} (§3's evidence-identity invariant).
 */
public enum CollectionWarningType {
    OPERATIONAL_FAILURE,
    DUPLICATE_EVIDENCE_ID
}
