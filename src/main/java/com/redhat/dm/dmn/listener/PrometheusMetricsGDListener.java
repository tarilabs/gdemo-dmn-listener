/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.redhat.dm.dmn.listener;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.jbpm.services.api.service.ServiceRegistry;
import org.kie.dmn.api.core.DMNDecisionResult;
import org.kie.dmn.api.core.ast.DecisionNode;
import org.kie.dmn.api.core.event.AfterEvaluateBKMEvent;
import org.kie.dmn.api.core.event.AfterEvaluateContextEntryEvent;
import org.kie.dmn.api.core.event.AfterEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.AfterEvaluateDecisionServiceEvent;
import org.kie.dmn.api.core.event.AfterEvaluateDecisionTableEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateBKMEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateContextEntryEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateDecisionEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateDecisionServiceEvent;
import org.kie.dmn.api.core.event.BeforeEvaluateDecisionTableEvent;
import org.kie.dmn.api.core.event.DMNRuntimeEventListener;

import io.prometheus.client.Histogram;

public class PrometheusMetricsGDListener implements DMNRuntimeEventListener {

    private static final String CARD_HOLDER_RISK_RATING_DECISION = "Cardholder Risk Rating";
    private static final String DISPUTE_RISK_RATING_DECISION = "Dispute Risk Rating";
    private static final String PROCESS_AUTOMATICALLY_DECISION = "Process Automatically";

    private static final String CARD_HOLDER_RISK_RATING_COLLECTOR = "CardHolderRisktRatingCollector";
    private static final String DISPUTE_RISK_RATING_COLLECTOR = "DisputeRiskRatingCollector";
    private static final String PROCESS_AUTOMATICALLY_RATING_COLLECTOR = "DisputeRiskRatingCollector";

    private static final Histogram disputeRiskRating;

    private static final Histogram cardholderRiskRating;

    private static final Histogram processAutomaticallyRating;

    private final String[] labels;

    static {
        ServiceRegistry registry = ServiceRegistry.get();
        
        Histogram crr = null;
        try {
            crr = (Histogram) registry.service(CARD_HOLDER_RISK_RATING_COLLECTOR);
        } catch (IllegalArgumentException iae) {
            //Collector is not in registry, so building a new one.
            crr = Histogram.build()
                    .name("cardholder_risk_rating")
                    .help("Cardholder Risk Rating")
                    .labelNames("group_id", "artifact_id", "version", "decision_namespace", "decision_name")
                    .buckets(1, 2, 3, 4, 5)
                    .register();

            registry.register(CARD_HOLDER_RISK_RATING_COLLECTOR, crr);
        }
        cardholderRiskRating = crr;

        Histogram drr = null;
        try {
            drr = (Histogram) registry.service(DISPUTE_RISK_RATING_COLLECTOR);
        } catch (IllegalArgumentException iae) {
            //Collector is not in registry, so building a new one.
            drr = Histogram.build()
                    .name("dispute_risk_rating")
                    .help("Dispute Risk Rating")
                    .labelNames("group_id", "artifact_id", "version", "decision_namespace", "decision_name")
                    .buckets(1, 2, 3, 4, 5)
                    .register();
            registry.register(DISPUTE_RISK_RATING_COLLECTOR, drr);
        }
        disputeRiskRating = drr;
    
        Histogram par = null;
        try {
            par = (Histogram) registry.service(PROCESS_AUTOMATICALLY_RATING_COLLECTOR);
        } catch (IllegalArgumentException iae) {
            //Collector is not in registry, so building a new one.
            par = Histogram.build()
                    .name("process_automatically_risk_rating")
                    .help("Process Automatically Risk Rating")
                    .labelNames("group_id", "artifact_id", "version", "decision_namespace", "decision_name")
                    .buckets(0, 1)
                    .register();
            registry.register(DISPUTE_RISK_RATING_COLLECTOR, par);
        } 
        processAutomaticallyRating = par;
    }

    public PrometheusMetricsGDListener(String... labels) {
        this.labels = labels;
    }

    @Override
    public void beforeEvaluateDecision(BeforeEvaluateDecisionEvent e) {
    }

    @Override
    public void afterEvaluateDecision(AfterEvaluateDecisionEvent e) {
        DecisionNode decisionNode = e.getDecision();
        DMNDecisionResult disputeRiskRating = e.getResult().getDecisionResultByName(DISPUTE_RISK_RATING_DECISION);
        DMNDecisionResult cardholderRiskRating = e.getResult().getDecisionResultByName(CARD_HOLDER_RISK_RATING_DECISION);
        DMNDecisionResult processAutomatically = e.getResult().getDecisionResultByName(PROCESS_AUTOMATICALLY_DECISION);
        publishToPrometheus(getDisputeRiskRatingHistogram(), decisionNode, disputeRiskRating);
        publishToPrometheus(getCardholderRiskRatingHistogram(), decisionNode, cardholderRiskRating);
        publishToPrometheus(getProcessAutomaticallyRating(), decisionNode, processAutomatically);
    }

    void publishToPrometheus(Histogram histogram, DecisionNode decisionNode, DMNDecisionResult dmnDecisionResult) {
        if (dmnDecisionResult != null && !dmnDecisionResult.hasErrors()) {
            double result = 0;
            Object dmnResult = dmnDecisionResult.getResult();
            if (dmnResult instanceof BigDecimal) {
                BigDecimal bdScore = (BigDecimal) dmnDecisionResult.getResult();
                result = bdScore.doubleValue();
            } else if (dmnResult instanceof Boolean) {
                Boolean bScore = (Boolean) dmnDecisionResult.getResult();
                result = bScore.booleanValue() ? 1 : 0;
            }

            histogram.labels(buildLabels(decisionNode.getModelName(), decisionNode.getModelNamespace())).observe(result);
        }
    }

    Histogram getDisputeRiskRatingHistogram() {
        return disputeRiskRating;
    }

    Histogram getCardholderRiskRatingHistogram() {
        return cardholderRiskRating;
    }

    Histogram getProcessAutomaticallyRating() {
        return processAutomaticallyRating;
    }
    private String[] buildLabels(String... dynamicLabels) {
        List<String> labelsList = new ArrayList<>();
        for (String nextStaticLabel: labels) {
            labelsList.add(nextStaticLabel);
        }
        for (String nextDynamicLabel: dynamicLabels) {
            labelsList.add(nextDynamicLabel);
        }
       return labelsList.toArray(new String[labelsList.size()]);
     }

    @Override
    public void beforeEvaluateBKM(BeforeEvaluateBKMEvent event) {
    }

    @Override
    public void afterEvaluateBKM(AfterEvaluateBKMEvent event) {
    }

    @Override
    public void beforeEvaluateContextEntry(BeforeEvaluateContextEntryEvent event) {
    }

    @Override
    public void afterEvaluateContextEntry(AfterEvaluateContextEntryEvent event) {
    }

    @Override
    public void beforeEvaluateDecisionTable(BeforeEvaluateDecisionTableEvent event) {
    }

    @Override
    public void afterEvaluateDecisionTable(AfterEvaluateDecisionTableEvent event) {
    }

    @Override
    public void beforeEvaluateDecisionService(BeforeEvaluateDecisionServiceEvent event) {
    }

    @Override
    public void afterEvaluateDecisionService(AfterEvaluateDecisionServiceEvent event) {
    }
}
