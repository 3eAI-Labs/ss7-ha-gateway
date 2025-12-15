package com.company.ss7ha.core.handlers;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public class CorrelationIdWrapper implements Externalizable {
    private String correlationId;

    public CorrelationIdWrapper() {
    }

    public CorrelationIdWrapper(String correlationId) {
        this.correlationId = correlationId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeUTF(correlationId);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        correlationId = in.readUTF();
    }
}
