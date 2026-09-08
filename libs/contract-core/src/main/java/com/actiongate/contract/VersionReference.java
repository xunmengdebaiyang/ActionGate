package com.actiongate.contract;

import static com.actiongate.contract.ContractViolationException.require;

public record VersionReference(String id, String version) {
    public VersionReference {
        require(id != null && id.matches("[a-z][a-z0-9_-]{0,63}"), "Invalid contract id");
        require(version != null && version.matches("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"),
                "Version must be major.minor.patch without leading zeroes");
    }
}
