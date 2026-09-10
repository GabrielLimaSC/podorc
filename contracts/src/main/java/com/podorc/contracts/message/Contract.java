package com.podorc.contracts.message;

/** Small validation helpers shared by the message record constructors. */
final class Contract {

    private Contract() {
    }

    /** Fails with a clear message when a required contract field is absent. */
    static <T> T require(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("missing required field: " + field);
        }
        return value;
    }
}
