package com.axiom.investigation.model;

/**
 * Why an investigation started. Deliberately excludes {@code DEPLOYMENT_FAILURE} and any
 * production-alert trigger — no evidence source exists for either yet (see
 * {@code 16-investigation-domain-model.md} §4); adding the constant ahead of its evidence source
 * would itself be a speculative abstraction. Add it when the corresponding
 * {@code EvidenceCollector} actually exists, not before.
 */
public enum TriggerType {
    PR_BUILD_FAILURE,
    NIGHTLY_REGRESSION,
    MANUAL
}
