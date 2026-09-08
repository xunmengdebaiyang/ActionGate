package com.actiongate;

import com.actiongate.worker.AfterSalesWorkflowImpl;
import com.actiongate.worker.LocalAfterSalesActivities;
import com.actiongate.workflow.AfterSalesWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.testing.TestWorkflowEnvironment;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration
class TemporalTestConfiguration {
    @Bean(destroyMethod = "close")
    TestWorkflowEnvironment temporalEnvironment() {
        var environment = TestWorkflowEnvironment.newInstance();
        var worker = environment.newWorker(AfterSalesWorkflow.TASK_QUEUE);
        worker.registerWorkflowImplementationTypes(AfterSalesWorkflowImpl.class);
        worker.registerActivitiesImplementations(new LocalAfterSalesActivities());
        environment.start();
        return environment;
    }

    @Bean
    WorkflowClient workflowClient(TestWorkflowEnvironment environment) {
        return environment.getWorkflowClient();
    }
}
