package temporal.internal.dispatcher;

import io.temporal.workflow.DynamicWorkflow;
import io.temporal.common.converter.EncodedValues;

public class DynamicWorkflowProxy implements DynamicWorkflow {

    public DynamicWorkflowProxy(DynamicWorkflow backend) {
        m_backend = backend;
    }

    private final DynamicWorkflow m_backend;

    public Object execute(EncodedValues args) {
        return m_backend.execute(args);
    }
}
