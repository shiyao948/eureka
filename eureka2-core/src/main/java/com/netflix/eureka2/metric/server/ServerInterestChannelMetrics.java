package com.netflix.eureka2.metric.server;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.netflix.eureka2.interests.ApplicationInterest;
import com.netflix.eureka2.interests.FullRegistryInterest;
import com.netflix.eureka2.interests.InstanceInterest;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.MultipleInterests;
import com.netflix.eureka2.interests.VipInterest;
import com.netflix.eureka2.metric.InterestChannelMetrics;
import com.netflix.eureka2.model.instance.InstanceInfo;

/**
 * @author Tomasz Bak
 */
public interface ServerInterestChannelMetrics extends InterestChannelMetrics {

    enum AtomicInterest {
        Instance,
        Application,
        Vip,
        InterestAll
    }

    void incrementApplicationNotificationCounter(String applicationName);

    void incrementSubscriptionCounter(AtomicInterest interestType, String id);

    void decrementSubscriptionCounter(AtomicInterest interestType, String id);

    /**
     * This class helps tracking subscription status per channel.
     */
    class ChannelSubscriptionMonitor {

        private final ServerInterestChannelMetrics metrics;
        private Set<String> applications = new HashSet<>();
        private Set<String> vips = new HashSet<>();
        private Set<String> instances = new HashSet<>();
        private boolean fullRegistry;

        public ChannelSubscriptionMonitor(ServerInterestChannelMetrics metrics) {
            this.metrics = metrics;
        }

        /**
         *  Group by interest type (instance, application, vip).
         */
        public void update(Interest<InstanceInfo> newInterests) {
            // Group interests first
            Set<String> newApplications = new HashSet<>();
            Set<String> newVips = new HashSet<>();
            Set<String> newInstances = new HashSet<>();
            boolean newFullRegistry = false;
            for (Interest<InstanceInfo> basicInterest : getBasicInterests(newInterests)) {
                if (basicInterest instanceof InstanceInterest) {
                    newInstances.add(((InstanceInterest) basicInterest).getPattern());
                } else if (basicInterest instanceof ApplicationInterest) {
                    newApplications.add(((ApplicationInterest) basicInterest).getPattern());
                } else if (basicInterest instanceof VipInterest) {
                    newVips.add(((VipInterest) basicInterest).getPattern());
                } else if (basicInterest instanceof FullRegistryInterest) {
                    newFullRegistry = true;
                }
            }

            // Update applications
            for (String app : newApplications) {
                if (!applications.contains(app)) {
                    metrics.incrementSubscriptionCounter(AtomicInterest.Application, app);
                }
            }
            for (String app : applications) {
                if (!newApplications.contains(app)) {
                    metrics.decrementSubscriptionCounter(AtomicInterest.Application, app);
                }
            }
            applications = newApplications;

            // Update vip
            for (String vip : newVips) {
                if (!vips.contains(vip)) {
                    metrics.incrementSubscriptionCounter(AtomicInterest.Vip, vip);
                }
            }
            for (String vip : vips) {
                if (!newVips.contains(vip)) {
                    metrics.decrementSubscriptionCounter(AtomicInterest.Vip, vip);
                }
            }
            vips = newVips;

            // Update instances
            for (String instance : newInstances) {
                if (!instances.contains(instance)) {
                    metrics.incrementSubscriptionCounter(AtomicInterest.Instance, instance);
                }
            }
            for (String instance : instances) {
                if (!newInstances.contains(instance)) {
                    metrics.decrementSubscriptionCounter(AtomicInterest.Instance, instance);
                }
            }
            instances = newInstances;

            // Full registry fetch
            if (fullRegistry) {
                if (!newFullRegistry) {
                    metrics.decrementSubscriptionCounter(AtomicInterest.InterestAll, null);
                }
            } else {
                if (newFullRegistry) {
                    metrics.incrementSubscriptionCounter(AtomicInterest.InterestAll, null);
                }
            }
            fullRegistry = newFullRegistry;
        }

        protected Set<Interest<InstanceInfo>> getBasicInterests(Interest<InstanceInfo> newInterests) {
            Set<Interest<InstanceInfo>> basicInterests;
            if (newInterests instanceof MultipleInterests) {
                basicInterests = ((MultipleInterests<InstanceInfo>) newInterests).flatten();
            } else {
                basicInterests = Collections.singleton(newInterests);
            }
            return basicInterests;
        }
    }
}
