package dev.achiri.multivault.tenant.provisioning;

public interface ProvisioningJobQueue {

    void enqueue(ProvisioningJob job);

    long pendingCount();
}