package com.kylontech.experiment.events;

/**
 * A scope defines resource visibility. A resource is only visible by components a with a scope at
 * least as broad as its own. Scope comparison is defined by ordinal values.
 */
public enum Scope {

    /**
     * Narrowest scope. It only gives access to resources that may be visible by untrusted remote
     * actors.
     */
    SCOPE_PUBLIC,
    /**
     * Intermediate scope. It gives access to all public resources, including those shared with other
     * trusted MESH servers.
     */
    SCOPE_FEDERATED,
    /**
     * Broad scope. It gives access to public and sensitive resources. It should only be given to
     * trusted local actors.
     */
    SCOPE_PRIVATE,
    /**
     * Broadest scope. It gives access to all resources. It should only be given to trusted local
     * actors.
     */
    SCOPE_ROOT
}
