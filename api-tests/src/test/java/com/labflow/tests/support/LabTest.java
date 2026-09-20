package com.labflow.tests.support;

import com.labflow.client.LabClient;
import com.labflow.common.config.LabConfig;

/**
 * Base class for every lab check. It holds only the client for the lab named in the
 * configuration (env vars / system properties). There is no static or mutable state, so checks
 * stay independent of each other and safe to run in any order.
 */
public abstract class LabTest {

    protected final LabClient lab = LabClient.create(LabConfig.fromEnvironment());
}
