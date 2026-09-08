package com.actiongate.workflow;

import com.actiongate.contract.ContractJson;
import com.actiongate.contract.SchemaValidator;

import static com.actiongate.contract.ContractViolationException.require;

public final class TicketValidator {
    private static final SchemaValidator SCHEMA = SchemaValidator.resource("/schemas/ticket.schema.json");

    private TicketValidator() {
    }

    public static TicketInput parseAndValidate(String json) {
        require(json != null && json.length() <= 4096, "Ticket must be a JSON document of at most 4096 characters");
        return ContractJson.convert(SCHEMA.validate(json), TicketInput.class);
    }
}
